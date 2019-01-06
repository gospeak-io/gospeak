package fr.gospeak.core.domain

case class Event(id: Event.Id,
                 group: Group.Id)

object Event {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Event.Id]("Event.Id", new Event.Id(_))

}
