package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, Info, UuidIdBuilder}

case class Event(group: Group.Id,
                 id: Event.Id,
                 slug: Event.Slug,
                 name: Event.Name,
                 // start: datetime
                 // duration: Option[Duration]
                 description: Option[String],
                 venue: Option[String],
                 talks: Seq[Proposal.Id],
                 info: Info)

object Event {

  class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Event.Id]("Event.Id", new Event.Id(_))

  case class Slug(value: String) extends AnyVal

  case class Name(value: String) extends AnyVal

}
