package fr.gospeak.web.pages.orga.venues

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Group, Venue}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.venues.VenueCtrl._
import fr.gospeak.web.utils.{OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class VenueCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                env: ApplicationConf.Env,
                userRepo: OrgaUserRepo,
                val groupRepo: OrgaGroupRepo,
                eventRepo: OrgaEventRepo,
                venueRepo: OrgaVenueRepo,
                groupSettingsRepo: GroupSettingsRepo,
                timeShape: TimeShape) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    venueRepo.listFull(params).map(venues => Ok(html.list(venues)(listBreadcrumb)))
  })

  def explore(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      groupVenues <- venueRepo.listAllFull()
      publicVenues <- venueRepo.listPublicFull(params)
    } yield Ok(html.explore(publicVenues, groupVenues)(listBreadcrumb))
  })

  def duplicate(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    venueRepo.duplicate(venue).map(_ => redirectToPreviousPageOr(routes.VenueCtrl.list(group)))
  })

  def create(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createView(VenueForms.create(timeShape))
  })

  def doCreate(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => createView(formWithErrors),
      data => venueRepo.create(data).map(venueElt => Redirect(routes.VenueCtrl.detail(group, venueElt.id)))
    )
  })

  private def createView(form: Form[Venue.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    for {
      meetupAccount <- groupSettingsRepo.findMeetup
      call = routes.VenueCtrl.doCreate(req.group.slug)
      b = listBreadcrumb.add("New" -> routes.VenueCtrl.create(req.group.slug))
    } yield Ok(html.create(meetupAccount.isDefined, None, form, call)(b))
  }

  def detail(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      venueElt <- OptionT(venueRepo.findFull(venue))
      events <- OptionT.liftF(eventRepo.list(venue))
      users <- OptionT.liftF(userRepo.list(venueElt.users))
      editCall = routes.VenueCtrl.edit(group, venue)
      removeCall = routes.VenueCtrl.doRemove(group, venue)
      b = breadcrumb(venueElt)
      res = Ok(html.detail(venueElt, events, users, editCall, removeCall)(b))
    } yield res).value.map(_.getOrElse(venueNotFound(group, venue)))
  })

  def edit(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    editView(venue, VenueForms.create(timeShape))
  })

  def doEdit(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => editView(venue, formWithErrors),
      data => venueRepo.edit(venue, data).map(_ => Redirect(routes.VenueCtrl.detail(group, venue)))
    )
  })

  private def editView(venue: Venue.Id, form: Form[Venue.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      venueElt <- OptionT(venueRepo.findFull(venue))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      filledForm = if (form.hasErrors) form else form.fill(venueElt.data)
      call = routes.VenueCtrl.doEdit(req.group.slug, venue)
      b = breadcrumb(venueElt).add("Edit" -> routes.VenueCtrl.edit(req.group.slug, venue))
      res = Ok(html.edit(meetupAccount.isDefined, venueElt, filledForm, call)(b))
    } yield res).value.map(_.getOrElse(venueNotFound(req.group.slug, venue)))
  }

  def doRemove(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    venueRepo.remove(venue).map(_ => Redirect(routes.VenueCtrl.list(group)))
  })
}

object VenueCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb.add("Venues" -> routes.VenueCtrl.list(req.group.slug))

  def breadcrumb(venue: Venue.Full)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb.add(venue.address.value -> routes.VenueCtrl.detail(req.group.slug, venue.id))
}
