package fr.gospeak.web.utils

import fr.gospeak.core.domain.{Event, Group, UserRequest}
import fr.gospeak.libs.scalautils.domain.{Page, Url}
import play.api.mvc.QueryStringBindable
import fr.gospeak.libs.scalautils.Extensions._

object QueryStringBindables {
  implicit def pageParamsQueryStringBindable(implicit stringBinder: QueryStringBindable[String], intBinder: QueryStringBindable[Int]): QueryStringBindable[Page.Params] =
    new QueryStringBindable[Page.Params] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Page.Params]] =
        Some(for {
          page <- intBinder.bind(Page.No.key, params).map(_.map(p => Page.No(p))).getOrElse(Right(Page.Params.defaults.page))
          pageSize <- intBinder.bind(Page.Size.key, params).map(_.map(p => Page.Size(p))).getOrElse(Right(Page.Params.defaults.pageSize))
          search <- stringBinder.bind(Page.Search.key, params).map(_.map(s => Some(Page.Search(s)))).getOrElse(Right(Page.Params.defaults.search))
          orderBy <- stringBinder.bind(Page.OrderBy.key, params).map(_.map(Page.OrderBy.parse)).getOrElse(Right(Page.Params.defaults.orderBy))
        } yield Page.Params(page, pageSize, search, orderBy))

      override def unbind(key: String, params: Page.Params): String =
        Seq(
          Some(params.page).filter(_.nonEmpty).map(p => intBinder.unbind(p.key, p.value)),
          Some(params.pageSize).filter(_.nonEmpty).map(p => intBinder.unbind(p.key, p.value)),
          params.search.filter(_.nonEmpty).map(p => stringBinder.unbind(p.key, p.value)),
          params.orderBy.filter(_.nonEmpty).map(p => stringBinder.unbind(p.key, p.value))
        ).flatten.mkString("&")
    }

  implicit def urlQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Url] =
    new QueryStringBindable[Url] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Url]] =
        stringBinder.bind(key, params).map(_.flatMap(u => Url.from(u).left.map(_.getMessage)))

      override def unbind(key: String, url: Url): String =
        stringBinder.unbind(key, url.value)
    }

  implicit def userRequestIdQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[UserRequest.Id] =
    new QueryStringBindable[UserRequest.Id] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, UserRequest.Id]] =
        stringBinder.bind(key, params).map(_.flatMap(u => UserRequest.Id.from(u).left.map(_.getMessage)))

      override def unbind(key: String, id: UserRequest.Id): String =
        stringBinder.unbind(key, id.value)
    }

  implicit def eventSlugQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Event.Slug] =
    new QueryStringBindable[Event.Slug] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Event.Slug]] =
        stringBinder.bind(key, params).map(_.flatMap(u => Event.Slug.from(u).left.map(_.getMessage)))

      override def unbind(key: String, slug: Event.Slug): String =
        stringBinder.unbind(key, slug.value)
    }

  implicit def groupSettingsEventQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Group.Settings.Events.Event] =
    new QueryStringBindable[Group.Settings.Events.Event] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Group.Settings.Events.Event]] =
        stringBinder.bind(key, params).map(_.flatMap(e => Group.Settings.Events.Event.all.find(_.toString == e).toEither(s"Invalid event '$e'")))

      override def unbind(key: String, event: Group.Settings.Events.Event): String =
        stringBinder.unbind(key, event.toString)
    }
}
