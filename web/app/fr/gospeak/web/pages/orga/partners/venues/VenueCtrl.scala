package fr.gospeak.web.pages.orga.partners.venues

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Group, Partner, Venue}
import fr.gospeak.core.services.storage._
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
                groupSettingsRepo: GroupSettingsRepo) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def create(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createView(group, partner, VenueForms.create)
  })

  def doCreate(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    VenueForms.create.bindFromRequest.fold(
      formWithErrors => createView(group, partner, formWithErrors),
      data => venueRepo.create(data).map(_ => Redirect(PartnerRoutes.detail(group, partner)))
    )
  })

  private def createView(group: Group.Slug, partner: Partner.Slug, form: Form[Venue.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      call = routes.VenueCtrl.doCreate(group, partner)
      b = listBreadcrumb(partnerElt).add("New" -> routes.VenueCtrl.create(group, partner))
    } yield Ok(html.create(meetupAccount.isDefined, Some(partnerElt), form, call)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def detail(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      venueElt <- OptionT(venueRepo.findFull(venue))
      events <- OptionT.liftF(eventRepo.list(venue))
      users <- OptionT.liftF(userRepo.list(venueElt.users))
      editCall = routes.VenueCtrl.edit(group, partner, venue)
      removeCall = routes.VenueCtrl.doRemove(group, partner, venue)
      b = breadcrumb(venueElt)
      res = Ok(html.detail(venueElt, events, users, editCall, removeCall)(b))
    } yield res).value.map(_.getOrElse(venueNotFound(group, partner, venue)))
  })

  def edit(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    editView(group, partner, venue, VenueForms.create)
  })

  def doEdit(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    VenueForms.create.bindFromRequest.fold(
      formWithErrors => editView(group, partner, venue, formWithErrors),
      data => venueRepo.edit(venue, data).map(_ => Redirect(routes.VenueCtrl.detail(group, partner, venue)))
    )
  })

  private def editView(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id, form: Form[Venue.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      venueElt <- OptionT(venueRepo.findFull(venue))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      filledForm = if (form.hasErrors) form else form.fill(venueElt.data)
      call = routes.VenueCtrl.doEdit(group, partner, venue)
      b = breadcrumb(venueElt).add("Edit" -> routes.VenueCtrl.edit(group, partner, venue))
    } yield Ok(html.edit(meetupAccount.isDefined, venueElt, filledForm, call)(b))).value.map(_.getOrElse(venueNotFound(group, venue)))
  }

  def doRemove(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    venueRepo.remove(venue).map(_ => Redirect(PartnerRoutes.detail(group, partner)))
  })
}

object VenueCtrl {
  def listBreadcrumb(partner: Partner)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    PartnerCtrl.breadcrumb(partner).add("Venues" -> PartnerRoutes.detail(req.group.slug, partner.slug))

  def breadcrumb(venue: Venue.Full)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb(venue.partner).add(venue.address.value -> routes.VenueCtrl.detail(req.group.slug, venue.partner.slug, venue.id))
}
