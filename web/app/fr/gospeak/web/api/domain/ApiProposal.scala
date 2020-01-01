package fr.gospeak.web.api.domain

import fr.gospeak.core.domain.utils.BasicCtx
import fr.gospeak.core.domain.{Proposal, User, Venue}
import play.api.libs.json.{Json, Writes}
import fr.gospeak.web.api.domain.utils.JsonFormats._

import scala.concurrent.duration._

object ApiProposal {

  // data to display publicly
  final case class Published(id: String,
                             title: String,
                             description: String,
                             duration: FiniteDuration,
                             slides: Option[String],
                             video: Option[String],
                             speakers: Seq[ApiUser.Embed],
                             tags: Seq[String],
                             event: Option[ApiEvent.Embed],
                             venue: Option[ApiVenue.Embed])

  object Published {
    implicit val writes: Writes[Published] = Json.writes[Published]
  }

  def published(p: Proposal.Full, users: Seq[User], venues: Seq[Venue.Full])(implicit ctx: BasicCtx): Published =
    new Published(
      id = p.id.value,
      title = p.title.value,
      description = p.description.value,
      duration = p.duration,
      slides = p.slides.map(_.value),
      video = p.video.map(_.value),
      speakers = p.speakers.toList.map(ApiUser.embed(_, users)),
      tags = p.tags.map(_.value),
      event = p.event.map(ApiEvent.embed),
      venue = p.event.flatMap(_.venue).map(ApiVenue.embed(_, venues)))

  // embedded data in other models, should be public
  final case class Embed(id: String,
                         title: String,
                         description: String,
                         duration: FiniteDuration,
                         slides: Option[String],
                         video: Option[String],
                         speakers: Seq[ApiUser.Embed],
                         tags: Seq[String])

  object Embed {
    implicit val writes: Writes[Embed] = Json.writes[Embed]
  }

  def embed(id: Proposal.Id, proposals: Seq[Proposal], users: Seq[User])(implicit ctx: BasicCtx): Embed =
    proposals.find(_.id == id).map(embed(_, users)).getOrElse(unknown(id))

  def embed(p: Proposal, users: Seq[User])(implicit ctx: BasicCtx): Embed =
    new Embed(
      id = p.id.value,
      title = p.title.value,
      description = p.description.value,
      duration = p.duration,
      slides = p.slides.map(_.value),
      video = p.video.map(_.value),
      speakers = p.speakers.toList.map(ApiUser.embed(_, users)),
      tags = p.tags.map(_.value))

  def unknown(id: Proposal.Id)(implicit ctx: BasicCtx): Embed =
    new Embed(
      id = id.value,
      title = "Unknown talk",
      description = "",
      duration = 10.minutes,
      slides = None,
      video = None,
      speakers = Seq(),
      tags = Seq())

}
