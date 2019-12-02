package fr.gospeak.web.utils

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Page, Url}
import play.api.mvc.QueryStringBindable

import scala.util.Try

object QueryStringBindables {
  private[utils] val dtf1 = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
  private[utils] val dtf2 = DateTimeFormatter.ofPattern("dd'%2F'MM'%2F'yyyy+HH'%3A'mm")
  private[utils] val df1 = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private[utils] val df2 = DateTimeFormatter.ofPattern("dd'%2F'MM'%2F'yyyy")

  implicit def localDateTimeQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[LocalDateTime] =
    new QueryStringBindable[LocalDateTime] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LocalDateTime]] =
        stringBinder.bind(key, params).map(_.flatMap { u =>
          Try(LocalDateTime.parse(u, dtf1))
            .orElse(Try(LocalDateTime.parse(u, dtf2)))
            .orElse(Try(LocalDate.parse(u, df1).atTime(0, 0)))
            .orElse(Try(LocalDate.parse(u, df2).atTime(0, 0)))
            .toEither.left.map(_.getMessage)
        })

      override def unbind(key: String, value: LocalDateTime): String =
        stringBinder.unbind(key, value.format(dtf1))
    }

  implicit def pageParamsQueryStringBindable(implicit stringBinder: QueryStringBindable[String], intBinder: QueryStringBindable[Int]): QueryStringBindable[Page.Params] =
    new QueryStringBindable[Page.Params] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Page.Params]] =
        Some(for {
          page <- intBinder.bind(Page.No.key, params).map(_.map(p => Page.No(p))).getOrElse(Right(Page.Params.defaults.page))
          pageSize <- intBinder.bind(Page.Size.key, params).map(_.flatMap(p => Page.Size.from(p))).getOrElse(Right(Page.Params.defaults.pageSize))
          search <- stringBinder.bind(Page.Search.key, params).map(_.map(s => Some(Page.Search(s)))).getOrElse(Right(Page.Params.defaults.search))
          orderBy <- stringBinder.bind(Page.OrderBy.key, params).map(_.map(Page.OrderBy.parse)).getOrElse(Right(Page.Params.defaults.orderBy))
        } yield Page.Params(page, pageSize, search, orderBy))

      override def unbind(key: String, value: Page.Params): String =
        Seq(
          Some(value.page).filter(_.nonEmpty).map(p => intBinder.unbind(p.key, p.value)),
          Some(value.pageSize).filter(_.nonEmpty).map(p => intBinder.unbind(p.key, p.value)),
          value.search.filter(_.nonEmpty).map(p => stringBinder.unbind(p.key, p.value)),
          value.orderBy.filter(_.nonEmpty).map(p => stringBinder.unbind(p.key, p.value))
        ).flatten.mkString("&")
    }

  implicit def externalCfpDuplicateParamsQueryStringBindable(implicit stringBinder: QueryStringBindable[String], ldtBinder: QueryStringBindable[LocalDateTime]): QueryStringBindable[ExternalCfp.DuplicateParams] =
    new QueryStringBindable[ExternalCfp.DuplicateParams] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ExternalCfp.DuplicateParams]] =
        Some(for {
          cfpUrl <- stringBinder.bind("cfpUrl", params).sequence
          cfpName <- stringBinder.bind("cfpName", params).sequence
          cfpEndDate <- ldtBinder.bind("cfpEndDate", params).sequence
          eventUrl <- stringBinder.bind("eventUrl", params).sequence
          eventStartDate <- ldtBinder.bind("eventStartDate", params).sequence
          twitterAccount <- stringBinder.bind("twitterAccount", params).sequence
          twitterHashtag <- stringBinder.bind("twitterHashtag", params).sequence
        } yield ExternalCfp.DuplicateParams(cfpUrl, cfpName, cfpEndDate, eventUrl, eventStartDate, twitterAccount, twitterHashtag))

      override def unbind(key: String, value: ExternalCfp.DuplicateParams): String =
        Seq(
          value.cfpUrl.filter(_.nonEmpty).map(p => stringBinder.unbind("cfpUrl", p)),
          value.cfpName.filter(_.nonEmpty).map(p => stringBinder.unbind("cfpName", p)),
          value.cfpEndDate.map(p => ldtBinder.unbind("cfpEndDate", p)),
          value.eventUrl.filter(_.nonEmpty).map(p => stringBinder.unbind("eventUrl", p)),
          value.eventStartDate.map(p => ldtBinder.unbind("eventStartDate", p)),
          value.twitterAccount.filter(_.nonEmpty).map(p => stringBinder.unbind("twitterAccount", p)),
          value.twitterHashtag.filter(_.nonEmpty).map(p => stringBinder.unbind("twitterHashtag", p))
        ).flatten.mkString("&")
    }

  implicit def urlQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Url] =
    new QueryStringBindable[Url] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Url]] =
        stringBinder.bind(key, params).map(_.flatMap(u => Url.from(u).left.map(_.getMessage)))

      override def unbind(key: String, value: Url): String =
        stringBinder.unbind(key, value.value)
    }

  implicit def userRequestIdQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[UserRequest.Id] =
    new QueryStringBindable[UserRequest.Id] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, UserRequest.Id]] =
        stringBinder.bind(key, params).map(_.flatMap(u => UserRequest.Id.from(u).left.map(_.getMessage)))

      override def unbind(key: String, value: UserRequest.Id): String =
        stringBinder.unbind(key, value.value)
    }

  implicit def eventSlugQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Event.Slug] =
    new QueryStringBindable[Event.Slug] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Event.Slug]] =
        stringBinder.bind(key, params).map(_.flatMap(u => Event.Slug.from(u).left.map(_.getMessage)))

      override def unbind(key: String, value: Event.Slug): String =
        stringBinder.unbind(key, value.value)
    }

  implicit def partnerSlugQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Partner.Slug] =
    new QueryStringBindable[Partner.Slug] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Partner.Slug]] =
        stringBinder.bind(key, params).map(_.flatMap(u => Partner.Slug.from(u).left.map(_.getMessage)))

      override def unbind(key: String, value: Partner.Slug): String =
        stringBinder.unbind(key, value.value)
    }

  implicit def sponsorPackSlugQueryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[SponsorPack.Slug] =
    new QueryStringBindable[SponsorPack.Slug] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, SponsorPack.Slug]] =
        stringBinder.bind(key, params).map(_.flatMap(u => SponsorPack.Slug.from(u).left.map(_.getMessage)))

      override def unbind(key: String, value: SponsorPack.Slug): String =
        stringBinder.unbind(key, value.value)
    }
}
