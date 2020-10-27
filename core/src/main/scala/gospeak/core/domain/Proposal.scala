package gospeak.core.domain

import java.time.Instant

import cats.data.NonEmptyList
import gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain._

import scala.concurrent.duration.FiniteDuration

final case class Proposal(id: Proposal.Id,
                          talk: Talk.Id,
                          cfp: Cfp.Id,
                          event: Option[Event.Id],
                          status: Proposal.Status,
                          title: Talk.Title,
                          duration: FiniteDuration,
                          description: Markdown,
                          message: Markdown,
                          speakers: NonEmptyList[User.Id],
                          slides: Option[Url.Slides],
                          video: Option[Url.Video],
                          tags: List[Tag],
                          orgaTags: List[Tag],
                          info: Info) {
  def data: Proposal.Data = Proposal.Data(this)

  def dataOrga: Proposal.DataOrga = Proposal.DataOrga(this)

  def hasSpeaker(user: User.Id): Boolean = speakers.toList.contains(user)

  def speakerUsers(users: List[User]): List[User] = speakers.toList.flatMap(id => users.find(_.id == id))

  def users: List[User.Id] = (speakers.toList ++ info.users).distinct
}

object Proposal {
  def apply(talk: Talk.Id,
            cfp: Cfp.Id,
            event: Option[Event.Id],
            d: Data,
            status: Status,
            speakers: NonEmptyList[User.Id],
            info: Info): Proposal =
    new Proposal(Id.generate(), talk, cfp, event, status, d.title, d.duration, d.description, d.message, speakers, d.slides, d.video, d.tags, List(), info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Proposal.Id", new Id(_))

  sealed trait Status extends StringEnum {
    def value: String = toString
  }

  object Status extends EnumBuilder[Status]("Proposal.Status") {

    case object Pending extends Status

    case object Accepted extends Status

    case object Declined extends Status {
      def description = "Remove this proposal from the pending ones"
    }

    val all: List[Status] = List(Pending, Accepted, Declined)
  }

  final case class Full(proposal: Proposal,
                        cfp: Cfp,
                        group: Group,
                        talk: Talk,
                        event: Option[Event],
                        venue: Option[Venue.Full],
                        speakerCommentCount: Long,
                        speakerLastComment: Option[Instant],
                        orgaCommentCount: Long,
                        orgaLastComment: Option[Instant],
                        score: Long,
                        likes: Long,
                        dislikes: Long,
                        userGrade: Option[Rating.Grade]) {
    def id: Id = proposal.id

    def status: Status = proposal.status

    def title: Talk.Title = proposal.title

    def description: Markdown = proposal.description

    def message: Markdown = proposal.message

    def duration: FiniteDuration = proposal.duration

    def speakers: NonEmptyList[User.Id] = proposal.speakers

    def speakerUsers(users: List[User]): List[User] = proposal.speakerUsers(users)

    def slides: Option[Url.Slides] = proposal.slides

    def video: Option[Url.Video] = proposal.video

    def tags: List[Tag] = proposal.tags

    def orgaTags: List[Tag] = proposal.orgaTags

    def info: Info = proposal.info

    def data: Data = proposal.data

    def hasSpeaker(user: User.Id): Boolean = proposal.hasSpeaker(user)

    def hasOrga(user: User.Id): Boolean = group.hasOrga(user)

    def users: List[User.Id] = (proposal.users ++ cfp.users ++ group.users ++ talk.users ++ event.map(_.users).getOrElse(List()) ++ venue.map(_.users).getOrElse(List())).distinct
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

      val all: List[Grade] = List(Like, Dislike)

      def from(value: Int): Either[CustomException, Grade] =
        all.find(_.value == value).map(Right(_)).getOrElse(Left(CustomException(s"'$value' is not a valid Proposal.Rating.Grade")))

      implicit val ordering: Ordering[Grade] = (x: Grade, y: Grade) => x.value - y.value
    }

    final case class Full(rating: Rating, user: User, proposal: Proposal) {
      def grade: Grade = rating.grade

      def createdAt: Instant = rating.createdAt

      def createdBy: User.Id = rating.createdBy

      def users: List[User.Id] = proposal.users
    }

  }

  final case class Data(title: Talk.Title,
                        duration: FiniteDuration,
                        description: Markdown,
                        message: Markdown,
                        slides: Option[Url.Slides],
                        video: Option[Url.Video],
                        tags: List[Tag])

  object Data {
    def apply(t: Talk): Data = Data(t.title, t.duration, t.description, t.message, t.slides, t.video, t.tags)

    def apply(t: Talk.Data): Proposal.Data = Proposal.Data(t.title, t.duration, t.description, t.message, t.slides, t.video, t.tags)

    def apply(p: Proposal): Data = Data(p.title, p.duration, p.description, p.message, p.slides, p.video, p.tags)
  }

  final case class DataOrga(title: Talk.Title,
                            duration: FiniteDuration,
                            description: Markdown,
                            slides: Option[Url.Slides],
                            video: Option[Url.Video],
                            tags: List[Tag],
                            orgaTags: List[Tag])

  object DataOrga {
    def apply(p: Proposal): DataOrga = DataOrga(p.title, p.duration, p.description, p.slides, p.video, p.tags, p.orgaTags)
  }

}
