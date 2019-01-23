package fr.gospeak.web.utils

import fr.gospeak.core.domain._
import play.api.mvc.PathBindable

object PathBindables {
  implicit def groupSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Group.Slug] = new PathBindable[Group.Slug] {
    override def bind(key: String, value: String): Either[String, Group.Slug] =
      stringBinder.bind(key, value).map(Group.Slug)

    override def unbind(key: String, slug: Group.Slug): String =
      slug.value
  }

  implicit def eventSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Event.Slug] = new PathBindable[Event.Slug] {
    override def bind(key: String, value: String): Either[String, Event.Slug] =
      stringBinder.bind(key, value).map(Event.Slug)

    override def unbind(key: String, slug: Event.Slug): String =
      slug.value
  }

  implicit def talkSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Talk.Slug] = new PathBindable[Talk.Slug] {
    override def bind(key: String, value: String): Either[String, Talk.Slug] =
      stringBinder.bind(key, value).map(Talk.Slug)

    override def unbind(key: String, slug: Talk.Slug): String =
      slug.value
  }

  implicit def cfpSlugPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Cfp.Slug] = new PathBindable[Cfp.Slug] {
    override def bind(key: String, value: String): Either[String, Cfp.Slug] =
      stringBinder.bind(key, value).map(Cfp.Slug)

    override def unbind(key: String, id: Cfp.Slug): String =
      id.value
  }

  implicit def proposalIdPathBinder(implicit stringBinder: PathBindable[String]): PathBindable[Proposal.Id] = new PathBindable[Proposal.Id] {
    override def bind(key: String, value: String): Either[String, Proposal.Id] =
      stringBinder.bind(key, value).flatMap(Proposal.Id.from(_).toEither.swap.map(_.getMessage).swap)

    override def unbind(key: String, id: Proposal.Id): String =
      id.value
  }
}
