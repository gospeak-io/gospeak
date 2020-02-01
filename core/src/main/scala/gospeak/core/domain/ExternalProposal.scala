package gospeak.core.domain

import cats.data.NonEmptyList
import gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain._

import scala.concurrent.duration.FiniteDuration

final case class ExternalProposal(id: ExternalProposal.Id,
                                  talk: Talk.Id,
                                  event: ExternalEvent.Id,
                                  title: ExternalProposal.Title,
                                  duration: FiniteDuration,
                                  description: Markdown,
                                  speakers: NonEmptyList[User.Id],
                                  slides: Option[Slides],
                                  video: Option[Video],
                                  tags: Seq[Tag],
                                  info: Info)

object ExternalProposal {

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("ExternalProposal.Id", new Id(_))

  final case class Title(value: String) extends AnyVal

  final case class Data(title: ExternalProposal.Title,
                        duration: FiniteDuration,
                        description: Markdown,
                        slides: Option[Slides],
                        video: Option[Video],
                        tags: Seq[Tag])

  object Data {
    def apply(p: ExternalProposal): Data = new Data(p.title, p.duration, p.description, p.slides, p.video, p.tags)
  }

}
