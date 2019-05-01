package fr.gospeak.web.pages.orga.partners

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, Partner}
import fr.gospeak.core.services.{OrgaGroupRepo, OrgaPartnerRepo, OrgaUserRepo, OrgaVenueRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.partners.PartnerCtrl._
import fr.gospeak.web.utils.Mappings._
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class PartnerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  userRepo: OrgaUserRepo,
                  groupRepo: OrgaGroupRepo,
                  partnerRepo: OrgaPartnerRepo,
                  venueRepo: OrgaVenueRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  private val createForm: Form[Partner.Data] = Form(mapping(
    "slug" -> partnerSlug,
    "name" -> partnerName,
    "description" -> markdown,
    "logo" -> url
  )(Partner.Data.apply)(Partner.Data.unapply))

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      partners <- OptionT.liftF(partnerRepo.list(groupElt.id, params))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, partners)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
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
        partnerElt <- OptionT.liftF(partnerRepo.create(groupElt.id, data, by, now))
      } yield Redirect(routes.PartnerCtrl.detail(group, partnerElt.slug))).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, form: Form[Partner.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      b = listBreadcrumb(groupElt).add("New" -> routes.PartnerCtrl.create(group))
    } yield Ok(html.create(groupElt, form)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      partnerElt <- OptionT(partnerRepo.find(groupElt.id, partner))
      venues <- OptionT.liftF(venueRepo.list(partnerElt.id))
      users <- OptionT.liftF(userRepo.list((partnerElt.users ++ venues.flatMap(_.users)).distinct))
      b = breadcrumb(groupElt, partnerElt)
    } yield Ok(html.detail(groupElt, partnerElt, venues, users)(b))).value.map(_.getOrElse(partnerNotFound(group, partner))).unsafeToFuture()
  }

  def edit(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, partner, createForm).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    createForm.bindFromRequest.fold(
      formWithErrors => editForm(group, partner, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        partnerOpt <- OptionT.liftF(partnerRepo.find(groupElt.id, data.slug))
        res <- OptionT.liftF(partnerOpt match {
          case Some(duplicate) if data.slug != partner =>
            editForm(group, partner, createForm.fillAndValidate(data).withError("slug", s"Slug already taken by partner: ${duplicate.name.value}"))
          case _ =>
            partnerRepo.edit(groupElt.id, partner)(data, by, now).map { _ => Redirect(routes.PartnerCtrl.detail(group, data.slug)) }
        })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, partner: Partner.Slug, form: Form[Partner.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      partnerElt <- OptionT(partnerRepo.find(groupElt.id, partner))
      b = breadcrumb(groupElt, partnerElt).add("Edit" -> routes.PartnerCtrl.edit(group, partner))
      filledForm = if (form.hasErrors) form else form.fill(partnerElt.data)
    } yield Ok(html.edit(groupElt, partnerElt, filledForm)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }
}

object PartnerCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Partners" -> routes.PartnerCtrl.list(group.slug))

  def breadcrumb(group: Group, partner: Partner): Breadcrumb =
    listBreadcrumb(group).add(partner.name.value -> routes.PartnerCtrl.detail(group.slug, partner.slug))
}
