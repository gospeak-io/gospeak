package fr.gospeak.web.pages.orga.sponsors

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Group, Partner, Sponsor, SponsorPack}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.partners.routes.{PartnerCtrl => PartnerRoutes}
import fr.gospeak.web.pages.orga.sponsors.SponsorCtrl._
import fr.gospeak.web.utils.{HttpUtils, Mappings, SecuredReq, UICtrl}
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
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      sponsorPacks <- OptionT.liftF(sponsorPackRepo.listAll(groupElt.id))
      sponsors <- OptionT.liftF(sponsorRepo.listFull(groupElt.id, params))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, sponsorPacks, sponsors)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def createPack(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    createPackView(group, SponsorForms.createPack)
  }

  def doCreatePack(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    SponsorForms.createPack.bindFromRequest.fold(
      formWithErrors => createPackView(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.user.id, group))
        _ <- OptionT.liftF(sponsorPackRepo.create(groupElt.id, data, req.user.id, req.now))
      } yield Redirect(routes.SponsorCtrl.list(group))).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def createPackView(group: Group.Slug, form: Form[SponsorPack.Data])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      b = listBreadcrumb(groupElt).add("New pack" -> routes.SponsorCtrl.createPack(group))
    } yield Ok(html.createPack(groupElt, form)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def create(group: Group.Slug, pack: SponsorPack.Slug, partner: Option[Partner.Slug]): Action[AnyContent] = SecuredActionIO { implicit req =>
    createView(group, pack, SponsorForms.create, partner)
  }

  def doCreate(group: Group.Slug, pack: SponsorPack.Slug, partner: Option[Partner.Slug]): Action[AnyContent] = SecuredActionIO { implicit req =>
    SponsorForms.create.bindFromRequest.fold(
      formWithErrors => createView(group, pack, formWithErrors, partner),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.user.id, group))
        _ <- OptionT.liftF(sponsorRepo.create(groupElt.id, data, req.user.id, req.now))
        next = partner.map(p => PartnerRoutes.detail(group, p)).getOrElse(routes.SponsorCtrl.list(group))
      } yield Redirect(next)).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def createView(group: Group.Slug, pack: SponsorPack.Slug, form: Form[Sponsor.Data], partner: Option[Partner.Slug])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      packElt <- OptionT(sponsorPackRepo.find(groupElt.id, pack))
      partnerElt <- partner.map(p => OptionT.liftF(partnerRepo.find(groupElt.id, p))).getOrElse(OptionT.liftF(IO.pure(None)))
      filledForm = if (form.hasErrors) form else form.bind(Map(
        "pack" -> packElt.id.value,
        "price.amount" -> packElt.price.amount.toString,
        "price.currency" -> packElt.price.currency.name,
        "start" -> Mappings.localDateFormatter.format(req.nowLD),
        "finish" -> Mappings.localDateFormatter.format(packElt.duration.unit.chrono.addTo(req.nowLD, packElt.duration.length))
      )).discardingErrors
      b = listBreadcrumb(groupElt).add("New" -> routes.SponsorCtrl.create(group, packElt.slug))
    } yield Ok(html.create(groupElt, packElt, filledForm, partnerElt)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, pack: SponsorPack.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      packElt <- OptionT(sponsorPackRepo.find(groupElt.id, pack))
      b = breadcrumb(groupElt, packElt)
    } yield Ok(html.detail(groupElt, packElt)(b))).value.map(_.getOrElse(packNotFound(group, pack)))
  }

  def edit(group: Group.Slug, sponsor: Sponsor.Id, partner: Option[Partner.Slug]): Action[AnyContent] = SecuredActionIO { implicit req =>
    updateView(group, sponsor, SponsorForms.create, partner)
  }

  def doEdit(group: Group.Slug, sponsor: Sponsor.Id, partner: Option[Partner.Slug]): Action[AnyContent] = SecuredActionIO { implicit req =>
    SponsorForms.create.bindFromRequest.fold(
      formWithErrors => updateView(group, sponsor, formWithErrors, partner),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.user.id, group))
        _ <- OptionT.liftF(sponsorRepo.edit(groupElt.id, sponsor)(data, req.user.id, req.now))
        next = partner.map(p => PartnerRoutes.detail(group, p)).getOrElse(routes.SponsorCtrl.list(group))
      } yield Redirect(next)).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def updateView(group: Group.Slug, sponsor: Sponsor.Id, form: Form[Sponsor.Data], partner: Option[Partner.Slug])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      sponsorElt <- OptionT(sponsorRepo.find(groupElt.id, sponsor))
      partnerElt <- OptionT(partnerRepo.find(groupElt.id, sponsorElt.partner))
      b = listBreadcrumb(groupElt).add("Edit" -> routes.SponsorCtrl.edit(group, sponsor, partner))
      filledForm = if (form.hasErrors) form else form.fill(sponsorElt.data)
    } yield Ok(html.edit(groupElt, partnerElt, sponsorElt, filledForm, partner)(b))).value.map(_.getOrElse(sponsorNotFound(group, sponsor)))
  }

  def disablePack(group: Group.Slug, pack: SponsorPack.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      _ <- OptionT.liftF(sponsorPackRepo.disable(groupElt.id, pack)(req.user.id, req.now))
    } yield next).value.map(_.getOrElse(next.flashing("error" -> s"Unable to disable sponsor pack ${pack.value} of group ${group.value}")))
  }

  def enablePack(group: Group.Slug, pack: SponsorPack.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      _ <- OptionT.liftF(sponsorPackRepo.enable(groupElt.id, pack)(req.user.id, req.now))
    } yield next).value.map(_.getOrElse(next.flashing("error" -> s"Unable to disable sponsor pack ${pack.value} of group ${group.value}")))
  }

  def paid(group: Group.Slug, sponsor: Sponsor.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      sponsorElt <- OptionT(sponsorRepo.find(groupElt.id, sponsor))
      _ <- OptionT.liftF(sponsorRepo.edit(groupElt.id, sponsor)(sponsorElt.data.copy(paid = Some(req.nowLD)), req.user.id, req.now))
    } yield next).value.map(_.getOrElse(next.flashing("error" -> s"Unable to mark sponsor ${sponsor.value} of group ${group.value} as paid :(")))
  }

  def remove(group: Group.Slug, sponsor: Sponsor.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      _ <- OptionT.liftF(sponsorRepo.remove(groupElt.id, sponsor))
    } yield next).value.map(_.getOrElse(next.flashing("error" -> s"Unable to mark remove ${sponsor.value} of group ${group.value} :(")))
  }
}

object SponsorCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Sponsors" -> routes.SponsorCtrl.list(group.slug))

  def breadcrumb(group: Group, pack: SponsorPack): Breadcrumb =
    listBreadcrumb(group).add(pack.name.value -> routes.SponsorCtrl.detail(group.slug, pack.slug))
}
