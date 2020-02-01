package gospeak.core.domain.utils

import gospeak.core.domain._

/*
  Messages sent over the bus so any part of the app can listen to them
 */
sealed trait GsMessage

object GsMessage {

  sealed trait EventMessage extends GsMessage

  final case class EventCreated(group: Linked[Group],
                                event: Linked[Event],
                                user: User) extends EventMessage

  final case class EventPublished(group: Linked[Group],
                                  event: Linked[Event],
                                  user: User) extends EventMessage


  sealed trait TalkMessage extends EventMessage

  final case class TalkAdded(group: Linked[Group],
                             event: Linked[Event],
                             cfp: Linked[Cfp],
                             proposal: Linked[Proposal],
                             user: User) extends TalkMessage

  final case class TalkRemoved(group: Linked[Group],
                               event: Linked[Event],
                               cfp: Linked[Cfp],
                               proposal: Linked[Proposal],
                               user: User) extends TalkMessage


  sealed trait ProposalMessage extends GsMessage

  final case class ProposalCreated(group: Linked[Group],
                                   cfp: Linked[Cfp],
                                   proposal: Linked[Proposal],
                                   user: User) extends ProposalMessage


  sealed trait ExternalCfpMessage extends GsMessage

  final case class ExternalCfpCreated(cfp: Linked[ExternalCfp],
                                      user: User) extends ExternalCfpMessage

  // nested classes

  final case class Linked[A](value: A, link: String, publicLink: Option[String])

}
