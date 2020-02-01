package gospeak.web.domain

import cats.effect.IO
import gospeak.core.domain.utils.GsMessage
import gospeak.core.domain.{Cfp, Event, ExternalCfp, Group, Proposal}
import gospeak.web.utils.{OrgaReq, UserReq}
import gospeak.libs.scala.MessageBus
import play.api.mvc.AnyContent

class GsMessageBus(bus: MessageBus[GsMessage], builder: MessageBuilder) {
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

  def publishExternalCfpCreated(cfp: ExternalCfp)(implicit req: UserReq[AnyContent]): IO[Int] =
    bus.publish(builder.buildExternalCfpCreated(cfp))
}
