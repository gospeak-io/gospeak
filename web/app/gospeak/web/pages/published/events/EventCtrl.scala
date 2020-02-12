package gospeak.web.pages.published.events

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.{ExternalEvent, ExternalProposal}
import gospeak.core.services.storage.{PublicExternalEventRepo, PublicExternalProposalRepo, PublicUserRepo}
import gospeak.libs.scala.domain.Page
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.published.HomeCtrl
import gospeak.web.pages.published.events.EventCtrl._
import gospeak.web.utils.{GsForms, UICtrl, UserReq}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class EventCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                userRepo: PublicUserRepo,
                externalEventRepo: PublicExternalEventRepo,
                externalProposalRepo: PublicExternalProposalRepo) extends UICtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    for {
      events <- externalEventRepo.listCommon(params)
      users <- userRepo.list(events.items.flatMap(_.users))
    } yield Ok(html.list(events, users)(listBreadcrumb()))
  }

  def create(): Action[AnyContent] = UserAction { implicit req =>
    createView(GsForms.externalEvent)
  }

  def doCreate(): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalEvent.bindFromRequest.fold(
      formWithErrors => createView(formWithErrors),
      data => externalEventRepo.create(data).map(e => Redirect(routes.EventCtrl.detailExt(e.id)))
    )
  }

  private def createView(form: Form[ExternalEvent.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    IO.pure(Ok(html.createExt(form)(listBreadcrumb().add("Add event" -> routes.EventCtrl.create()))))
  }

  def detailExt(event: ExternalEvent.Id, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      eventElt <- OptionT(externalEventRepo.find(event))
      proposals <- OptionT.liftF(externalProposalRepo.list(event, params))
      users <- OptionT.liftF(userRepo.list((eventElt.users ++ proposals.items.flatMap(_.users)).distinct))
      res = Ok(html.detailExt(eventElt, proposals, users)(breadcrumb(eventElt)))
    } yield res).value.map(_.getOrElse(publicEventNotFound(event)))
  }

  def edit(event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    editView(event, GsForms.externalEvent)
  }

  def doEdit(event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalEvent.bindFromRequest.fold(
      formWithErrors => editView(event, formWithErrors),
      data => externalEventRepo.edit(event)(data).map(_ => Redirect(routes.EventCtrl.detailExt(event)).flashing("success" -> "Event updated"))
    )
  }

  private def editView(event: ExternalEvent.Id, form: Form[ExternalEvent.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      eventElt <- OptionT(externalEventRepo.find(event))
      b = breadcrumb(eventElt).add("Edit" -> routes.EventCtrl.edit(event))
      filledForm = if (form.hasErrors) form else form.fill(eventElt.data)
    } yield Ok(html.editExt(eventElt, filledForm)(b))).value.map(_.getOrElse(publicEventNotFound(event)))
  }

  def proposalExt(event: ExternalEvent.Id, proposal: ExternalProposal.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      eventElt <- OptionT(externalEventRepo.find(event))
      proposalElt <- OptionT(externalProposalRepo.find(proposal))
      users <- OptionT.liftF(userRepo.list((eventElt.users ++ proposalElt.users).distinct))
      res = Ok(html.proposalExt(eventElt, proposalElt, users)(breadcrumb(eventElt, proposalElt)))
    } yield res).value.map(_.getOrElse(publicProposalNotFound(event, proposal)))
  }
}

object EventCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Events" -> routes.EventCtrl.list())

  def breadcrumb(event: ExternalEvent): Breadcrumb =
    listBreadcrumb().add(event.name.value -> routes.EventCtrl.detailExt(event.id))

  def breadcrumb(event: ExternalEvent, proposal: ExternalProposal): Breadcrumb =
    breadcrumb(event).add("Talks" -> routes.EventCtrl.detailExt(event.id)).add(proposal.title.value -> routes.EventCtrl.proposalExt(event.id, proposal.id))
}
