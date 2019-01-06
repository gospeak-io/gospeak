package fr.gospeak.core.domain

case class Presentation(id: Presentation.Id)

object Presentation {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Presentation.Id]("Presentation.Id", new Presentation.Id(_))

}
