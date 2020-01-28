package fr.gospeak.web.domain

import cats.effect.IO
import gospeak.core.domain.utils.GospeakMessage
import gospeak.core.domain.{Cfp, Event, Group, Proposal}
import fr.gospeak.web.utils.{OrgaReq, UserReq}
import gospeak.libs.scala.MessageBus
import play.api.mvc.AnyContent

class GospeakMessageBus(bus: MessageBus[GospeakMessage], builder: MessageBuilder) {
  def publishEventCreated(event: Event)(implicit req: OrgaReq[AnyContent]): IO[Int] =
    bus.publish(builder.buildEventCreated(event))

  def publishTalkAdded(event: Event, cfp: Cfp, proposal: Proposal)(implicit req: OrgaReq[AnyContent]): IO[Int] =
    bus.publish(builder.buildTalkAdded(event, cfp, proposal))

  def publishTalkRemoved(event: Event, cfp: Cfp, proposal: Proposal)(implicit req: OrgaReq[AnyContent]): IO[Int] =
    bus.publish(builder.buildTalkRemoved(event, cfp, proposal))

  def publishEventPublished(event: Event)(implicit req: OrgaReq[AnyContent]): IO[Int] =
    bus.publish(builder.buildEventPublished(event))

  def publishProposalCreated(group: Group, cfp: Cfp, proposal: Proposal)(implicit req: UserReq[AnyContent]): IO[Int] =
    bus.publish(builder.buildProposalCreated(group, cfp, proposal))
}
