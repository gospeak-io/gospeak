package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain._

import scala.concurrent.duration.FiniteDuration

final case class Proposal(id: Proposal.Id,
                          talk: Talk.Id,
                          cfp: Cfp.Id,
                          event: Option[Event.Id],
                          title: Talk.Title,
                          duration: FiniteDuration,
                          status: Proposal.Status,
                          description: Markdown,
                          speakers: NonEmptyList[User.Id],
                          slides: Option[Slides],
                          video: Option[Video],
                          info: Info) {
  def data: Proposal.Data = Proposal.Data(this)

  def users: Seq[User.Id] = (info.createdBy :: info.updatedBy :: speakers.toList).distinct
}

object Proposal {
  def apply(talk: Talk.Id,
            cfp: Cfp.Id,
            event: Option[Event.Id],
            data: Data,
            status: Status,
            speakers: NonEmptyList[User.Id],
            info: Info): Proposal =
    new Proposal(Id.generate(), talk, cfp, event, data.title, data.duration, status, data.description, speakers, data.slides, data.video, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Proposal.Id", new Id(_))

  sealed trait Status extends Product with Serializable

  object Status extends EnumBuilder[Status]("Proposal.Status") {

    case object Pending extends Status

    case object Accepted extends Status // TODO: rename this to Planified ?

    case object Rejected extends Status

    val all: Seq[Status] = Seq(Pending, Accepted, Rejected)
  }

  final case class Data(title: Talk.Title,
                        duration: FiniteDuration,
                        description: Markdown,
                        slides: Option[Slides],
                        video: Option[Video])

  object Data {
    def apply(talk: Talk): Data = Data(talk.title, talk.duration, talk.description, talk.slides, talk.video)

    def apply(proposal: Proposal): Data = Data(proposal.title, proposal.duration, proposal.description, proposal.slides, proposal.video)
  }

}
