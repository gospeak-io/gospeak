package fr.gospeak.core.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain._

final case class Event(id: Event.Id,
                       group: Group.Id,
                       cfp: Option[Cfp.Id],
                       slug: Event.Slug,
                       name: Event.Name,
                       start: LocalDateTime,
                       // duration: Option[Duration]
                       description: Markdown,
                       venue: Option[GMapPlace],
                       talks: Seq[Proposal.Id],
                       tags: Seq[Tag],
                       info: Info) {
  def data: Event.Data = Event.Data(this)

  def add(talk: Proposal.Id): Event = copy(talks = talks :+ talk)

  def remove(talk: Proposal.Id): Event = copy(talks = talks.filter(_ != talk))

  def move(talk: Proposal.Id, up: Boolean): Event = copy(talks = talks.swap(talk, up))
}

object Event {
  def apply(group: Group.Id, data: Data, info: Info): Event =
    new Event(Id.generate(), group, data.cfp, data.slug, data.name, data.start, Markdown(""), data.venue, Seq(), data.tags, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Event.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Event.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(cfp: Option[Cfp.Id],
                        slug: Event.Slug,
                        name: Event.Name,
                        start: LocalDateTime,
                        venue: Option[GMapPlace],
                        tags: Seq[Tag])

  object Data {
    def apply(event: Event): Data = new Data(event.cfp, event.slug, event.name, event.start, event.venue, event.tags)
  }

}
