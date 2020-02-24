package gospeak.web.api.domain

import java.time.{Instant, LocalDateTime}

import gospeak.core.domain.utils.{BasicCtx, OrgaCtx}
import gospeak.core.domain.{CommonEvent, Event, Proposal, User}
import gospeak.web.api.domain.utils.{ApiInfo, ApiPlace}
import play.api.libs.json.{Json, Writes}

object ApiEvent {

  // data to display for orgas (everything)
  final case class Orga(slug: String,
                        name: String,
                        start: LocalDateTime,
                        published: Option[Instant],
                        cfp: Option[ApiCfp.Embed],
                        venue: Option[ApiVenue.Embed],
                        proposals: Seq[ApiProposal.Embed],
                        maxAttendee: Option[Int],
                        allowRsvp: Boolean,
                        descriptionTmpl: String,
                        orgaNotes: Notes,
                        tags: Seq[String],
                        refs: Refs,
                        info: ApiInfo)

  object Orga {
    implicit val writes: Writes[Orga] = Json.writes[Orga]
  }

  def orga(e: Event.Full, proposals: Seq[Proposal], users: Seq[User])(implicit ctx: OrgaCtx): Orga =
    new Orga(
      slug = e.slug.value,
      name = e.name.value,
      start = e.start,
      published = e.published,
      cfp = e.cfp.map(ApiCfp.embed),
      venue = e.venue.map(ApiVenue.embed),
      proposals = e.talks.map(ApiProposal.embed(_, proposals, users)),
      maxAttendee = e.maxAttendee,
      allowRsvp = e.allowRsvp,
      descriptionTmpl = e.event.description.value,
      orgaNotes = Notes.from(e.orgaNotes, users),
      tags = e.tags.map(_.value),
      refs = Refs.from(e.refs),
      info = ApiInfo.from(e.event.info, users))

  // data to display publicly
  final case class Published(slug: String,
                             name: String,
                             start: LocalDateTime,
                             // FIXME add rendered description
                             venue: Option[ApiVenue.Embed],
                             proposals: Seq[ApiProposal.Embed],
                             tags: Seq[String],
                             meetup: Option[String])

  object Published {
    implicit val writes: Writes[Published] = Json.writes[Published]
  }

  def published(e: Event.Full, proposals: Seq[Proposal], users: Seq[User])(implicit ctx: BasicCtx): Published =
    new Published(
      slug = e.slug.value,
      name = e.name.value,
      start = e.start,
      venue = e.venue.map(ApiVenue.embed),
      proposals = e.talks.map(ApiProposal.embed(_, proposals, users)),
      tags = e.event.tags.map(_.value),
      meetup = e.refs.meetup.map(_.link))

  final case class Common(kind: String,
                          id: String,
                          name: String,
                          format: String,
                          start: Option[LocalDateTime],
                          location: Option[ApiPlace],
                          tags: Seq[String])

  object Common {
    implicit val writes: Writes[Common] = Json.writes[Common]
  }

  def common(e: CommonEvent)(implicit ctx: BasicCtx): Common =
    new Common(
      kind = if (e.internal.isDefined) "internal" else "external",
      id = e.id.value,
      name = e.name.value,
      format = e.kind.value,
      start = e.start,
      location = e.location.map(ApiPlace.from),
      tags = e.tags.map(_.value))

  // embedded data in other models, should be public
  final case class Embed(slug: String,
                         name: String,
                         start: LocalDateTime,
                         meetup: Option[String])

  object Embed {
    implicit val writes: Writes[Embed] = Json.writes[Embed]
  }

  def embed(e: Event)(implicit ctx: BasicCtx): Embed =
    new Embed(
      slug = e.slug.value,
      name = e.name.value,
      start = e.start,
      meetup = e.refs.meetup.map(_.link))

  /*
    Nested classes
   */

  final case class Notes(text: String,
                         updatedAt: Instant,
                         updatedBy: ApiUser.Embed)

  object Notes {
    def from(n: Event.Notes, users: Seq[User])(implicit ctx: OrgaCtx): Notes =
      new Notes(
        text = n.text,
        updatedAt = n.updatedAt,
        updatedBy = ApiUser.embed(n.updatedBy, users))

    implicit val writes: Writes[Notes] = Json.writes[Notes]
  }

  final case class Refs(meetup: Option[Refs.Meetup])

  object Refs {

    sealed trait Ref {
      val link: String
    }

    final case class Meetup(link: String, group: String, id: Long) extends Ref

    def from(e: Event.ExtRefs): Refs =
      new Refs(
        meetup = e.meetup.map(m => Meetup(m.link, m.group.value, m.event.value)))

    implicit val writesMeetup: Writes[Refs.Meetup] = Json.writes[Refs.Meetup]
    implicit val writes: Writes[Refs] = Json.writes[Refs]
  }

}
