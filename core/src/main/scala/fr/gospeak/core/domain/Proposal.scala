package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain._

import scala.concurrent.duration.FiniteDuration

final case class Proposal(id: Proposal.Id,
                          talk: Talk.Id,
                          cfp: Cfp.Id,
                          event: Option[Event.Id],
                          status: Proposal.Status,
                          title: Talk.Title,
                          duration: FiniteDuration,
                          description: Markdown,
                          speakers: NonEmptyList[User.Id],
                          slides: Option[Slides],
                          video: Option[Video],
                          tags: Seq[Tag],
                          info: Info) {
  def data: Proposal.Data = Proposal.Data(this)

  def users: Seq[User.Id] = (info.users ++ speakers.toList).distinct
}

object Proposal {
  def apply(talk: Talk.Id,
            cfp: Cfp.Id,
            event: Option[Event.Id],
            data: Data,
            status: Status,
            speakers: NonEmptyList[User.Id],
            info: Info): Proposal =
    new Proposal(Id.generate(), talk, cfp, event, status, data.title, data.duration, data.description, speakers, data.slides, data.video, data.tags, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Proposal.Id", new Id(_))

  sealed trait Status extends StringEnum with Product with Serializable {
    def value: String = toString
  }

  object Status extends EnumBuilder[Status]("Proposal.Status") {

    case object Pending extends Status

    case object Accepted extends Status // TODO: rename this to Planified ?

    case object Rejected extends Status {
      def description = "Remove this proposal from the pending ones"
    }

    val all: Seq[Status] = Seq(Pending, Accepted, Rejected)
  }

  final case class Data(title: Talk.Title,
                        duration: FiniteDuration,
                        description: Markdown,
                        slides: Option[Slides],
                        video: Option[Video],
                        tags: Seq[Tag])

  object Data {
    def apply(talk: Talk): Data = Data(talk.title, talk.duration, talk.description, talk.slides, talk.video, talk.tags)

    def apply(talk: Talk.Data): Proposal.Data = Proposal.Data(talk.title, talk.duration, talk.description, talk.slides, talk.video, talk.tags)

    def apply(proposal: Proposal): Data = Data(proposal.title, proposal.duration, proposal.description, proposal.slides, proposal.video, proposal.tags)
  }

}
