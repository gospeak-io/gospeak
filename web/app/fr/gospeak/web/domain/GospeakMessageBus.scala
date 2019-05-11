package fr.gospeak.web.domain

import cats.effect.IO
import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import fr.gospeak.core.domain.utils.GospeakMessage
import fr.gospeak.core.domain.{Cfp, Event, Group, Proposal}
import fr.gospeak.libs.scalautils.MessageBus
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import play.api.mvc.{AnyContent, RequestHeader}

class GospeakMessageBus(mb: MessageBus[GospeakMessage]) {
  def publishEventCreated(group: Group, event: Event)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Int] = {
    mb.publish(GospeakMessage.EventCreated(group, link(group), event, link(group, event), req.identity.user))
  }

  def publishTalkAdded(group: Group, event: Event, cfp: Cfp, proposal: Proposal)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Int] = {
    mb.publish(GospeakMessage.TalkAdded(group, link(group), event, link(group, event), cfp, link(group, cfp), proposal, link(group, cfp, proposal), req.identity.user))
  }

  def publishTalkRemoved(group: Group, event: Event, cfp: Cfp, proposal: Proposal)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Int] = {
    mb.publish(GospeakMessage.TalkRemoved(group, link(group), event, link(group, event), cfp, link(group, cfp), proposal, link(group, cfp, proposal), req.identity.user))
  }

  def publishProposalCreated(group: Group, cfp: Cfp, proposal: Proposal)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Int] = {
    mb.publish(GospeakMessage.ProposalCreated(group, link(group), cfp, link(group, cfp), proposal, link(group, cfp, proposal), req.identity.user))
  }

  def publishProposalCreated(group: Group, cfp: Cfp, proposal: Proposal, identity: AuthUser)(implicit req: UserAwareRequest[CookieEnv, AnyContent]): IO[Int] = {
    mb.publish(GospeakMessage.ProposalCreated(group, link(group), cfp, link(group, cfp), proposal, link(group, cfp, proposal), identity.user))
  }

  private def link(group: Group)(implicit request: RequestHeader): String =
    fr.gospeak.web.pages.orga.routes.GroupCtrl.detail(group.slug).absoluteURL()

  private def link(group: Group, event: Event)(implicit request: RequestHeader): String =
    fr.gospeak.web.pages.orga.events.routes.EventCtrl.detail(group.slug, event.slug).absoluteURL()

  private def link(group: Group, cfp: Cfp)(implicit request: RequestHeader): String =
    fr.gospeak.web.pages.orga.cfps.routes.CfpCtrl.detail(group.slug, cfp.slug).absoluteURL()

  private def link(group: Group, cfp: Cfp, proposal: Proposal)(implicit request: RequestHeader): String =
    fr.gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl.detail(group.slug, cfp.slug, proposal.id).absoluteURL()
}
