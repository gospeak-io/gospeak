package fr.gospeak.migration.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.{Cfp => NewCfp, User, Proposal => NewProposal, Talk => NewTalk}
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
  def toTalk: NewTalk = {
    Try(NewTalk(
      id = NewTalk.Id.from(id).get,
      slug = NewTalk.Slug.from(StringUtils.slugify(data.title)).get,
      title = NewTalk.Title(data.title),
      duration = 10.minutes,
      status = NewTalk.Status.Draft,
      description = Markdown(data.description.getOrElse("")),
      speakers = NonEmptyList.fromListUnsafe(data.speakers.map(User.Id.from(_).get)),
      slides = data.slides.map(Slides.from(_).get),
      video = data.video.map(Video.from(_).get),
      tags = Seq(),
      info = meta.toInfo)).mapFailure(e => new Exception(s"toTalk error for $this", e)).get
  }
  def toProposal(cfp: NewCfp.Id): NewProposal = {
    Try(NewProposal(
      id = NewProposal.Id.generate(),
      talk = NewTalk.Id.from(id).get,
      cfp = cfp,
      event = None, // should be set later
      title = NewTalk.Title(data.title),
      duration = 10.minutes,
      status = status match {
        case "Proposed" => NewProposal.Status.Pending
        case "Accepted" => NewProposal.Status.Pending
        case "Planified" => NewProposal.Status.Accepted
        case "Finalized" => NewProposal.Status.Accepted
        case "Rejected" => NewProposal.Status.Rejected
      },
      description = Markdown(data.description.getOrElse("")),
      speakers = NonEmptyList.fromListUnsafe(data.speakers.map(User.Id.from(_).get)),
      slides = data.slides.map(Slides.from(_).get),
      video = data.video.map(Video.from(_).get),
      tags = Seq(),
      info = meta.toInfo)).mapFailure(e => new Exception(s"toProposal error for $this", e)).get
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
