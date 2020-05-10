package gospeak.web.utils

import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.services.meetup.domain.MeetupGroup
import gospeak.libs.scala.domain.{CustomException, EmailAddress}
import play.api.mvc.PathBindable

object PathBindables {
  implicit def optionStringPathBinder(implicit s: PathBindable[String]): PathBindable[Option[String]] = new PathBindable[Option[String]] {
    override def bind(key: String, value: String): Either[String, Option[String]] = Right(Some(value))

    override def unbind(key: String, value: Option[String]): String = value.getOrElse("")
  }

  implicit def emailPathBinder(implicit s: PathBindable[String]): PathBindable[EmailAddress] =
    stringBindable(EmailAddress.from, _.value)

  implicit def userRequestIdPathBinder(implicit s: PathBindable[String]): PathBindable[UserRequest.Id] =
    stringBindable(UserRequest.Id.from, _.value)

  implicit def groupSlugPathBinder(implicit s: PathBindable[String]): PathBindable[Group.Slug] =
    stringBindable(Group.Slug.from, _.value)

  implicit def groupActionTriggerPathBinder(implicit s: PathBindable[String]): PathBindable[Group.Settings.Action.Trigger] =
    stringBindable(Group.Settings.Action.Trigger.from, _.value)

  implicit def eventSlugPathBinder(implicit s: PathBindable[String]): PathBindable[Event.Slug] =
    stringBindable(Event.Slug.from, _.value)

  implicit def eventRsvpAnswerPathBinder(implicit s: PathBindable[String]): PathBindable[Event.Rsvp.Answer] =
    stringBindable(Event.Rsvp.Answer.from, _.value)

  implicit def talkSlugPathBinder(implicit s: PathBindable[String]): PathBindable[Talk.Slug] =
    stringBindable(Talk.Slug.from, _.value)

  implicit def talkStatusPathBinder(implicit s: PathBindable[String]): PathBindable[Talk.Status] =
    stringBindable(Talk.Status.from, _.value)

  implicit def cfpSlugPathBinder(implicit s: PathBindable[String]): PathBindable[Cfp.Slug] =
    stringBindable(Cfp.Slug.from, _.value)

  implicit def proposalIdPathBinder(implicit s: PathBindable[String]): PathBindable[Proposal.Id] =
    stringBindable(Proposal.Id.from, _.value)

  implicit def proposalRatingGradePathBinder(implicit i: PathBindable[Int]): PathBindable[Proposal.Rating.Grade] =
    intBindable(Proposal.Rating.Grade.from, _.value)

  implicit def userSlugPathBinder(implicit s: PathBindable[String]): PathBindable[User.Slug] =
    stringBindable(User.Slug.from, _.value)

  implicit def userStatusPathBinder(implicit s: PathBindable[String]): PathBindable[User.Status] =
    stringBindable(User.Status.from, _.value)

  implicit def partnerIdPathBinder(implicit s: PathBindable[String]): PathBindable[Partner.Id] =
    stringBindable(Partner.Id.from, _.value)

  implicit def partnerSlugPathBinder(implicit s: PathBindable[String]): PathBindable[Partner.Slug] =
    stringBindable(Partner.Slug.from, _.value)

  implicit def venueIdPathBinder(implicit s: PathBindable[String]): PathBindable[Venue.Id] =
    stringBindable(Venue.Id.from, _.value)

  implicit def contactIdPathBinder(implicit s: PathBindable[String]): PathBindable[Contact.Id] =
    stringBindable(Contact.Id.from, _.value)

  implicit def messageRefPathBinder(implicit s: PathBindable[String]): PathBindable[Message.Ref] =
    stringBindable(Message.Ref.from, _.value)

  implicit def sponsorPackSlugPathBinder(implicit s: PathBindable[String]): PathBindable[SponsorPack.Slug] =
    stringBindable(SponsorPack.Slug.from, _.value)

  implicit def sponsorIdPathBinder(implicit s: PathBindable[String]): PathBindable[Sponsor.Id] =
    stringBindable(Sponsor.Id.from, _.value)

  implicit def meetupGroupUrlNamePathBinder(implicit s: PathBindable[String]): PathBindable[MeetupGroup.Slug] =
    stringBindable(MeetupGroup.Slug.from, _.value)

  implicit def externalEventPathBinder(implicit s: PathBindable[String]): PathBindable[ExternalEvent.Id] =
    stringBindable(ExternalEvent.Id.from, _.value)

  implicit def externalCfpPathBinder(implicit s: PathBindable[String]): PathBindable[ExternalCfp.Id] =
    stringBindable(ExternalCfp.Id.from, _.value)

  implicit def externalProposalPathBinder(implicit s: PathBindable[String]): PathBindable[ExternalProposal.Id] =
    stringBindable(ExternalProposal.Id.from, _.value)

  private def stringBindable[A](from: String => Either[CustomException, A], to: A => String)(implicit s: PathBindable[String]): PathBindable[A] =
    new PathBindable[A] {
      override def bind(key: String, value: String): Either[String, A] = s.bind(key, value).flatMap(from(_).left.map(_.getMessage))

      override def unbind(key: String, value: A): String = to(value)
    }

  private def intBindable[A](from: Int => Either[CustomException, A], to: A => Int)(implicit i: PathBindable[Int]): PathBindable[A] =
    new PathBindable[A] {
      override def bind(key: String, value: String): Either[String, A] = i.bind(key, value).flatMap(from(_).left.map(_.getMessage))

      override def unbind(key: String, value: A): String = to(value).toString
    }
}
