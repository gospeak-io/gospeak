package fr.gospeak.web.pages.orga.partners

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Group, Partner}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.partners.PartnerCtrl._
import fr.gospeak.web.utils.{OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class PartnerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  env: ApplicationConf.Env,
                  userRepo: OrgaUserRepo,
                  val groupRepo: OrgaGroupRepo,
                  eventRepo: OrgaEventRepo,
                  partnerRepo: OrgaPartnerRepo,
                  contactRepo: ContactRepo,
                  venueRepo: OrgaVenueRepo,
                  sponsorPackRepo: OrgaSponsorPackRepo,
                  sponsorRepo: OrgaSponsorRepo) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    partnerRepo.list(params).map(partners => Ok(html.list(partners)(listBreadcrumb)))
  })

  def create(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createView(group, PartnerForms.create)
  })

  def doCreate(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    PartnerForms.create.bindFromRequest.fold(
      formWithErrors => createView(group, formWithErrors),
      data => partnerRepo.create(data).map(partnerElt => Redirect(routes.PartnerCtrl.detail(group, partnerElt.slug)))
    )
  })

  private def createView(group: Group.Slug, form: Form[Partner.Data])(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    val b = listBreadcrumb.add("New" -> routes.PartnerCtrl.create(group))
    IO.pure(Ok(html.create(form)(b)))
  }

  def detail(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      contacts <- OptionT.liftF(contactRepo.list(partnerElt.id))
      venues <- OptionT.liftF(venueRepo.listFull(partnerElt.id))
      packs <- OptionT.liftF(sponsorPackRepo.listActives)
      sponsors <- OptionT.liftF(sponsorRepo.listAllFull(partnerElt.id))
      events <- OptionT.liftF(eventRepo.list(partnerElt.id))
      users <- OptionT.liftF(userRepo.list((partnerElt.users ++ venues.flatMap(_.users)).distinct))
      b = breadcrumb(partnerElt)
      res = Ok(html.detail(partnerElt, venues, contacts, users, sponsors, packs, events)(b))
    } yield res).value.map(_.getOrElse(partnerNotFound(group, partner)))
  })

  def edit(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    editView(group, partner, PartnerForms.create)
  })

  def doEdit(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    PartnerForms.create.bindFromRequest.fold(
      formWithErrors => editView(group, partner, formWithErrors),
      data => for {
        partnerOpt <- partnerRepo.find(data.slug)
        res <- partnerOpt match {
          case Some(duplicate) if data.slug != partner =>
            editView(group, partner, PartnerForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by partner: ${duplicate.name.value}"))
          case _ =>
            partnerRepo.edit(partner, data).map { _ => Redirect(routes.PartnerCtrl.detail(group, data.slug)) }
        }
      } yield res
    )
  })

  private def editView(group: Group.Slug, partner: Partner.Slug, form: Form[Partner.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      filledForm = if (form.hasErrors) form else form.fill(partnerElt.data)
      b = breadcrumb(partnerElt).add("Edit" -> routes.PartnerCtrl.edit(group, partner))
    } yield Ok(html.edit(partnerElt, filledForm)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def doRemove(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    partnerRepo.remove(partner).map(_ => Redirect(routes.PartnerCtrl.list(group)))
  })
}

object PartnerCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb.add("Partners" -> routes.PartnerCtrl.list(req.group.slug))

  def breadcrumb(partner: Partner)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb.add(partner.name.value -> routes.PartnerCtrl.detail(req.group.slug, partner.slug))
}
