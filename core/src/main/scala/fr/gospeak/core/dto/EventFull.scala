package fr.gospeak.core.dto

import java.time.LocalDateTime

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, ValidatedNec}
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain.{Markdown, Tag}

// same as fr.gospeak.core.domain.Event but with some entity already loaded
final case class EventFull(id: Event.Id,
                           group: Group,
                           cfp: Option[Cfp],
                           slug: Event.Slug,
                           name: Event.Name,
                           start: LocalDateTime,
                           // duration: Option[Duration]
                           description: Markdown,
                           venue: Option[VenueFull],
                           talks: Seq[ProposalWithSpeakers],
                           tags: Seq[Tag],
                           info: Info) {
  def toEvent: Event = Event(id, group.id, cfp.map(_.id), slug, name, start, description, venue.map(_.id), talks.map(_.id), tags, info)
}

object EventFull {
  def from(group: Group, event: Event, cfp: Option[Cfp], venue: Option[(Venue, Partner)], talks: Seq[Proposal], speakers: Seq[User]): ValidatedNec[String, EventFull] = {
    import cats.implicits._
    (validGroup(event, group), validCfp(event, cfp), validVenue(event, venue), validTalks(event, talks, speakers)).mapN { (g, c, v, t) =>
      new EventFull(
        id = event.id,
        group = g,
        cfp = c,
        slug = event.slug,
        name = event.name,
        start = event.start,
        description = event.description,
        venue = v,
        talks = t,
        tags = event.tags,
        info = event.info)
    }
  }

  private def validGroup(event: Event, group: Group): ValidatedNec[String, Group] = {
    if (group.id == event.group) {
      Validated.Valid(group)
    } else {
      Validated.invalidNec(s"Group id mismatch: expect ${event.group.value} but got ${group.id.value} (${group.name.value})")
    }
  }

  private def validCfp(event: Event, cfp: Option[Cfp]): ValidatedNec[String, Option[Cfp]] = {
    (event.cfp, cfp) match {
      case (Some(id), Some(c)) =>
        if (id == c.id) {
          Validated.Valid(Some(c))
        } else {
          Validated.invalidNec(s"Cfp id mismatch: expect ${id.value} but got ${c.id.value} (${c.name.value})")
        }
      case (None, None) => Validated.Valid(None)
      case (Some(id), None) => Validated.invalidNec(s"Cfp not found but expect ${id.value}")
      case (None, Some(elt)) => Validated.invalidNec(s"Unknown Cfp found: expect no Cfp but got ${elt.id.value} (${elt.name.value})")
    }
  }

  private def validVenue(event: Event, venue: Option[(Venue, Partner)]): ValidatedNec[String, Option[VenueFull]] = {
    (event.venue, venue) match {
      case (Some(id), Some((elt, p))) if id == elt.id => VenueFull.from(elt, p).map(Some(_))
      case (Some(id), Some((elt, _))) => Validated.invalidNec(s"Venue id mismatch: expect ${id.value} but got ${elt.id.value} (${elt.address.value})")
      case (None, None) => Validated.Valid(None)
      case (Some(id), None) => Validated.invalidNec(s"Venue not found but expect ${id.value}")
      case (None, Some((elt, _))) => Validated.invalidNec(s"Unknown Venue found: not expected but got ${elt.id.value} (${elt.address.value})")
    }
  }

  private def validTalks(event: Event, talks: Seq[Proposal], speakers: Seq[User]): ValidatedNec[String, Seq[ProposalWithSpeakers]] = {
    event.talks.foldLeft(Validated.Valid(Seq.empty[ProposalWithSpeakers]): ValidatedNec[String, Seq[ProposalWithSpeakers]]) { (acc, id) =>
      (acc, validTalk(id, talks, speakers)) match {
        case (Invalid(accErr), Invalid(err)) => Validated.invalid(accErr ++ err)
        case (Invalid(accErr), _) => Validated.invalid(accErr)
        case (_, Invalid(err)) => Validated.invalid(err)
        case (Valid(res), Valid(elt)) => Validated.Valid(res :+ elt)
      }
    }
  }

  private def validTalk(id: Proposal.Id, talks: Seq[Proposal], speakers: Seq[User]): ValidatedNec[String, ProposalWithSpeakers] =
    talks.find(_.id == id).map(ProposalWithSpeakers.from(_, speakers)).getOrElse(Validated.invalidNec(s"Talk not found but expect ${id.value}"))
}
