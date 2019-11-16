package fr.gospeak.core.domain

import java.time.Instant

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

    case object Declined extends Status {
      def description = "Remove this proposal from the pending ones"
    }

    val all: Seq[Status] = Seq(Pending, Accepted, Declined)
  }

  final case class Full(proposal: Proposal, cfp: Cfp, group: Group, talk: Talk, event: Option[Event]) {
    def id: Id = proposal.id

    def status: Status = proposal.status

    def title: Talk.Title = proposal.title

    def description: Markdown = proposal.description

    def duration: FiniteDuration = proposal.duration

    def speakers: NonEmptyList[User.Id] = proposal.speakers

    def slides: Option[Slides] = proposal.slides

    def video: Option[Video] = proposal.video

    def tags: Seq[Tag] = proposal.tags

    def info: Info = proposal.info

    def data: Data = proposal.data

    def users: Seq[User.Id] = proposal.users
  }

  final case class Vote(proposal: Proposal.Id,
                        user: User.Id,
                        rating: Vote.Rating,
                        voted: Instant)

  object Vote {

    sealed trait Rating extends Product with Serializable {
      def value: Int
    }

    object Rating {

      case object Like extends Rating {
        val value = 1
      }

      case object Dislike extends Rating {
        val value = 0
      }

      val all: Seq[Rating] = Seq(Like, Dislike)

      def from(int: Int): Either[CustomException, Rating] =
        all.find(_.value == int).map(Right(_)).getOrElse(Left(CustomException(s"'$int' is not a valid rating for Proposal.Vote.Rating")))

    }

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
