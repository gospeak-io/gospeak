package fr.gospeak.core.domain.utils

import fr.gospeak.core.domain._

/*
  Messages sent over the bus so any part of the app can listen to them
 */
sealed trait GospeakMessage

object GospeakMessage {

  sealed trait EventMessage extends GospeakMessage

  sealed trait TalkMessage extends EventMessage

  sealed trait ProposalMessage extends GospeakMessage


  final case class EventCreated(group: Group,
                                groupLink: String,
                                event: Event,
                                eventLink: String,
                                user: User) extends EventMessage

  final case class TalkAdded(group: Group,
                             groupLink: String,
                             event: Event,
                             eventLink: String,
                             cfp: Cfp,
                             cfpLink: String,
                             proposal: Proposal,
                             proposalLink: String,
                             user: User) extends TalkMessage

  final case class TalkRemoved(group: Group,
                               groupLink: String,
                               event: Event,
                               eventLink: String,
                               cfp: Cfp,
                               cfpLink: String,
                               proposal: Proposal,
                               proposalLink: String,
                               user: User) extends TalkMessage

  final case class EventPublished() extends EventMessage

  final case class ProposalCreated(group: Group,
                                   groupLink: String,
                                   cfp: Cfp,
                                   cfpLink: String,
                                   proposal: Proposal,
                                   proposalLink: String,
                                   user: User) extends ProposalMessage

}
