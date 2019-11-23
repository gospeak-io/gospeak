package fr.gospeak.web.domain

import cats.effect.IO
import fr.gospeak.core.domain.utils.GospeakMessage
import fr.gospeak.core.domain.{Cfp, Event, Group, Proposal}
import fr.gospeak.libs.scalautils.MessageBus
import fr.gospeak.web.utils.UserReq
import play.api.mvc.AnyContent

class GospeakMessageBus(bus: MessageBus[GospeakMessage], builder: MessageBuilder) {
  def publishEventCreated(group: Group, event: Event)(implicit req: UserReq[AnyContent]): IO[Int] = {
    bus.publish(builder.buildEventCreated(group, event))
  }

  def publishTalkAdded(group: Group, event: Event, cfp: Cfp, proposal: Proposal)(implicit req: UserReq[AnyContent]): IO[Int] = {
    bus.publish(builder.buildTalkAdded(group, event, cfp, proposal))
  }

  def publishTalkRemoved(group: Group, event: Event, cfp: Cfp, proposal: Proposal)(implicit req: UserReq[AnyContent]): IO[Int] = {
    bus.publish(builder.buildTalkRemoved(group, event, cfp, proposal))
  }

  def publishProposalCreated(group: Group, cfp: Cfp, proposal: Proposal)(implicit req: UserReq[AnyContent]): IO[Int] = {
    bus.publish(builder.buildProposalCreated(group, cfp, proposal))
  }
}
