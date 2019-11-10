package fr.gospeak.web.pages.orga.venues

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.{Group, Venue}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.venues.VenueCtrl._
import fr.gospeak.web.utils.{SecuredReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class VenueCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                env: ApplicationConf.Env,
                userRepo: OrgaUserRepo,
                groupRepo: OrgaGroupRepo,
                eventRepo: OrgaEventRepo,
                venueRepo: OrgaVenueRepo,
                groupSettingsRepo: GroupSettingsRepo,
                timeShape: TimeShape) extends UICtrl(cc, silhouette, env) {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      venues <- OptionT.liftF(venueRepo.listFull(groupElt.id, params))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, venues)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def create(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    createView(group, VenueForms.create(timeShape))
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => createView(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.user.id, group))
        venueElt <- OptionT.liftF(venueRepo.create(groupElt.id, data, req.user.id, req.now))
      } yield Redirect(routes.VenueCtrl.detail(group, venueElt.id))).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def createView(group: Group.Slug, form: Form[Venue.Data])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup(groupElt.id))
      b = listBreadcrumb(groupElt).add("New" -> routes.VenueCtrl.create(group))
      call = routes.VenueCtrl.doCreate(group)
    } yield Ok(html.create(groupElt, meetupAccount.isDefined, None, form, call)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      venueElt <- OptionT(venueRepo.findFull(groupElt.id, venue))
      events <- OptionT.liftF(eventRepo.list(groupElt.id, venue))
      users <- OptionT.liftF(userRepo.list(venueElt.users))
      b = breadcrumb(groupElt, venueElt)
      editCall = routes.VenueCtrl.edit(group, venue)
      removeCall = routes.VenueCtrl.doRemove(group, venue)
      res = Ok(html.detail(groupElt, venueElt, events, users, editCall, removeCall)(b))
    } yield res).value.map(_.getOrElse(venueNotFound(group, venue)))
  }

  def edit(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    editView(group, venue, VenueForms.create(timeShape))
  }

  def doEdit(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => editView(group, venue, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.user.id, group))
        _ <- OptionT.liftF(venueRepo.edit(groupElt.id, venue)(data, req.user.id, req.now))
      } yield Redirect(routes.VenueCtrl.detail(group, venue))).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def editView(group: Group.Slug, venue: Venue.Id, form: Form[Venue.Data])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup(groupElt.id))
      venueElt <- OptionT(venueRepo.findFull(groupElt.id, venue))
      b = breadcrumb(groupElt, venueElt).add("Edit" -> routes.VenueCtrl.edit(group, venue))
      filledForm = if (form.hasErrors) form else form.fill(venueElt.data)
      call = routes.VenueCtrl.doEdit(group, venue)
    } yield Ok(html.edit(groupElt, meetupAccount.isDefined, venueElt, filledForm, call)(b))).value.map(_.getOrElse(venueNotFound(group, venue)))
  }

  def doRemove(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      _ <- OptionT.liftF(venueRepo.remove(groupElt.id, venue)(req.user.id, req.now))
    } yield Redirect(routes.VenueCtrl.list(group))).value.map(_.getOrElse(groupNotFound(group)))
  }
}

object VenueCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Venues" -> routes.VenueCtrl.list(group.slug))

  def breadcrumb(group: Group, venue: Venue.Full): Breadcrumb =
    listBreadcrumb(group).add(venue.address.value -> routes.VenueCtrl.detail(group.slug, venue.id))
}
