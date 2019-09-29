package fr.gospeak.web.pages.orga.venues

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, Venue}
import fr.gospeak.core.services.storage.{GroupSettingsRepo, OrgaEventRepo, OrgaGroupRepo, OrgaUserRepo, OrgaVenueRepo}
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.venues.VenueCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class VenueCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                userRepo: OrgaUserRepo,
                groupRepo: OrgaGroupRepo,
                eventRepo: OrgaEventRepo,
                venueRepo: OrgaVenueRepo,
                groupSettingsRepo: GroupSettingsRepo,
                timeShape: TimeShape) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      venues <- OptionT.liftF(venueRepo.listFull(groupElt.id, params))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, venues)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def create(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(group, VenueForms.create(timeShape)).unsafeToFuture()
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => createForm(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        venueElt <- OptionT.liftF(venueRepo.create(groupElt.id, data, by, now))
      } yield Redirect(routes.VenueCtrl.detail(group, venueElt.id))).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, form: Form[Venue.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup(groupElt.id))
      b = listBreadcrumb(groupElt).add("New" -> routes.VenueCtrl.create(group))
      call = routes.VenueCtrl.doCreate(group)
    } yield Ok(html.create(groupElt, meetupAccount.isDefined, None, form, call)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      venueElt <- OptionT(venueRepo.findFull(groupElt.id, venue))
      events <- OptionT.liftF(eventRepo.list(groupElt.id, venue))
      users <- OptionT.liftF(userRepo.list(venueElt.users))
      b = breadcrumb(groupElt, venueElt.venue)
      edit = routes.VenueCtrl.edit(group, venue)
      res = Ok(html.detail(groupElt, venueElt, events, users, edit)(b))
    } yield res).value.map(_.getOrElse(venueNotFound(group, venue))).unsafeToFuture()
  }

  def edit(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, venue, VenueForms.create(timeShape)).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    VenueForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => editForm(group, venue, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        _ <- OptionT.liftF(venueRepo.edit(groupElt.id, venue)(data, by, now))
      } yield Redirect(routes.VenueCtrl.detail(group, venue))).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, venue: Venue.Id, form: Form[Venue.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup(groupElt.id))
      venueElt <- OptionT(venueRepo.findFull(groupElt.id, venue))
      b = breadcrumb(groupElt, venueElt.venue).add("Edit" -> routes.VenueCtrl.edit(group, venue))
      filledForm = if (form.hasErrors) form else form.fill(venueElt.data)
      call = routes.VenueCtrl.doEdit(group, venue)
    } yield Ok(html.edit(groupElt, meetupAccount.isDefined, venueElt, filledForm, call)(b))).value.map(_.getOrElse(venueNotFound(group, venue)))
  }
}

object VenueCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Venues" -> routes.VenueCtrl.list(group.slug))

  def breadcrumb(group: Group, venue: Venue): Breadcrumb =
    listBreadcrumb(group).add(venue.address.value -> routes.VenueCtrl.detail(group.slug, venue.id))
}
