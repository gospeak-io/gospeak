package fr.gospeak.core.domain

case class Proposal(id: Proposal.Id)

object Proposal {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Proposal.Id]("Submission.Id", new Proposal.Id(_))

}
