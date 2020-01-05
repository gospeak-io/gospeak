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
                          orgaTags: Seq[Tag],
                          info: Info) {
  def data: Proposal.Data = Proposal.Data(this)

  def dataOrga: Proposal.DataOrga = Proposal.DataOrga(this)

  def users: Seq[User.Id] = speakers.toList ++ info.users
}

object Proposal {
  def apply(talk: Talk.Id,
            cfp: Cfp.Id,
            event: Option[Event.Id],
            data: Data,
            status: Status,
            speakers: NonEmptyList[User.Id],
            info: Info): Proposal =
    new Proposal(Id.generate(), talk, cfp, event, status, data.title, data.duration, data.description, speakers, data.slides, data.video, data.tags, Seq(), info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Proposal.Id", new Id(_))

  sealed trait Status extends StringEnum {
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

  final case class Full(proposal: Proposal, cfp: Cfp, group: Group, talk: Talk, event: Option[Event], venue: Option[Venue.Full], score: Long, likes: Long, dislikes: Long, userGrade: Option[Rating.Grade]) {
    def id: Id = proposal.id

    def status: Status = proposal.status

    def title: Talk.Title = proposal.title

    def description: Markdown = proposal.description

    def duration: FiniteDuration = proposal.duration

    def speakers: NonEmptyList[User.Id] = proposal.speakers

    def slides: Option[Slides] = proposal.slides

    def video: Option[Video] = proposal.video

    def tags: Seq[Tag] = proposal.tags

    def orgaTags: Seq[Tag] = proposal.orgaTags

    def info: Info = proposal.info

    def data: Data = proposal.data

    def users: Seq[User.Id] = proposal.users
  }

  final case class Rating(proposal: Id,
                          grade: Rating.Grade,
                          createdAt: Instant,
                          createdBy: User.Id)

  object Rating {

    sealed abstract class Grade(val value: Int) extends Product with Serializable

    object Grade {

      case object Like extends Grade(1)

      case object Dislike extends Grade(-1)

      val all: Seq[Grade] = Seq(Like, Dislike)

      def from(value: Int): Either[CustomException, Grade] =
        all.find(_.value == value).map(Right(_)).getOrElse(Left(CustomException(s"'$value' is not a valid Proposal.Rating.Grade")))

      implicit val ordering: Ordering[Grade] = (x: Grade, y: Grade) => x.value - y.value
    }

    final case class Full(rating: Rating, user: User, proposal: Proposal) {
      def grade: Grade = rating.grade

      def createdAt: Instant = rating.createdAt

      def createdBy: User.Id = rating.createdBy

      def users: Seq[User.Id] = proposal.users
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

  final case class DataOrga(title: Talk.Title,
                            duration: FiniteDuration,
                            description: Markdown,
                            slides: Option[Slides],
                            video: Option[Video],
                            tags: Seq[Tag],
                            orgaTags: Seq[Tag])

  object DataOrga {
    def apply(proposal: Proposal): DataOrga = DataOrga(proposal.title, proposal.duration, proposal.description, proposal.slides, proposal.video, proposal.tags, proposal.orgaTags)
  }

}
