package gospeak.core.domain

import cats.data.NonEmptyList
import gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain._

import scala.concurrent.duration.FiniteDuration

final case class ExternalProposal(id: ExternalProposal.Id,
                                  talk: Talk.Id,
                                  event: ExternalEvent.Id,
                                  status: Proposal.Status,
                                  title: Talk.Title,
                                  duration: FiniteDuration,
                                  description: Markdown,
                                  message: Markdown,
                                  speakers: NonEmptyList[User.Id],
                                  slides: Option[Slides],
                                  video: Option[Video],
                                  url: Option[Url],
                                  tags: Seq[Tag],
                                  info: Info) {
  def data: ExternalProposal.Data = ExternalProposal.Data(this)

  def hasSpeaker(user: User.Id): Boolean = speakers.toList.contains(user)

  def users: List[User.Id] = (speakers.toList ++ info.users).distinct
}

object ExternalProposal {
  def apply(d: Data, talk: Talk.Id, event: ExternalEvent.Id, speakers: NonEmptyList[User.Id], info: Info): ExternalProposal =
    new ExternalProposal(Id.generate(), talk, event, d.status, d.title, d.duration, d.description, d.message, speakers, d.slides, d.video, d.url, d.tags, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("ExternalProposal.Id", new Id(_))

  final case class Full(proposal: ExternalProposal, talk: Talk, event: ExternalEvent) {
    def id: Id = proposal.id

    def status: Proposal.Status = proposal.status

    def title: Talk.Title = proposal.title

    def duration: FiniteDuration = proposal.duration

    def description: Markdown = proposal.description

    def message: Markdown = proposal.message

    def speakers: NonEmptyList[User.Id] = proposal.speakers

    def slides: Option[Slides] = proposal.slides

    def video: Option[Video] = proposal.video

    def url: Option[Url] = proposal.url

    def tags: Seq[Tag] = proposal.tags

    def info: Info = proposal.info

    def hasSpeaker(user: User.Id): Boolean = proposal.hasSpeaker(user)

    def users: List[User.Id] = (proposal.users ++ talk.users ++ event.users).distinct
  }

  final case class Data(status: Proposal.Status,
                        title: Talk.Title,
                        duration: FiniteDuration,
                        description: Markdown,
                        message: Markdown,
                        slides: Option[Slides],
                        video: Option[Video],
                        url: Option[Url],
                        tags: Seq[Tag])

  object Data {
    def apply(p: ExternalProposal): Data = new Data(p.status, p.title, p.duration, p.description, p.message, p.slides, p.video, p.url, p.tags)

    def apply(t: Talk): Data = new Data(Proposal.Status.Pending, t.title, t.duration, t.description, t.message, t.slides, t.video, None, t.tags)
  }

}
