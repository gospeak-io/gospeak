package gospeak.web.utils

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime}
import gospeak.core.domain._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain.{CustomException, Page, Url}
import play.api.mvc.QueryStringBindable

import scala.util.Try

object QueryStringBindables {
  private[utils] val dtf1 = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
  private[utils] val dtf2 = DateTimeFormatter.ofPattern("dd'%2F'MM'%2F'yyyy+HH'%3A'mm")
  private[utils] val df1 = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private[utils] val df2 = DateTimeFormatter.ofPattern("dd'%2F'MM'%2F'yyyy")

  implicit def instantQueryStringBindable(implicit s: QueryStringBindable[String]): QueryStringBindable[Instant] =
    stringBindable(TimeUtils.parseInstant(_).toEither.leftMap(e => CustomException(s"Bad Instant query string: ${e.getMessage}")), _.toString)

  implicit def localDateTimeQueryStringBindable(implicit s: QueryStringBindable[String]): QueryStringBindable[LocalDateTime] =
    new QueryStringBindable[LocalDateTime] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LocalDateTime]] =
        s.bind(key, params).map(_.flatMap { u =>
          Try(LocalDateTime.parse(u, dtf1))
            .orElse(Try(LocalDateTime.parse(u, dtf2)))
            .orElse(Try(LocalDate.parse(u, df1).atTime(0, 0)))
            .orElse(Try(LocalDate.parse(u, df2).atTime(0, 0)))
            .toEither.left.map(_.getMessage)
        })

      override def unbind(key: String, value: LocalDateTime): String =
        s.unbind(key, value.format(dtf1))
    }

  implicit def pageParamsQueryStringBindable(implicit s: QueryStringBindable[String], i: QueryStringBindable[Int]): QueryStringBindable[Page.Params] =
    new QueryStringBindable[Page.Params] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Page.Params]] =
        Some(for {
          page <- i.bind(Page.No.key, params).map(_.map(p => Page.No(p))).getOrElse(Right(Page.Params.defaults.page))
          pageSize <- i.bind(Page.Size.key, params).map(_.flatMap(p => Page.Size.from(p))).getOrElse(Right(Page.Params.defaults.pageSize))
          search <- s.bind(Page.Search.key, params).map(_.map(s => Some(Page.Search(s)))).getOrElse(Right(Page.Params.defaults.search))
          orderBy <- s.bind(Page.OrderBy.key, params).map(_.map(Page.OrderBy.parse)).getOrElse(Right(Page.Params.defaults.orderBy))
          ignoreKeys = Set(Page.No.key, Page.Size.key, Page.Search.key, Page.OrderBy.key)
          filters = params.filterKeys(!ignoreKeys.contains(_)).collect { case (key, Seq(value)) => key -> value }
        } yield Page.Params(page, pageSize, search, orderBy, filters))

      override def unbind(key: String, value: Page.Params): String =
        (List(
          Some(value.page).filter(_.nonEmpty).map(p => i.unbind(p.key, p.value)),
          Some(value.pageSize).filter(_.nonEmpty).map(p => i.unbind(p.key, p.value)),
          value.search.filter(_.nonEmpty).map(p => s.unbind(p.key, p.value)),
          value.orderBy.map(p => s.unbind(p.key, p.value))
        ).flatten ++ value.filters.map { case (key, value) => s.unbind(key, value) }).mkString("&")
    }

  implicit def externalCfpDuplicateParamsQueryStringBindable(implicit s: QueryStringBindable[String], d: QueryStringBindable[LocalDateTime]): QueryStringBindable[ExternalCfp.DuplicateParams] =
    new QueryStringBindable[ExternalCfp.DuplicateParams] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ExternalCfp.DuplicateParams]] =
        Some(for {
          cfpUrl <- s.bind("cfpUrl", params).sequence
          cfpName <- s.bind("cfpName", params).sequence
          cfpEndDate <- d.bind("cfpEndDate", params).sequence
          eventUrl <- s.bind("eventUrl", params).sequence
          eventStartDate <- d.bind("eventStartDate", params).sequence
          twitterAccount <- s.bind("twitterAccount", params).sequence
          twitterHashtag <- s.bind("twitterHashtag", params).sequence
        } yield ExternalCfp.DuplicateParams(cfpUrl, cfpName, cfpEndDate, eventUrl, eventStartDate, twitterAccount, twitterHashtag))

      override def unbind(key: String, value: ExternalCfp.DuplicateParams): String =
        List(
          value.cfpUrl.filter(_.nonEmpty).map(p => s.unbind("cfpUrl", p)),
          value.cfpName.filter(_.nonEmpty).map(p => s.unbind("cfpName", p)),
          value.cfpEndDate.map(p => d.unbind("cfpEndDate", p)),
          value.eventUrl.filter(_.nonEmpty).map(p => s.unbind("eventUrl", p)),
          value.eventStartDate.map(p => d.unbind("eventStartDate", p)),
          value.twitterAccount.filter(_.nonEmpty).map(p => s.unbind("twitterAccount", p)),
          value.twitterHashtag.filter(_.nonEmpty).map(p => s.unbind("twitterHashtag", p))
        ).flatten.mkString("&")
    }

  implicit def urlQueryStringBindable(implicit s: QueryStringBindable[String]): QueryStringBindable[Url] =
    stringBindable(Url.from, _.value)

  implicit def urlVideosChannelQueryStringBindable(implicit s: QueryStringBindable[String]): QueryStringBindable[Url.Videos.Channel] =
    stringBindable(Url.Videos.Channel.from, _.value)

  implicit def urlVideosPlaylistQueryStringBindable(implicit s: QueryStringBindable[String]): QueryStringBindable[Url.Videos.Playlist] =
    stringBindable(Url.Videos.Playlist.from, _.value)

  implicit def userRequestIdQueryStringBindable(implicit s: QueryStringBindable[String]): QueryStringBindable[UserRequest.Id] =
    stringBindable(UserRequest.Id.from, _.value)

  implicit def eventSlugQueryStringBindable(implicit s: QueryStringBindable[String]): QueryStringBindable[Event.Slug] =
    stringBindable(Event.Slug.from, _.value)

  implicit def partnerSlugQueryStringBindable(implicit s: QueryStringBindable[String]): QueryStringBindable[Partner.Slug] =
    stringBindable(Partner.Slug.from, _.value)

  implicit def sponsorPackSlugQueryStringBindable(implicit s: QueryStringBindable[String]): QueryStringBindable[SponsorPack.Slug] =
    stringBindable(SponsorPack.Slug.from, _.value)

  private def stringBindable[A](from: String => Either[CustomException, A], to: A => String)(implicit s: QueryStringBindable[String]): QueryStringBindable[A] =
    new QueryStringBindable[A] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, A]] =
        s.bind(key, params).map(_.flatMap(u => from(u).left.map(_.getMessage)))

      override def unbind(key: String, value: A): String =
        s.unbind(key, to(value))
    }
}
