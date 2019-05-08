package fr.gospeak.core.domain.utils

/*
  Formatted data for user templates (mustache for example)
 */
sealed trait TemplateData

object TemplateData {

  case class ProposalCreated(proposal: ProposalCreated.Proposal) extends TemplateData

  object ProposalCreated {

    case class Proposal(title: String, description: String)

  }

}
