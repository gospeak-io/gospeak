package fr.gospeak.web.utils

import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.TemplateData
import play.api.mvc.PathBindable

object PathBindables {
  implicit def groupSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Group.Slug] = new PathBindable[Group.Slug] {
    override def bind(key: String, value: String): Either[String, Group.Slug] =
      stringBinder.bind(key, value).flatMap(Group.Slug.from(_).left.map(_.getMessage))

    override def unbind(key: String, slug: Group.Slug): String =
      slug.value
  }

  implicit def eventSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Event.Slug] = new PathBindable[Event.Slug] {
    override def bind(key: String, value: String): Either[String, Event.Slug] =
      stringBinder.bind(key, value).flatMap(Event.Slug.from(_).left.map(_.getMessage))

    override def unbind(key: String, slug: Event.Slug): String =
      slug.value
  }

  implicit def talkSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Talk.Slug] = new PathBindable[Talk.Slug] {
    override def bind(key: String, value: String): Either[String, Talk.Slug] =
      stringBinder.bind(key, value).flatMap(Talk.Slug.from(_).left.map(_.getMessage))

    override def unbind(key: String, slug: Talk.Slug): String =
      slug.value
  }

  implicit def talkStatusPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Talk.Status] = new PathBindable[Talk.Status] {
    override def bind(key: String, value: String): Either[String, Talk.Status] =
      stringBinder.bind(key, value).flatMap(Talk.Status.from(_).left.map(_.getMessage))

    override def unbind(key: String, status: Talk.Status): String =
      status.toString
  }

  implicit def cfpSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Cfp.Slug] = new PathBindable[Cfp.Slug] {
    override def bind(key: String, value: String): Either[String, Cfp.Slug] =
      stringBinder.bind(key, value).flatMap(Cfp.Slug.from(_).left.map(_.getMessage))

    override def unbind(key: String, slug: Cfp.Slug): String =
      slug.value
  }

  implicit def proposalIdPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Proposal.Id] = new PathBindable[Proposal.Id] {
    override def bind(key: String, value: String): Either[String, Proposal.Id] =
      stringBinder.bind(key, value).flatMap(Proposal.Id.from(_).left.map(_.getMessage))

    override def unbind(key: String, id: Proposal.Id): String =
      id.value
  }

  implicit def userSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[User.Slug] = new PathBindable[User.Slug] {
    override def bind(key: String, value: String): Either[String, User.Slug] =
      stringBinder.bind(key, value).flatMap(User.Slug.from(_).left.map(_.getMessage))

    override def unbind(key: String, slug: User.Slug): String =
      slug.value
  }

  implicit def partnerSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Partner.Slug] = new PathBindable[Partner.Slug] {
    override def bind(key: String, value: String): Either[String, Partner.Slug] =
      stringBinder.bind(key, value).flatMap(Partner.Slug.from(_).left.map(_.getMessage))

    override def unbind(key: String, slug: Partner.Slug): String =
      slug.value
  }

  implicit def venueIdPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Venue.Id] = new PathBindable[Venue.Id] {
    override def bind(key: String, value: String): Either[String, Venue.Id] =
      stringBinder.bind(key, value).flatMap(Venue.Id.from(_).left.map(_.getMessage))

    override def unbind(key: String, id: Venue.Id): String =
      id.value
  }

  implicit def templateDataRefPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[TemplateData.Ref] = new PathBindable[TemplateData.Ref] {
    override def bind(key: String, value: String): Either[String, TemplateData.Ref] =
      stringBinder.bind(key, value).flatMap(TemplateData.Ref.from(_).left.map(_.getMessage))

    override def unbind(key: String, id: TemplateData.Ref): String =
      id.value
  }

  implicit def sponsorPackSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[SponsorPack.Slug] = new PathBindable[SponsorPack.Slug] {
    override def bind(key: String, value: String): Either[String, SponsorPack.Slug] =
      stringBinder.bind(key, value).flatMap(SponsorPack.Slug.from(_).left.map(_.getMessage))

    override def unbind(key: String, slug: SponsorPack.Slug): String =
      slug.value
  }

  implicit def sponsorIdPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Sponsor.Id] = new PathBindable[Sponsor.Id] {
    override def bind(key: String, value: String): Either[String, Sponsor.Id] =
      stringBinder.bind(key, value).flatMap(Sponsor.Id.from(_).left.map(_.getMessage))

    override def unbind(key: String, id: Sponsor.Id): String =
      id.value
  }
}
