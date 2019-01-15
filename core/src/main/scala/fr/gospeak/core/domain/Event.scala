package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, Meta, UuidIdBuilder}

case class Event(id: Event.Id,
                 slug: Event.Slug,
                 group: Group.Id,
                 name: Event.Name,
                 // start: datetime
                 // duration: Option[Duration]
                 description: Option[String],
                 venue: Option[String],
                 talks: Seq[Proposal.Id],
                 meta: Meta)

object Event {

  class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Event.Id]("Event.Id", new Event.Id(_))

  case class Slug(value: String) extends AnyVal

  case class Name(value: String) extends AnyVal

}
