package fr.gospeak.web.pages.orga.partners.venues

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Group, Partner, Venue}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.partners.PartnerCtrl
import fr.gospeak.web.pages.orga.partners.routes.{PartnerCtrl => PartnerRoutes}
import fr.gospeak.web.pages.orga.partners.venues.VenueCtrl._
import fr.gospeak.web.pages.orga.venues.{VenueForms, html}
import fr.gospeak.web.utils.{OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class VenueCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                env: ApplicationConf.Env,
                userRepo: OrgaUserRepo,
                val groupRepo: OrgaGroupRepo,
                eventRepo: OrgaEventRepo,
                partnerRepo: OrgaPartnerRepo,
                venueRepo: OrgaVenueRepo,
                groupSettingsRepo: GroupSettingsRepo,
                timeShape: TimeShape) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def create(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createView(group, partner, VenueForms.create(timeShape))
  })

  def doCreate(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => createView(group, partner, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.user.id, group))
        _ <- OptionT.liftF(venueRepo.create(groupElt.id, data, req.user.id, req.now))
      } yield Redirect(PartnerRoutes.detail(group, partner))).value.map(_.getOrElse(partnerNotFound(group, partner)))
    )
  })

  private def createView(group: Group.Slug, partner: Partner.Slug, form: Form[Venue.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      partnerElt <- OptionT(partnerRepo.find(req.group.id, partner))
      b = listBreadcrumb(req.group, partnerElt).add("New" -> routes.VenueCtrl.create(group, partner))
      call = routes.VenueCtrl.doCreate(group, partner)
    } yield Ok(html.create(req.group, meetupAccount.isDefined, Some(partnerElt), form, call)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def detail(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      venueElt <- OptionT(venueRepo.findFull(groupElt.id, venue))
      events <- OptionT.liftF(eventRepo.list(groupElt.id, venue))
      users <- OptionT.liftF(userRepo.list(venueElt.users))
      b = breadcrumb(groupElt, venueElt)
      editCall = routes.VenueCtrl.edit(group, partner, venue)
      removeCall = routes.VenueCtrl.doRemove(group, partner, venue)
      res = Ok(html.detail(groupElt, venueElt, events, users, editCall, removeCall)(b))
    } yield res).value.map(_.getOrElse(venueNotFound(group, partner, venue)))
  }

  def edit(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    editView(group, partner, venue, VenueForms.create(timeShape))
  })

  def doEdit(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => editView(group, partner, venue, formWithErrors),
      data => venueRepo.edit(venue, data).map(_ => Redirect(routes.VenueCtrl.detail(group, partner, venue)))
    )
  })

  private def editView(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id, form: Form[Venue.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      venueElt <- OptionT(venueRepo.findFull(req.group.id, venue))
      b = breadcrumb(req.group, venueElt).add("Edit" -> routes.VenueCtrl.edit(group, partner, venue))
      filledForm = if (form.hasErrors) form else form.fill(venueElt.data)
      call = routes.VenueCtrl.doEdit(group, partner, venue)
    } yield Ok(html.edit(req.group, meetupAccount.isDefined, venueElt, filledForm, call)(b))).value.map(_.getOrElse(venueNotFound(group, venue)))
  }

  def doRemove(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      _ <- OptionT.liftF(venueRepo.remove(groupElt.id, venue)(req.user.id, req.now))
    } yield Redirect(PartnerRoutes.detail(group, partner))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }
}

object VenueCtrl {
  def listBreadcrumb(group: Group, partner: Partner): Breadcrumb =
    PartnerCtrl.breadcrumb(group, partner).add("Venues" -> PartnerRoutes.detail(group.slug, partner.slug))

  def breadcrumb(group: Group, venue: Venue.Full): Breadcrumb =
    listBreadcrumb(group, venue.partner).add(venue.address.value -> routes.VenueCtrl.detail(group.slug, venue.partner.slug, venue.id))
}
