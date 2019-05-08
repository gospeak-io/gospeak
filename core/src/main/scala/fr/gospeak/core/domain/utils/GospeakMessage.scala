package fr.gospeak.core.domain.utils

import fr.gospeak.core.domain.{Cfp, Proposal, User}

/*
  Messages sent over the bus so any part of the app can listen to them
 */
sealed trait GospeakMessage

object GospeakMessage {

  sealed trait ProposalMessage extends GospeakMessage

  final case class ProposalCreated(cfp: Cfp, proposal: Proposal, user: User) extends ProposalMessage

}
