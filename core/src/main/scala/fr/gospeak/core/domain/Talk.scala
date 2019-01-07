package fr.gospeak.core.domain

case class Talk(id: Talk.Id)

object Talk {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Talk.Id]("Presentation.Id", new Talk.Id(_))

}
