package gospeak.web.pages.published.events

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.messages.Message
import gospeak.core.domain.{ExternalEvent, ExternalProposal, Talk}
import gospeak.core.services.storage._
import gospeak.libs.scala.MessageBus
import gospeak.libs.scala.domain.Page
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.published.HomeCtrl
import gospeak.web.pages.published.events.EventCtrl._
import gospeak.web.services.MessageSrv
import gospeak.web.utils.{GsForms, UICtrl, UserReq}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class EventCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                userRepo: PublicUserRepo,
                talkRepo: PublicTalkRepo,
                externalEventRepo: PublicExternalEventRepo,
                externalCfpRepo: PublicExternalCfpRepo,
                externalProposalRepo: PublicExternalProposalRepo,
                ms: MessageSrv,
                bus: MessageBus[Message]) extends UICtrl(cc, silhouette, conf) {
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
      data => for {
        e <- externalEventRepo.create(data)
        _ <- ms.externalEventCreated(e).map(bus.publish)
      } yield Redirect(routes.EventCtrl.detailExt(e.id))
    )
  }

  private def createView(form: Form[ExternalEvent.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    IO.pure(Ok(html.createExt(form)(listBreadcrumb().add("Add event" -> routes.EventCtrl.create()))))
  }

  def detailExt(event: ExternalEvent.Id, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      eventElt <- OptionT(externalEventRepo.find(event))
      cfps <- OptionT.liftF(externalCfpRepo.listAll(eventElt.id))
      proposals <- OptionT.liftF(externalProposalRepo.listPublic(event, params))
      users <- OptionT.liftF(userRepo.list((eventElt.users ++ proposals.items.flatMap(_.users)).distinct))
      res = Ok(html.detailExt(eventElt, cfps, proposals, users)(breadcrumb(eventElt)))
    } yield res).value.map(_.getOrElse(publicEventNotFound(event)))
  }

  def edit(event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    editView(event, GsForms.externalEvent)
  }

  def doEdit(event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalEvent.bindFromRequest.fold(
      formWithErrors => editView(event, formWithErrors),
      data => (for {
        _ <- OptionT.liftF(externalEventRepo.edit(event)(data))
        eventElt <- OptionT(externalEventRepo.find(event))
        _ <- OptionT.liftF(ms.externalEventUpdated(eventElt).map(bus.publish))
        res = Redirect(routes.EventCtrl.detailExt(event)).flashing("success" -> "Event updated")
      } yield res).value.map(_.getOrElse(extEventNotFound(event)))
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
      proposalElt <- OptionT(externalProposalRepo.findFull(proposal)).filter(_.event.id == event)
      users <- OptionT.liftF(userRepo.list(proposalElt.users))
      res = Ok(html.proposalExt(proposalElt, users)(breadcrumb(proposalElt)))
    } yield res).value.map(_.getOrElse(publicProposalNotFound(event, proposal)))
  }

  def findTalk(event: ExternalEvent.Id, params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    (for {
      eventElt <- OptionT(externalEventRepo.find(event))
      talks <- OptionT.liftF(talkRepo.list(params))
      res = Ok(html.findTalk(eventElt, talks)(talksBreadcrumb(eventElt)))
    } yield res).value.map(_.getOrElse(publicEventNotFound(event)))
  }

  def createTalk(event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    createTalkView(event, GsForms.talk)
  }

  def doCreateTalk(event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    GsForms.talk.bindFromRequest.fold(
      formWithErrors => createTalkView(event, formWithErrors),
      data => talkRepo.create(data).map { talk =>
        Redirect(routes.EventCtrl.createExternalProposal(event, talk.slug))
          .flashing("success" -> s"<b>Well done!</b> Your talk ${data.title.value} is created")
      }
    )
  }

  private def createTalkView(event: ExternalEvent.Id, form: Form[Talk.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      eventElt <- OptionT(externalEventRepo.find(event))
      res = Ok(html.createTalk(eventElt, form)(talksBreadcrumb(eventElt).add("Create" -> routes.EventCtrl.createTalk(event))))
    } yield res).value.map(_.getOrElse(extEventNotFound(event)))
  }

  def createExternalProposal(event: ExternalEvent.Id, talk: Talk.Slug): Action[AnyContent] = UserAction { implicit req =>
    createExternalProposalView(event, talk, GsForms.externalProposal)
  }

  def doCreateExternalProposal(event: ExternalEvent.Id, talk: Talk.Slug): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalProposal.bindFromRequest.fold(
      formWithErrors => createExternalProposalView(event, talk, formWithErrors),
      data => (for {
        talkElt <- OptionT(talkRepo.find(talk))
        _ <- OptionT.liftF(externalProposalRepo.create(talkElt.id, event, data, talkElt.speakers))
      } yield Redirect(routes.EventCtrl.detailExt(event))).value.map(_.getOrElse(talkNotFound(talk)))
    )
  }

  private def createExternalProposalView(event: ExternalEvent.Id, talk: Talk.Slug, form: Form[ExternalProposal.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      eventElt <- OptionT(externalEventRepo.find(event))
      talkElt <- OptionT(talkRepo.find(talk))
      filledForm = if (form.hasErrors) form else form.fill(ExternalProposal.Data(talkElt))
      res = Ok(html.createExternalProposal(eventElt, talkElt, filledForm)(breadcrumb(eventElt, talkElt).add("Add proposal" -> routes.EventCtrl.createExternalProposal(event, talk))))
    } yield res).value.map(_.getOrElse(extEventNotFound(talk, event)))
  }
}

object EventCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Events" -> routes.EventCtrl.list())

  def breadcrumb(event: ExternalEvent): Breadcrumb =
    listBreadcrumb().add(event.name.value -> routes.EventCtrl.detailExt(event.id))

  def breadcrumb(proposal: ExternalProposal.Full): Breadcrumb =
    breadcrumb(proposal.event)
      .add("Talks" -> routes.EventCtrl.detailExt(proposal.event.id))
      .add(proposal.title.value -> routes.EventCtrl.proposalExt(proposal.event.id, proposal.id))

  def talksBreadcrumb(event: ExternalEvent): Breadcrumb =
    breadcrumb(event).add("Your talks" -> routes.EventCtrl.findTalk(event.id))

  def breadcrumb(event: ExternalEvent, talk: Talk): Breadcrumb =
    talksBreadcrumb(event).add(talk.title.value -> routes.EventCtrl.createExternalProposal(event.id, talk.slug))
}
