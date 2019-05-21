package fr.gospeak.web.domain

import java.time.LocalDateTime

import cats.effect.IO
import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import fr.gospeak.core.domain.utils.GospeakMessage
import fr.gospeak.core.domain.{Cfp, Event, Group, Proposal}
import fr.gospeak.libs.scalautils.MessageBus
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import play.api.mvc.AnyContent

class GospeakMessageBus(bus: MessageBus[GospeakMessage], builder: MessageBuilder) {
  def publishEventCreated(group: Group, event: Event)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Int] = {
    bus.publish(builder.buildEventCreated(group, event))
  }

  def publishTalkAdded(group: Group, event: Event, cfp: Cfp, proposal: Proposal, now: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Int] = {
    bus.publish(builder.buildTalkAdded(group, event, cfp, proposal, now))
  }

  def publishTalkRemoved(group: Group, event: Event, cfp: Cfp, proposal: Proposal, now: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Int] = {
    bus.publish(builder.buildTalkRemoved(group, event, cfp, proposal, now))
  }

  def publishProposalCreated(group: Group, cfp: Cfp, proposal: Proposal, now: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Int] = {
    bus.publish(builder.buildProposalCreated(group, cfp, proposal, now))
  }

  def publishProposalCreated(group: Group, cfp: Cfp, proposal: Proposal, identity: AuthUser, now: LocalDateTime)(implicit req: UserAwareRequest[CookieEnv, AnyContent]): IO[Int] = {
    bus.publish(builder.buildProposalCreated(group, cfp, proposal, identity, now))
  }
}
