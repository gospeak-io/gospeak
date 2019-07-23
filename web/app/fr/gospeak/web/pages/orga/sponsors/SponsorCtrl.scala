package fr.gospeak.web.pages.orga.sponsors

import java.time.{Instant, LocalDate}

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, Partner, Sponsor, SponsorPack}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.sponsors.SponsorCtrl._
import fr.gospeak.web.pages.orga.partners.routes.{PartnerCtrl => PartnerRoutes}
import fr.gospeak.web.utils.{HttpUtils, Mappings, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class SponsorCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  userRepo: OrgaUserRepo,
                  sponsorPackRepo: OrgaSponsorPackRepo,
                  sponsorRepo: OrgaSponsorRepo,
                  groupRepo: OrgaGroupRepo,
                  partnerRepo: OrgaPartnerRepo,
                  venueRepo: OrgaVenueRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      sponsorPacks <- OptionT.liftF(sponsorPackRepo.listAll(groupElt.id))
      sponsors <- OptionT.liftF(sponsorRepo.list(groupElt.id, params))
      partners <- OptionT.liftF(partnerRepo.list(sponsors.items.map(_.partner)))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, sponsorPacks, sponsors, partners)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
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

  def create(group: Group.Slug, pack: SponsorPack.Slug, partner: Option[Partner.Slug]): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(group, pack, SponsorForms.create, partner).unsafeToFuture()
  }

  def doCreate(group: Group.Slug, pack: SponsorPack.Slug, partner: Option[Partner.Slug]): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    SponsorForms.create.bindFromRequest.fold(
      formWithErrors => createForm(group, pack, formWithErrors, partner),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        _ <- OptionT.liftF(sponsorRepo.create(groupElt.id, data, by, now))
        next = partner.map(p => PartnerRoutes.detail(group, p)).getOrElse(routes.SponsorCtrl.list(group))
      } yield Redirect(next)).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, pack: SponsorPack.Slug, form: Form[Sponsor.Data], partner: Option[Partner.Slug])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    val nowLD = LocalDate.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      packElt <- OptionT(sponsorPackRepo.find(groupElt.id, pack))
      partnerElt <- partner.map(p => OptionT.liftF(partnerRepo.find(groupElt.id, p))).getOrElse(OptionT.liftF(IO.pure(None)))
      filledForm = if (form.hasErrors) form else form.bind(Map(
        "pack" -> packElt.id.value,
        "price.amount" -> packElt.price.amount.toString,
        "price.currency" -> packElt.price.currency.name,
        "start" -> Mappings.localDateFormatter.format(nowLD),
        "finish" -> Mappings.localDateFormatter.format(packElt.duration.unit.chrono.addTo(nowLD, packElt.duration.length))
      )).discardingErrors
      b = listBreadcrumb(groupElt).add("New" -> routes.SponsorCtrl.create(group, packElt.slug))
    } yield Ok(html.create(groupElt, packElt, filledForm, partnerElt)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, pack: SponsorPack.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      packElt <- OptionT(sponsorPackRepo.find(groupElt.id, pack))
      b = breadcrumb(groupElt, packElt)
    } yield Ok(html.detail(groupElt, packElt)(b))).value.map(_.getOrElse(packNotFound(group, pack))).unsafeToFuture()
  }

  def disablePack(group: Group.Slug, pack: SponsorPack.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      _ <- OptionT.liftF(sponsorPackRepo.disable(groupElt.id, pack)(user, now))
    } yield next).value.map(_.getOrElse(next.flashing("error" -> s"Unable to disable sponsor pack ${pack.value} of group ${group.value}"))).unsafeToFuture()
  }

  def enablePack(group: Group.Slug, pack: SponsorPack.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      _ <- OptionT.liftF(sponsorPackRepo.enable(groupElt.id, pack)(user, now))
    } yield next).value.map(_.getOrElse(next.flashing("error" -> s"Unable to disable sponsor pack ${pack.value} of group ${group.value}"))).unsafeToFuture()
  }

  def paid(group: Group.Slug, sponsor: Sponsor.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val nowLD = LocalDate.now()
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      sponsorElt <- OptionT(sponsorRepo.find(groupElt.id, sponsor))
      _ <- OptionT.liftF(sponsorRepo.edit(groupElt.id, sponsor)(sponsorElt.data.copy(paid = Some(nowLD)), user, now))
    } yield next).value.map(_.getOrElse(next.flashing("error" -> s"Unable to mark sponsor ${sponsor.value} of group ${group.value} as paid :("))).unsafeToFuture()
  }

  def remove(group: Group.Slug, sponsor: Sponsor.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      _ <- OptionT.liftF(sponsorRepo.remove(groupElt.id, sponsor))
    } yield next).value.map(_.getOrElse(next.flashing("error" -> s"Unable to mark remove ${sponsor.value} of group ${group.value} :("))).unsafeToFuture()
  }
}

object SponsorCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Sponsors" -> routes.SponsorCtrl.list(group.slug))

  def breadcrumb(group: Group, pack: SponsorPack): Breadcrumb =
    listBreadcrumb(group).add(pack.name.value -> routes.SponsorCtrl.detail(group.slug, pack.slug))
}
