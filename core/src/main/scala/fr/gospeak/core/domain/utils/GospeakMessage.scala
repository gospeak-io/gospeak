package fr.gospeak.core.domain.utils

import fr.gospeak.core.domain.{Cfp, Event, Group, Proposal, User}

/*
  Messages sent over the bus so any part of the app can listen to them
 */
sealed trait GospeakMessage

object GospeakMessage {

  sealed trait EventMessage extends GospeakMessage

  sealed trait TalkMessage extends EventMessage

  sealed trait ProposalMessage extends GospeakMessage


  final case class EventCreated(group: Group, event: Event, user: User) extends EventMessage

  final case class TalkAdded(group: Group, event: Event, cfp: Cfp, proposal: Proposal, user: User) extends TalkMessage

  final case class TalkRemoved(group: Group, event: Event, cfp: Cfp, proposal: Proposal, user: User) extends TalkMessage

  final case class EventPublished() extends EventMessage // TODO implement

  final case class ProposalCreated(cfp: Cfp, proposal: Proposal, user: User) extends ProposalMessage

}
