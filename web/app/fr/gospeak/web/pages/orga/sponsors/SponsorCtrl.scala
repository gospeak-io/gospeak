package fr.gospeak.web.pages.orga.sponsors

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Group, Partner, Sponsor, SponsorPack}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.partners.routes.{PartnerCtrl => PartnerRoutes}
import fr.gospeak.web.pages.orga.sponsors.SponsorCtrl._
import fr.gospeak.web.utils._
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class SponsorCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  env: ApplicationConf.Env,
                  userRepo: OrgaUserRepo,
                  sponsorPackRepo: OrgaSponsorPackRepo,
                  sponsorRepo: OrgaSponsorRepo,
                  val groupRepo: OrgaGroupRepo,
                  partnerRepo: OrgaPartnerRepo,
                  venueRepo: OrgaVenueRepo) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      sponsorPacks <- sponsorPackRepo.listAll
      sponsors <- sponsorRepo.listFull(params)
    } yield Ok(html.list(sponsorPacks, sponsors)(listBreadcrumb))
  })

  def createPack(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createPackView(SponsorForms.createPack)
  })

  def doCreatePack(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    SponsorForms.createPack.bindFromRequest.fold(
      formWithErrors => createPackView(formWithErrors),
      data => sponsorPackRepo.create(data).map(_ => Redirect(routes.SponsorCtrl.list(group)))
    )
  })

  private def createPackView(form: Form[SponsorPack.Data])(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    val b = listBreadcrumb.add("New pack" -> routes.SponsorCtrl.createPack(req.group.slug))
    IO.pure(Ok(html.createPack(form)(b)))
  }

  def create(group: Group.Slug, pack: SponsorPack.Slug, partner: Option[Partner.Slug]): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createView(pack, SponsorForms.create, partner)
  })

  def doCreate(group: Group.Slug, pack: SponsorPack.Slug, partner: Option[Partner.Slug]): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = partner.map(p => PartnerRoutes.detail(group, p)).getOrElse(routes.SponsorCtrl.list(group))
    SponsorForms.create.bindFromRequest.fold(
      formWithErrors => createView(pack, formWithErrors, partner),
      data => sponsorRepo.create(data).map(_ => Redirect(next))
    )
  })

  private def createView(pack: SponsorPack.Slug, form: Form[Sponsor.Data], partner: Option[Partner.Slug])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      packElt <- OptionT(sponsorPackRepo.find(pack))
      partnerElt <- OptionT.liftF(partner.map(p => partnerRepo.find(p)).getOrElse(IO.pure(None)))
      filledForm = if (form.hasErrors) form else form.bind(Map(
        "pack" -> packElt.id.value,
        "price.amount" -> packElt.price.amount.toString,
        "price.currency" -> packElt.price.currency.name,
        "start" -> Mappings.localDateFormatter.format(req.nowLD),
        "finish" -> Mappings.localDateFormatter.format(packElt.duration.unit.chrono.addTo(req.nowLD, packElt.duration.length))
      )).discardingErrors
      b = listBreadcrumb.add("New" -> routes.SponsorCtrl.create(req.group.slug, packElt.slug))
    } yield Ok(html.create(packElt, filledForm, partnerElt)(b))).value.map(_.getOrElse(packNotFound(req.group.slug, pack)))
  }

  def detail(group: Group.Slug, pack: SponsorPack.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    sponsorPackRepo.find(pack).map {
      case Some(packElt) => Ok(html.detail(packElt)(breadcrumb(packElt)))
      case None => packNotFound(group, pack)
    }
  })

  def edit(group: Group.Slug, sponsor: Sponsor.Id, partner: Option[Partner.Slug]): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    updateView(sponsor, SponsorForms.create, partner)
  })

  def doEdit(group: Group.Slug, sponsor: Sponsor.Id, partner: Option[Partner.Slug]): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = partner.map(p => PartnerRoutes.detail(group, p)).getOrElse(routes.SponsorCtrl.list(group))
    SponsorForms.create.bindFromRequest.fold(
      formWithErrors => updateView(sponsor, formWithErrors, partner),
      data => sponsorRepo.edit(sponsor, data).map(_ => Redirect(next))
    )
  })

  private def updateView(sponsor: Sponsor.Id, form: Form[Sponsor.Data], partner: Option[Partner.Slug])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      sponsorElt <- OptionT(sponsorRepo.find(sponsor))
      partnerElt <- OptionT(partnerRepo.find(sponsorElt.partner))
      filledForm = if (form.hasErrors) form else form.fill(sponsorElt.data)
      b = listBreadcrumb.add("Edit" -> routes.SponsorCtrl.edit(req.group.slug, sponsor, partner))
    } yield Ok(html.edit(partnerElt, sponsorElt, filledForm, partner)(b))).value.map(_.getOrElse(sponsorNotFound(req.group.slug, sponsor)))
  }

  def disablePack(group: Group.Slug, pack: SponsorPack.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    sponsorPackRepo.disable(pack).map(_ => next)
  })

  def enablePack(group: Group.Slug, pack: SponsorPack.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    sponsorPackRepo.enable(pack).map(_ => next)
  })

  def paid(group: Group.Slug, sponsor: Sponsor.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    (for {
      sponsorElt <- OptionT(sponsorRepo.find(sponsor))
      _ <- OptionT.liftF(sponsorRepo.edit(sponsor, sponsorElt.data.copy(paid = Some(req.nowLD))))
    } yield next).value.map(_.getOrElse(next.flashing("error" -> s"Unable to mark sponsor ${sponsor.value} of group ${group.value} as paid :(")))
  })

  def remove(group: Group.Slug, sponsor: Sponsor.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SponsorCtrl.list(group).toString))
    sponsorRepo.remove(sponsor).map(_ => next)
  })
}

object SponsorCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb.add("Sponsors" -> routes.SponsorCtrl.list(req.group.slug))

  def breadcrumb(pack: SponsorPack)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb.add(pack.name.value -> routes.SponsorCtrl.detail(req.group.slug, pack.slug))
}
