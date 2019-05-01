package fr.gospeak.web.pages.orga.venues

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, Venue}
import fr.gospeak.core.services.{OrgaGroupRepo, OrgaVenueRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.venues.VenueCtrl._
import fr.gospeak.web.utils.Mappings._
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class VenueCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                groupRepo: OrgaGroupRepo,
                venueRepo: OrgaVenueRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  private val createForm: Form[Venue.Data] = Form(mapping(
    "partner" -> partnerId,
    "address" -> gMapPlace,
    "description" -> markdown,
    "roomSize" -> optional(number)
  )(Venue.Data.apply)(Venue.Data.unapply))

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      venues <- OptionT.liftF(venueRepo.list(groupElt.id, params))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, venues)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def create(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(group, createForm).unsafeToFuture()
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    createForm.bindFromRequest.fold(
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
      b = listBreadcrumb(groupElt).add("New" -> routes.VenueCtrl.create(group))
    } yield Ok(html.create(groupElt, form)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      (partnerElt, venueElt) <- OptionT(venueRepo.find(groupElt.id, venue))
      b = breadcrumb(groupElt, venueElt)
    } yield Ok(html.detail(groupElt, partnerElt, venueElt)(b))).value.map(_.getOrElse(venueNotFound(group, venue))).unsafeToFuture()
  }

  def edit(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, venue, createForm).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, venue: Venue.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    createForm.bindFromRequest.fold(
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
      (partnerElt, venueElt) <- OptionT(venueRepo.find(groupElt.id, venue))
      b = breadcrumb(groupElt, venueElt).add("Edit" -> routes.VenueCtrl.edit(group, venue))
      filledForm = if (form.hasErrors) form else form.fill(venueElt.data)
    } yield Ok(html.edit(groupElt, venueElt, filledForm)(b))).value.map(_.getOrElse(venueNotFound(group, venue)))
  }
}

object VenueCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Venues" -> routes.VenueCtrl.list(group.slug))

  def breadcrumb(group: Group, venue: Venue): Breadcrumb =
    listBreadcrumb(group).add(venue.address.format -> routes.VenueCtrl.detail(group.slug, venue.id))
}
