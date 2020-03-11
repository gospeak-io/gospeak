package gospeak.core.domain.messages

import java.time.Instant

import gospeak.core.domain.Group
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.CustomException

/**
 * Base trait for messages about what happen in Gospeak
 * This is like 'Event' in event sourcing but with a different name as Event is a business word in Gospeak
 *
 * Possible alternative names: Event, Message, Command, Result, Action, Task, Status, News, Report, Notification
 *
 *
 * WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING
 * WARNING                                                                 WARNING
 * WARNING     Theses classes are used in user templating (mustache)       WARNING
 * WARNING     Be careful updating them as it may break user templates     WARNING
 * WARNING                                                                 WARNING
 * WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING
 */
sealed trait Message {
  val ref: Message.Ref = Message.Ref(getClass.getSimpleName)
}

object Message {

  sealed trait GroupMessage extends Message {
    val group: MsgGroup
  }

  final case class EventCreated(group: MsgGroup, event: MsgEvent, by: MsgUser.Embed, at: Instant) extends GroupMessage

  final case class EventPublished(group: MsgGroup, event: MsgEvent, by: MsgUser.Embed, at: Instant) extends GroupMessage

  final case class EventInfo(group: MsgGroup, event: MsgEvent) extends GroupMessage


  final case class ProposalCreated(group: MsgGroup, cfp: MsgCfp, proposal: MsgProposal, by: MsgUser.Embed, at: Instant) extends GroupMessage

  final case class ProposalAddedToEvent(group: MsgGroup, cfp: MsgCfp, proposal: MsgProposal, event: MsgEvent, by: MsgUser.Embed, at: Instant) extends GroupMessage

  final case class ProposalRemovedFromEvent(group: MsgGroup, cfp: MsgCfp, proposal: MsgProposal, event: MsgEvent, by: MsgUser.Embed, at: Instant) extends GroupMessage

  final case class ProposalInfo(group: MsgGroup, cfp: MsgCfp, proposal: MsgProposal, event: Option[MsgEvent.Embed]) extends GroupMessage


  final case class ExternalEventCreated(event: MsgExternalEvent, by: MsgUser.Embed, at: Instant) extends Message

  final case class ExternalEventUpdated(event: MsgExternalEvent, by: MsgUser.Embed, at: Instant) extends Message


  final case class ExternalCfpCreated(event: MsgExternalEvent, cfp: MsgExternalCfp, by: MsgUser.Embed, at: Instant) extends Message

  final case class ExternalCfpUpdated(event: MsgExternalEvent, cfp: MsgExternalCfp, by: MsgUser.Embed, at: Instant) extends Message


  final case class Ref(value: String)

  object Ref {
    val eventCreated: Ref = Ref(classOf[EventCreated].getSimpleName)
    val eventPublished: Ref = Ref(classOf[EventPublished].getSimpleName)
    val eventInfo: Ref = Ref(classOf[EventInfo].getSimpleName)
    val proposalCreated: Ref = Ref(classOf[ProposalCreated].getSimpleName)
    val proposalAddedToEvent: Ref = Ref(classOf[ProposalAddedToEvent].getSimpleName)
    val proposalRemovedFromEvent: Ref = Ref(classOf[ProposalRemovedFromEvent].getSimpleName)
    val proposalInfo: Ref = Ref(classOf[ProposalInfo].getSimpleName)
    val externalEventCreated: Ref = Ref(classOf[ExternalEventCreated].getSimpleName)
    val externalEventUpdated: Ref = Ref(classOf[ExternalEventUpdated].getSimpleName)
    val externalCfpCreated: Ref = Ref(classOf[ExternalCfpCreated].getSimpleName)
    val externalCfpUpdated: Ref = Ref(classOf[ExternalCfpUpdated].getSimpleName)

    val all = List(
      eventCreated, eventPublished, eventInfo,
      proposalCreated, proposalAddedToEvent, proposalRemovedFromEvent, proposalInfo,
      externalEventCreated, externalEventUpdated,
      externalCfpCreated, externalCfpUpdated)

    def from(in: String): Either[CustomException, Ref] =
      all.find(_.value == in)
        .orElse(Group.Settings.Action.Trigger.all.find(_.value == in).map(_.message))
        .toEither(CustomException(s"Unknown Message.Ref '$in'"))
  }

}
