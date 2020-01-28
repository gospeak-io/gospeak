package gospeak.web.api.domain

import java.time.Instant

import gospeak.core.domain.utils.{BasicCtx, OrgaCtx}
import gospeak.core.domain.{Proposal, User}
import gospeak.web.api.domain.utils.ApiInfo
import play.api.libs.json.{Json, Writes}
import gospeak.web.api.domain.utils.JsonFormats._

import scala.concurrent.duration._

object ApiProposal {

  // data to display for orgas (everything)
  final case class Orga(id: String,
                        status: String,
                        title: String,
                        description: String,
                        duration: FiniteDuration,
                        slides: Option[String],
                        video: Option[String],
                        speakers: Seq[ApiUser.Embed],
                        tags: Seq[String],
                        orgaTags: Seq[String],
                        cfp: ApiCfp.Embed,
                        event: Option[ApiEvent.Embed],
                        venue: Option[ApiVenue.Embed],
                        score: Long,
                        likes: Long,
                        dislikes: Long,
                        userGrade: Option[Int],
                        info: ApiInfo)

  object Orga {
    implicit val writes: Writes[Orga] = Json.writes[Orga]

    final case class Rating(proposal: Embed,
                            user: ApiUser.Embed,
                            grade: Int,
                            updatedAt: Instant)

    object Rating {
      implicit val writes: Writes[Rating] = Json.writes[Rating]

      def from(r: Proposal.Rating.Full, users: Seq[User])(implicit ctx: OrgaCtx): Rating =
        new Rating(
          proposal = embed(r.proposal, users),
          user = ApiUser.embed(r.user),
          grade = r.rating.grade.value,
          updatedAt = r.rating.createdAt)
    }

  }

  def orga(p: Proposal.Full, users: Seq[User])(implicit ctx: OrgaCtx): Orga =
    new Orga(
      id = p.id.value,
      status = p.status.value,
      title = p.title.value,
      description = p.description.value,
      duration = p.duration,
      slides = p.slides.map(_.value),
      video = p.video.map(_.value),
      speakers = p.speakers.toList.map(ApiUser.embed(_, users)),
      tags = p.tags.map(_.value),
      orgaTags = p.orgaTags.map(_.value),
      cfp = ApiCfp.embed(p.cfp),
      event = p.event.map(ApiEvent.embed),
      venue = p.venue.map(ApiVenue.embed),
      score = p.score,
      likes = p.likes,
      dislikes = p.dislikes,
      userGrade = p.userGrade.map(_.value),
      info = ApiInfo.from(p.info, users))

  // data to display publicly
  final case class Published(id: String,
                             title: String,
                             description: String,
                             duration: FiniteDuration,
                             slides: Option[String],
                             video: Option[String],
                             speakers: Seq[ApiUser.Embed],
                             tags: Seq[String],
                             group: ApiGroup.Embed,
                             cfp: ApiCfp.Embed,
                             event: Option[ApiEvent.Embed],
                             venue: Option[ApiVenue.Embed])

  object Published {
    implicit val writes: Writes[Published] = Json.writes[Published]
  }

  def published(p: Proposal.Full, users: Seq[User])(implicit ctx: BasicCtx): Published =
    new Published(
      id = p.id.value,
      title = p.title.value,
      description = p.description.value,
      duration = p.duration,
      slides = p.slides.map(_.value),
      video = p.video.map(_.value),
      speakers = p.speakers.toList.map(ApiUser.embed(_, users)),
      tags = p.tags.map(_.value),
      group = ApiGroup.embed(p.group),
      cfp = ApiCfp.embed(p.cfp),
      event = p.event.map(ApiEvent.embed),
      venue = p.venue.map(ApiVenue.embed))

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
