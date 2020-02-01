package gospeak.core.domain

import cats.data.NonEmptyList
import gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain._

import scala.concurrent.duration.FiniteDuration

final case class ExternalProposal(id: ExternalProposal.Id,
                                  talk: Talk.Id,
                                  event: ExternalEvent.Id,
                                  title: Talk.Title,
                                  duration: FiniteDuration,
                                  description: Markdown,
                                  speakers: NonEmptyList[User.Id],
                                  slides: Option[Slides],
                                  video: Option[Video],
                                  tags: Seq[Tag],
                                  info: Info) {
  def data: ExternalProposal.Data = ExternalProposal.Data(this)
}

object ExternalProposal {
  def apply(d: Data, talk: Talk.Id, event: ExternalEvent.Id, speakers: NonEmptyList[User.Id], info: Info): ExternalProposal =
    new ExternalProposal(Id.generate(), talk, event, d.title, d.duration, d.description, speakers, d.slides, d.video, d.tags, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("ExternalProposal.Id", new Id(_))

  final case class Data(title: Talk.Title,
                        duration: FiniteDuration,
                        description: Markdown,
                        slides: Option[Slides],
                        video: Option[Video],
                        tags: Seq[Tag])

  object Data {
    def apply(p: ExternalProposal): Data = new Data(p.title, p.duration, p.description, p.slides, p.video, p.tags)

    def apply(p: Talk): Data = new Data(p.title, p.duration, p.description, p.slides, p.video, p.tags)
  }

}
