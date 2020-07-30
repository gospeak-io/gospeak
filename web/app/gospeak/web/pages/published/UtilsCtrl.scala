package gospeak.web.pages.published

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain._
import gospeak.core.services.storage._
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.services.MessageSrv
import gospeak.web.utils.UICtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class UtilsCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                val groupRepo: OrgaGroupRepo,
                userRepo: PublicUserRepo,
                cfpRepo: PublicCfpRepo,
                eventRepo: PublicEventRepo,
                talkRepo: PublicTalkRepo,
                proposalRepo: PublicProposalRepo,
                partnerRepo: OrgaPartnerRepo,
                cfpExtRepo: PublicExternalCfpRepo,
                eventExtRepo: PublicExternalEventRepo,
                proposalExtRepo: PublicExternalProposalRepo,
                ms: MessageSrv) extends UICtrl(cc, silhouette, conf) with UICtrl.OrgaAction {
  def hovercardUser(user: User.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    userRepo.findPublic(user).map(_.map(u => Ok(partials.html.hovercardUser(u))).getOrElse(NotFound))
  }

  def hovercardGroup(group: Group.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    groupRepo.find(group).map(_.map(g => Ok(partials.html.hovercardGroup(g))).getOrElse(NotFound))
  }

  def hovercardEvent(group: Group.Slug, event: Event.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      e <- OptionT(eventRepo.findFull(group, event))
      info <- OptionT.liftF(ms.eventInfo(e))
    } yield Ok(partials.html.hovercardEvent(e, info))).value.map(_.getOrElse(NotFound))
  }

  def hovercardPartner(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    partnerRepo.find(partner).map(_.map(p => Ok(partials.html.hovercardPartner(p))).getOrElse(NotFound))
  }

  def hovercardProposal(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      g <- OptionT(groupRepo.find(group))
      p <- OptionT(proposalRepo.findPublicFull(g.id, proposal))
    } yield Ok(partials.html.hovercardProposal(p))).value.map(_.getOrElse(NotFound))
  }

  def hovercardCfp(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    cfpRepo.findRead(cfp).map(_.map(c => Ok(partials.html.hovercardCfp(c))).getOrElse(NotFound))
  }

  def hovercardTalk(talk: Talk.Slug): Action[AnyContent] = UserAction { implicit req =>
    // TODO: add proposal count
    talkRepo.find(talk).map(_.map(u => Ok(partials.html.hovercardTalk(u))).getOrElse(NotFound))
  }

  def hovercardCfpExt(cfp: ExternalCfp.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    cfpExtRepo.findFull(cfp).map(_.map(c => Ok(partials.html.hovercardCfpExt(c))).getOrElse(NotFound))
  }

  def hovercardEventExt(event: ExternalEvent.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    eventExtRepo.find(event).map(_.map(e => Ok(partials.html.hovercardEventExt(e))).getOrElse(NotFound))
  }

  def hovercardProposalExt(proposal: ExternalProposal.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    proposalExtRepo.findFull(proposal).map(_.map(p => Ok(partials.html.hovercardProposalExt(p))).getOrElse(NotFound))
  }
}
