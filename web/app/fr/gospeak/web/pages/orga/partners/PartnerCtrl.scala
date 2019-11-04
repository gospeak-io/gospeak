package fr.gospeak.web.pages.orga.partners

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Group, Partner}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.partners.PartnerCtrl._
import fr.gospeak.web.utils.{SecuredReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class PartnerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  userRepo: OrgaUserRepo,
                  groupRepo: OrgaGroupRepo,
                  eventRepo: OrgaEventRepo,
                  partnerRepo: OrgaPartnerRepo,
                  contactRepo: ContactRepo,
                  venueRepo: OrgaVenueRepo,
                  sponsorPackRepo: OrgaSponsorPackRepo,
                  sponsorRepo: OrgaSponsorRepo) extends UICtrl(cc, silhouette) {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      partners <- OptionT.liftF(partnerRepo.list(groupElt.id, params))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, partners)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def create(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    createView(group, PartnerForms.create)
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    PartnerForms.create.bindFromRequest.fold(
      formWithErrors => createView(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.user.id, group))
        partnerElt <- OptionT.liftF(partnerRepo.create(groupElt.id, data, req.user.id, req.now))
      } yield Redirect(routes.PartnerCtrl.detail(group, partnerElt.slug))).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def createView(group: Group.Slug, form: Form[Partner.Data])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      b = listBreadcrumb(groupElt).add("New" -> routes.PartnerCtrl.create(group))
    } yield Ok(html.create(groupElt, form)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      partnerElt <- OptionT(partnerRepo.find(groupElt.id, partner))
      contacts <- OptionT.liftF(contactRepo.list(partnerElt.id))
      venues <- OptionT.liftF(venueRepo.listFull(partnerElt.id))
      packs <- OptionT.liftF(sponsorPackRepo.listActives(groupElt.id))
      sponsors <- OptionT.liftF(sponsorRepo.listAllFull(groupElt.id, partnerElt.id))
      events <- OptionT.liftF(eventRepo.list(groupElt.id, partnerElt.id))
      users <- OptionT.liftF(userRepo.list((partnerElt.users ++ venues.flatMap(_.users)).distinct))
      b = breadcrumb(groupElt, partnerElt)
      res = Ok(html.detail(groupElt, partnerElt, venues, contacts, users, sponsors, packs, events)(b))
    } yield res).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def edit(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    editView(group, partner, PartnerForms.create)
  }

  def doEdit(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    PartnerForms.create.bindFromRequest.fold(
      formWithErrors => editView(group, partner, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.user.id, group))
        partnerOpt <- OptionT.liftF(partnerRepo.find(groupElt.id, data.slug))
        res <- OptionT.liftF(partnerOpt match {
          case Some(duplicate) if data.slug != partner =>
            editView(group, partner, PartnerForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by partner: ${duplicate.name.value}"))
          case _ =>
            partnerRepo.edit(groupElt.id, partner)(data, req.user.id, req.now).map { _ => Redirect(routes.PartnerCtrl.detail(group, data.slug)) }
        })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def editView(group: Group.Slug, partner: Partner.Slug, form: Form[Partner.Data])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      partnerElt <- OptionT(partnerRepo.find(groupElt.id, partner))
      b = breadcrumb(groupElt, partnerElt).add("Edit" -> routes.PartnerCtrl.edit(group, partner))
      filledForm = if (form.hasErrors) form else form.fill(partnerElt.data)
    } yield Ok(html.edit(groupElt, partnerElt, filledForm)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def doRemove(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      _ <- OptionT.liftF(partnerRepo.remove(groupElt.id, partner)(req.user.id, req.now))
    } yield Redirect(routes.PartnerCtrl.list(group))).value.map(_.getOrElse(groupNotFound(group)))
  }
}

object PartnerCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Partners" -> routes.PartnerCtrl.list(group.slug))

  def breadcrumb(group: Group, partner: Partner): Breadcrumb =
    listBreadcrumb(group).add(partner.name.value -> routes.PartnerCtrl.detail(group.slug, partner.slug))
}
