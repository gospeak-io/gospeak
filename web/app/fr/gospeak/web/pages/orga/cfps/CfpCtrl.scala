package fr.gospeak.web.pages.orga.cfps

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Cfp, Event, Group}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.cfps.CfpCtrl._
import fr.gospeak.web.pages.orga.events.routes.{EventCtrl => EventRoutes}
import fr.gospeak.web.utils.{OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              env: ApplicationConf.Env,
              userRepo: OrgaUserRepo,
              val groupRepo: OrgaGroupRepo,
              cfpRepo: OrgaCfpRepo,
              eventRepo: OrgaEventRepo,
              proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val customParams = params.withNullsFirst
    cfpRepo.list(customParams).map(cfps => Ok(html.list(cfps)(listBreadcrumb))) // TODO listWithProposalCount
  })

  def create(group: Group.Slug, event: Option[Event.Slug]): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createView(group, CfpForms.create, event)
  })

  def doCreate(group: Group.Slug, event: Option[Event.Slug]): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => createView(group, formWithErrors, event),
      data => (for {
        // TODO check if slug not already exist
        cfpElt <- OptionT.liftF(cfpRepo.create(data))
        redirect <- OptionT.liftF(event.map { e =>
          eventRepo.attachCfp(e, cfpElt.id)
            .map(_ => Redirect(EventRoutes.detail(group, e)))
          // TODO recover and redirect to cfp detail
        }.getOrElse {
          IO.pure(Redirect(routes.CfpCtrl.detail(group, data.slug)))
        })
      } yield redirect).value.map(_.getOrElse(groupNotFound(group)))
    )
  })

  private def createView(group: Group.Slug, form: Form[Cfp.Data], event: Option[Event.Slug])(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    val b = listBreadcrumb.add("New" -> routes.CfpCtrl.create(group))
    IO.pure(Ok(html.create(form, event)(b)))
  }

  def detail(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposals <- OptionT.liftF(proposalRepo.listFull(cfpElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users).distinct))
      userRatings <- OptionT.liftF(proposalRepo.listRatings(cfp))
      b = breadcrumb(cfpElt)
    } yield Ok(html.detail(cfpElt, proposals, speakers, userRatings)(b))).value.map(_.getOrElse(cfpNotFound(group, cfp)))
  })

  def edit(group: Group.Slug, cfp: Cfp.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    editView(group, cfp, CfpForms.create)
  })

  def doEdit(group: Group.Slug, cfp: Cfp.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => editView(group, cfp, formWithErrors),
      data => (for {
        cfpOpt <- OptionT.liftF(cfpRepo.find(data.slug))
        res <- OptionT.liftF(cfpOpt match {
          case Some(duplicate) if data.slug != cfp =>
            editView(group, cfp, CfpForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by cfp: ${duplicate.name.value}"))
          case _ =>
            cfpRepo.edit(cfp, data).map { _ => Redirect(routes.CfpCtrl.detail(group, data.slug)) }
        })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    )
  })

  private def editView(group: Group.Slug, cfp: Cfp.Slug, form: Form[Cfp.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      filledForm = if (form.hasErrors) form else form.fill(cfpElt.data)
      b = breadcrumb(cfpElt).add("Edit" -> routes.CfpCtrl.edit(group, cfp))
    } yield Ok(html.edit(cfpElt, filledForm)(b))).value.map(_.getOrElse(cfpNotFound(group, cfp)))
  }
}

object CfpCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb.add("CFPs" -> routes.CfpCtrl.list(req.group.slug))

  def breadcrumb(cfp: Cfp)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb.add(cfp.name.value -> routes.CfpCtrl.detail(req.group.slug, cfp.slug))
}
