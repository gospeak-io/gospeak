package fr.gospeak.core.domain

sealed trait GospeakMessage

object GospeakMessage {

  sealed trait ProposalMessage extends GospeakMessage

  object ProposalMessage {

    final case class ProposalCreated(proposal: Proposal) extends ProposalMessage

  }

}
