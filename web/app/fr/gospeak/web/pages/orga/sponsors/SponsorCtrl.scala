package fr.gospeak.web.pages.orga.sponsors

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, Sponsor, SponsorPack}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.sponsors.SponsorCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class SponsorCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  userRepo: OrgaUserRepo,
                  sponsorPackRepo: OrgaSponsorPackRepo,
                  sponsorRepo: OrgaSponsorRepo,
                  groupRepo: OrgaGroupRepo,
                  venueRepo: OrgaVenueRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      sponsorPacks <- OptionT.liftF(sponsorPackRepo.listAll(groupElt.id))
      sponsors <- OptionT.liftF(sponsorRepo.list(groupElt.id, params))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, sponsorPacks, sponsors)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def createPack(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createPackForm(group, SponsorForms.createPack).unsafeToFuture()
  }

  def doCreatePack(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    SponsorForms.createPack.bindFromRequest.fold(
      formWithErrors => createPackForm(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        _ <- OptionT.liftF(sponsorPackRepo.create(groupElt.id, data, by, now))
      } yield Redirect(routes.SponsorCtrl.list(group))).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createPackForm(group: Group.Slug, form: Form[SponsorPack.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      b = listBreadcrumb(groupElt).add("New pack" -> routes.SponsorCtrl.createPack(group))
    } yield Ok(html.createPack(groupElt, form)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def create(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(group, SponsorForms.create).unsafeToFuture()
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    SponsorForms.create.bindFromRequest.fold(
      formWithErrors => createForm(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        _ <- OptionT.liftF(sponsorRepo.create(groupElt.id, data, by, now))
      } yield Redirect(routes.SponsorCtrl.list(group))).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, form: Form[Sponsor.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      b = listBreadcrumb(groupElt).add("New" -> routes.SponsorCtrl.create(group))
    } yield Ok(html.create(groupElt, form)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }
}

object SponsorCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Sponsors" -> routes.SponsorCtrl.list(group.slug))
}
