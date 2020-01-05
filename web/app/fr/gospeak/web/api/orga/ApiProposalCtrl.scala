package fr.gospeak.web.api.orga

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Cfp, Group, Proposal}
import fr.gospeak.core.services.storage.{OrgaCfpRepo, OrgaGroupRepo, OrgaProposalRepo, OrgaUserRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.ApiProposal
import fr.gospeak.web.api.domain.utils.ApiResult
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class ApiProposalCtrl(cc: ControllerComponents,
                      silhouette: Silhouette[CookieEnv],
                      conf: AppConf,
                      val groupRepo: OrgaGroupRepo,
                      cfpRepo: OrgaCfpRepo,
                      proposalRepo: OrgaProposalRepo,
                      userRepo: OrgaUserRepo) extends ApiCtrl(cc, silhouette, conf) with ApiCtrl.OrgaAction {
  def list(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = OrgaAction[Seq[ApiProposal.Orga]](group) { implicit req =>
    for {
      proposals <- proposalRepo.listFull(cfp, params)
      users <- userRepo.list(proposals.items.flatMap(_.users))
    } yield ApiResult.of(proposals, (p: Proposal.Full) => ApiProposal.orga(p, users))
  }

  def listAll(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction[Seq[ApiProposal.Orga]](group) { implicit req =>
    for {
      proposals <- proposalRepo.listFull(params)
      users <- userRepo.list(proposals.items.flatMap(_.users))
    } yield ApiResult.of(proposals, (p: Proposal.Full) => ApiProposal.orga(p, users))
  }

  def detail(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction[ApiProposal.Orga](group) { implicit req =>
    (for {
      proposalElt <- OptionT(proposalRepo.findFull(cfp, proposal))
      users <- OptionT.liftF(userRepo.list(proposalElt.users))
      res = ApiResult.of(ApiProposal.orga(proposalElt, users))
    } yield res).value.map(_.getOrElse(proposalNotFound(cfp, proposal)))
  }

  // TODO def ratings(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)
  // TODO def speakerComments(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)
  // TODO def orgaComments(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)
  // TODO def invites(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)
}
