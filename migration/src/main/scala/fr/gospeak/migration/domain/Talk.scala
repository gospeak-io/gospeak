package fr.gospeak.migration.domain

import cats.data.NonEmptyList
import fr.gospeak.core.{domain => gs}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain.{Markdown, Slides, Video}
import fr.gospeak.migration.domain.utils.Meta

import scala.concurrent.duration._
import scala.util.Try

case class Talk(id: String, // Talk.Id
                status: String, // Talk.Status.Value, (Proposed, Accepted, Planified, Finalized, Rejected)
                data: TalkData,
                meta: Meta) {
  def toTalk: gs.Talk = {
    Try(gs.Talk(
      id = gs.Talk.Id.from(id).get,
      slug = gs.Talk.Slug.from(StringUtils.slugify(data.title)).get,
      title = gs.Talk.Title(data.title),
      duration = 10.minutes,
      status = gs.Talk.Status.Draft,
      description = Markdown(data.description.getOrElse("")),
      speakers = NonEmptyList.fromListUnsafe(data.speakers.map(gs.User.Id.from(_).get)),
      slides = data.slides.map(Slides.from(_).get),
      video = data.video.map(Video.from(_).get),
      tags = Seq(),
      info = meta.toInfo)).mapFailure(e => new Exception(s"toTalk error for $this ", e)).get
  }

  def toProposal(cfp: gs.Cfp.Id): gs.Proposal = {
    Try(gs.Proposal(
      id = gs.Proposal.Id.generate(),
      talk = gs.Talk.Id.from(id).get,
      cfp = cfp,
      event = None, // should be set later
      title = gs.Talk.Title(data.title),
      duration = 10.minutes,
      status = status match {
        case "Proposed" => gs.Proposal.Status.Pending
        case "Accepted" => gs.Proposal.Status.Pending
        case "Planified" => gs.Proposal.Status.Accepted
        case "Finalized" => gs.Proposal.Status.Accepted
        case "Rejected" => gs.Proposal.Status.Rejected
      },
      description = Markdown(data.description.getOrElse("")),
      speakers = NonEmptyList.fromListUnsafe(data.speakers.map(gs.User.Id.from(_).get)),
      slides = data.slides.map(Slides.from(_).get),
      video = data.video.map(Video.from(_).get),
      tags = Seq(),
      info = meta.toInfo)).mapFailure(e => new Exception(s"toProposal error for $this ", e)).get
  }
}

case class TalkData(title: String,
                    description: Option[String],
                    speakers: List[String], // List[Person.Id],
                    slides: Option[String],
                    slidesEmbedCode: Option[String],
                    video: Option[String],
                    videoEmbedCode: Option[String],
                    proposal: Option[Proposal])

case class Proposal(availabilities: List[String]) // List[LocalDate]
