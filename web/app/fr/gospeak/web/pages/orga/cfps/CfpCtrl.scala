package fr.gospeak.web.pages.orga.cfps

import java.time.{Instant, LocalDateTime}

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Cfp, Event, Group}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.cfps.CfpCtrl._
import fr.gospeak.web.pages.orga.events.routes.{EventCtrl => EventRoutes}
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              userRepo: OrgaUserRepo,
              groupRepo: OrgaGroupRepo,
              cfpRepo: OrgaCfpRepo,
              eventRepo: OrgaEventRepo,
              proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    val customParams = params.withNullsFirst
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      cfps <- OptionT.liftF(cfpRepo.list(groupElt.id, customParams)) // TODO listWithProposalCount
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, cfps)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def create(group: Group.Slug, event: Option[Event.Slug]): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(group, CfpForms.create, event).unsafeToFuture()
  }

  def doCreate(group: Group.Slug, event: Option[Event.Slug]): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => createForm(group, formWithErrors, event),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        // TODO check if slug not already exist
        cfpElt <- OptionT.liftF(cfpRepo.create(groupElt.id, data, by, now))
        redirect <- OptionT.liftF(event.map { e =>
          eventRepo.attachCfp(groupElt.id, e)(cfpElt.id, by, now)
            .map(_ => Redirect(EventRoutes.detail(group, e)))
          // TODO recover and redirect to cfp detail
        }.getOrElse {
          IO.pure(Redirect(routes.CfpCtrl.detail(group, data.slug)))
        })
      } yield redirect).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, form: Form[Cfp.Data], event: Option[Event.Slug])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      b = listBreadcrumb(groupElt).add("New" -> routes.CfpCtrl.create(group))
    } yield Ok(html.create(groupElt, form, event)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    val nowLDT = LocalDateTime.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposals <- OptionT.liftF(proposalRepo.listFull(cfpElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users).distinct))
      b = breadcrumb(groupElt, cfpElt)
    } yield Ok(html.detail(groupElt, cfpElt, proposals, speakers, nowLDT)(b))).value.map(_.getOrElse(cfpNotFound(group, cfp))).unsafeToFuture()
  }

  def edit(group: Group.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, cfp, CfpForms.create).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => editForm(group, cfp, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        cfpOpt <- OptionT.liftF(cfpRepo.find(groupElt.id, data.slug))
        res <- OptionT.liftF(cfpOpt match {
          case Some(duplicate) if data.slug != cfp =>
            editForm(group, cfp, CfpForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by cfp: ${duplicate.name.value}"))
          case _ =>
            cfpRepo.edit(groupElt.id, cfp)(data, by, now).map { _ => Redirect(routes.CfpCtrl.detail(group, data.slug)) }
        })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, cfp: Cfp.Slug, form: Form[Cfp.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      b = breadcrumb(groupElt, cfpElt).add("Edit" -> routes.CfpCtrl.edit(group, cfp))
      filledForm = if (form.hasErrors) form else form.fill(cfpElt.data)
    } yield Ok(html.edit(groupElt, cfpElt, filledForm)(b))).value.map(_.getOrElse(cfpNotFound(group, cfp)))
  }
}

object CfpCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("CFPs" -> routes.CfpCtrl.list(group.slug))

  def breadcrumb(group: Group, cfp: Cfp): Breadcrumb =
    listBreadcrumb(group).add(cfp.name.value -> routes.CfpCtrl.detail(group.slug, cfp.slug))
}
