package fr.gospeak.web.pages.orga.partners.venues

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, Partner, Venue}
import fr.gospeak.core.services.storage.{GroupSettingsRepo, OrgaGroupRepo, OrgaPartnerRepo, OrgaUserRepo, OrgaVenueRepo}
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.partners.PartnerCtrl
import fr.gospeak.web.pages.orga.partners.routes.{PartnerCtrl => PartnerRoutes}
import fr.gospeak.web.pages.orga.partners.venues.VenueCtrl._
import fr.gospeak.web.pages.orga.venues.{VenueForms, html}
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class VenueCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                userRepo: OrgaUserRepo,
                groupRepo: OrgaGroupRepo,
                partnerRepo: OrgaPartnerRepo,
                venueRepo: OrgaVenueRepo,
                groupSettingsRepo: GroupSettingsRepo,
                timeShape: TimeShape) extends UICtrl(cc, silhouette) {

  import silhouette._

  def create(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(group, partner, VenueForms.create(timeShape)).unsafeToFuture()
  }

  def doCreate(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => createForm(group, partner, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        _ <- OptionT.liftF(venueRepo.create(groupElt.id, data, by, now))
      } yield Redirect(PartnerRoutes.detail(group, partner))).value.map(_.getOrElse(partnerNotFound(group, partner)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, partner: Partner.Slug, form: Form[Venue.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup(groupElt.id))
      partnerElt <- OptionT(partnerRepo.find(groupElt.id, partner))
      b = listBreadcrumb(groupElt, partnerElt).add("New" -> routes.VenueCtrl.create(group, partner))
      call = routes.VenueCtrl.doCreate(group, partner)
    } yield Ok(html.create(groupElt, meetupAccount.isDefined, Some(partnerElt), form, call)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def detail(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      (partnerElt, venueElt) <- OptionT(venueRepo.find(groupElt.id, venue))
      users <- OptionT.liftF(userRepo.list((partnerElt.users ++ venueElt.users).distinct))
      b = breadcrumb(groupElt, partnerElt, venueElt)
      edit = routes.VenueCtrl.edit(group, partner, venue)
    } yield Ok(html.detail(groupElt, partnerElt, venueElt, users, edit)(b))).value.map(_.getOrElse(venueNotFound(group, partner, venue))).unsafeToFuture()
  }

  def edit(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, partner, venue, VenueForms.create(timeShape)).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => editForm(group, partner, venue, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        _ <- OptionT.liftF(venueRepo.edit(groupElt.id, venue)(data, by, now))
      } yield Redirect(routes.VenueCtrl.detail(group, partner, venue))).value.map(_.getOrElse(partnerNotFound(group, partner)))
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id, form: Form[Venue.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup(groupElt.id))
      (partnerElt, venueElt) <- OptionT(venueRepo.find(groupElt.id, venue))
      b = breadcrumb(groupElt, partnerElt, venueElt).add("Edit" -> routes.VenueCtrl.edit(group, partner, venue))
      filledForm = if (form.hasErrors) form else form.fill(venueElt.data)
      call = routes.VenueCtrl.doEdit(group, partner, venue)
    } yield Ok(html.edit(groupElt, meetupAccount.isDefined, partnerElt, venueElt, filledForm, call)(b))).value.map(_.getOrElse(venueNotFound(group, venue)))
  }
}

object VenueCtrl {
  def listBreadcrumb(group: Group, partner: Partner): Breadcrumb =
    PartnerCtrl.breadcrumb(group, partner).add("Venues" -> PartnerRoutes.detail(group.slug, partner.slug))

  def breadcrumb(group: Group, partner: Partner, venue: Venue): Breadcrumb =
    listBreadcrumb(group, partner).add(venue.address.value -> routes.VenueCtrl.detail(group.slug, partner.slug, venue.id))
}
