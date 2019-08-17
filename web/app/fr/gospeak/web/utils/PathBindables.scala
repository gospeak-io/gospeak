package fr.gospeak.web.utils

import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.meetup.domain.MeetupGroup
import fr.gospeak.libs.scalautils.domain.CustomException
import play.api.mvc.PathBindable

object PathBindables {
  implicit def optionStringPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Option[String]] = new PathBindable[Option[String]] {
    override def bind(key: String, value: String): Either[String, Option[String]] =
      Right(Some(value))

    override def unbind(key: String, value: Option[String]): String =
      value.getOrElse("")
  }

  implicit def groupSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Group.Slug] =
    stringEitherPathBindable[Group.Slug](Group.Slug.from, _.value)

  implicit def eventSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Event.Slug] =
    stringEitherPathBindable[Event.Slug](Event.Slug.from, _.value)

  implicit def talkSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Talk.Slug] =
    stringEitherPathBindable[Talk.Slug](Talk.Slug.from, _.value)

  implicit def talkStatusPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Talk.Status] =
    stringEitherPathBindable[Talk.Status](Talk.Status.from, _.toString)

  implicit def cfpSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Cfp.Slug] =
    stringEitherPathBindable[Cfp.Slug](Cfp.Slug.from, _.value)

  implicit def proposalIdPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Proposal.Id] =
    stringEitherPathBindable[Proposal.Id](Proposal.Id.from, _.value)

  implicit def userSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[User.Slug] =
    stringEitherPathBindable[User.Slug](User.Slug.from, _.value)

  implicit def partnerSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Partner.Slug] =
    stringEitherPathBindable[Partner.Slug](Partner.Slug.from, _.value)

  implicit def venueIdPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Venue.Id] =
    stringEitherPathBindable[Venue.Id](Venue.Id.from, _.value)

  implicit def contactIdPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Contact.Id] =
    stringEitherPathBindable[Contact.Id](Contact.Id.from, _.value)

  implicit def templateDataRefPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[TemplateData.Ref] =
    stringEitherPathBindable[TemplateData.Ref](TemplateData.Ref.from, _.value)

  implicit def sponsorPackSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[SponsorPack.Slug] =
    stringEitherPathBindable[SponsorPack.Slug](SponsorPack.Slug.from, _.value)

  implicit def sponsorIdPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Sponsor.Id] =
    stringEitherPathBindable[Sponsor.Id](Sponsor.Id.from, _.value)

  implicit def meetupGroupUrlNamePathBinder(implicit stringBinder: PathBindable[String]): PathBindable[MeetupGroup.Slug] =
    stringEitherPathBindable[MeetupGroup.Slug](MeetupGroup.Slug.from, _.value)

  private def stringEitherPathBindable[A](from: String => Either[CustomException, A], to: A => String)(implicit pb: PathBindable[String]): PathBindable[A] =
    new PathBindable[A] {
      override def bind(key: String, value: String): Either[String, A] = pb.bind(key, value).flatMap(from(_).left.map(_.getMessage))

      override def unbind(key: String, value: A): String = to(value)
    }
}
