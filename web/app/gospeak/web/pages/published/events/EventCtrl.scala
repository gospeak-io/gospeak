package gospeak.web.pages.published.events

import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.services.storage.{PublicExternalEventRepo, PublicExternalProposalRepo, PublicUserRepo}
import gospeak.libs.scala.domain.Page
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.utils.UICtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import EventCtrl._
import cats.data.OptionT
import gospeak.core.domain.{ExternalEvent, ExternalProposal}
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.published.HomeCtrl

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

  def detailExt(event: ExternalEvent.Id, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      eventElt <- OptionT(externalEventRepo.find(event))
      proposals <- OptionT.liftF(externalProposalRepo.list(event, params))
      users <- OptionT.liftF(userRepo.list((eventElt.users ++ proposals.items.flatMap(_.users)).distinct))
      res = Ok(html.detailExt(eventElt, proposals, users)(breadcrumb(eventElt)))
    } yield res).value.map(_.getOrElse(publicEventNotFound(event)))
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
