package fr.gospeak.core.domain

case class Submission(id: Submission.Id)

object Submission {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Submission.Id]("Submission.Id", new Submission.Id(_))

}
