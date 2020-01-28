package fr.gospeak.web.api.orga

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.{Cfp, Group, Proposal}
import gospeak.core.services.storage._
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.utils.ApiResult
import fr.gospeak.web.api.domain.{ApiComment, ApiProposal}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import gospeak.libs.scala.domain.Page
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class ApiProposalCtrl(cc: ControllerComponents,
                      silhouette: Silhouette[CookieEnv],
                      conf: AppConf,
                      userRepo: OrgaUserRepo,
                      val groupRepo: OrgaGroupRepo,
                      cfpRepo: OrgaCfpRepo,
                      proposalRepo: OrgaProposalRepo,
                      commentRepo: OrgaCommentRepo,
                      userRequestRepo: OrgaUserRequestRepo) extends ApiCtrl(cc, silhouette, conf) with ApiCtrl.OrgaAction {
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

  def ratings(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction[Seq[ApiProposal.Orga.Rating]](group) { implicit req =>
    for {
      ratings <- proposalRepo.listRatings(proposal)
      users <- userRepo.list(ratings.flatMap(_.users))
    } yield ApiResult.of(ratings.map(ApiProposal.Orga.Rating.from(_, users)))
  }

  def speakerComments(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction[Seq[ApiComment]](group) { implicit req =>
    commentRepo.getComments(proposal).map(comments => ApiResult.of(comments.map(ApiComment.from)))
  }

  def orgaComments(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction[Seq[ApiComment]](group) { implicit req =>
    commentRepo.getOrgaComments(proposal).map(comments => ApiResult.of(comments.map(ApiComment.from)))
  }

  def invites(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction[Int](group) { implicit req =>
    userRequestRepo.listPendingInvites(proposal)
    ??? // TODO
  }
}
