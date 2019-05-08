package fr.gospeak.core.domain.utils

import fr.gospeak.core.domain.{Cfp, Group, Proposal}

/*
  Messages sent over the bus so any part of the app can listen to them
 */
sealed trait GospeakMessage

object GospeakMessage {

  sealed trait ProposalMessage extends GospeakMessage

  final case class ProposalCreated(group: Group.Id, proposal: Proposal) extends ProposalMessage

  object ProposalCreated {
    def apply(cfp: Cfp, proposal: Proposal): ProposalCreated = new ProposalCreated(cfp.group, proposal)
  }

}
