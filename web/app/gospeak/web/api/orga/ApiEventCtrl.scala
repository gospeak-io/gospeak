package fr.gospeak.web.api.orga

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.{Event, Group}
import gospeak.core.services.storage.{OrgaEventRepo, OrgaGroupRepo, OrgaProposalRepo, OrgaUserRepo}
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.ApiEvent
import fr.gospeak.web.api.domain.utils.ApiResult
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import gospeak.libs.scala.domain.Page
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class ApiEventCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   conf: AppConf,
                   userRepo: OrgaUserRepo,
                   val groupRepo: OrgaGroupRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo) extends ApiCtrl(cc, silhouette, conf) with ApiCtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction[Seq[ApiEvent.Orga]](group) { implicit req =>
    for {
      events <- eventRepo.listFull(params)
      proposals <- proposalRepo.list(events.items.flatMap(_.talks))
      users <- userRepo.list(events.items.flatMap(_.users) ++ proposals.flatMap(_.users))
    } yield ApiResult.of(events, (e: Event.Full) => ApiEvent.orga(e, proposals, users))
  }

  def detail(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction[ApiEvent.Orga](group) { implicit req =>
    (for {
      eventElt <- OptionT(eventRepo.findFull(event))
      proposals <- OptionT.liftF(proposalRepo.list(eventElt.talks))
      users <- OptionT.liftF(userRepo.list(eventElt.users ++ proposals.flatMap(_.users)))
      res = ApiResult.of(ApiEvent.orga(eventElt, proposals, users))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
  }
}
