package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, UuidIdBuilder}

case class Event(id: Event.Id,
                 slug: Event.Slug,
                 group: Group.Id,
                 name: Event.Name,
                 description: String,
                 venue: Option[String],
                 talks: Seq[Proposal.Id])

object Event {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Event.Id]("Event.Id", new Event.Id(_))

  case class Slug(value: String) extends DataClass(value)

  case class Name(value: String) extends DataClass(value)

}
