package fr.gospeak.core.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain._

final case class Event(id: Event.Id,
                       group: Group.Id,
                       slug: Event.Slug,
                       name: Event.Name,
                       start: LocalDateTime,
                       // duration: Option[Duration]
                       description: Option[Markdown],
                       venue: Option[GMapPlace],
                       talks: Seq[Proposal.Id],
                       info: Info) {
  def data: Event.Data = Event.Data(slug, name, start, venue)

  def add(talk: Proposal.Id): Event = copy(talks = talks :+ talk)

  def remove(talk: Proposal.Id): Event = copy(talks = talks.filter(_ != talk))

  def move(talk: Proposal.Id, up: Boolean): Event = copy(talks = talks.swap(talk, up))
}

object Event {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Event.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Event.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Event.Slug, name: Event.Name, start: LocalDateTime, venue: Option[GMapPlace])

}
