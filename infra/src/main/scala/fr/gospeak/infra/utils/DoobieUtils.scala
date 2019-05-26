package fr.gospeak.infra.utils

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import doobie.Transactor
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update
import doobie.util.{Meta, Write}
import fr.gospeak.core.domain._
import fr.gospeak.infra.formats.JsonFormats._
import fr.gospeak.infra.services.storage.sql.DatabaseConf
import fr.gospeak.libs.scalautils.domain._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

object DoobieUtils {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def transactor(conf: DatabaseConf): doobie.Transactor[IO] = conf match {
    case c: DatabaseConf.H2 => Transactor.fromDriverManager[IO]("org.h2.Driver", c.url, "", "")
    case c: DatabaseConf.PostgreSQL => Transactor.fromDriverManager[IO]("org.postgresql.Driver", c.url, c.user, c.pass.decode)
  }

  object Fragments {
    val empty = fr0""
    val space = fr0" "

    def buildInsert(table: Fragment, fields: Fragment, values: Fragment): Fragment =
      fr"INSERT INTO" ++ table ++ fr0" (" ++ fields ++ fr0") VALUES (" ++ values ++ fr0")"

    def buildSelect(table: Fragment, fields: Fragment): Fragment =
      fr"SELECT" ++ fields ++ fr" FROM" ++ table

    def buildSelect(table: Fragment, fields: Fragment, where: Fragment): Fragment =
      buildSelect(table, fields) ++ space ++ where

    def buildUpdate(table: Fragment, fields: Fragment, where: Fragment): Fragment =
      fr"UPDATE" ++ table ++ fr" SET" ++ fields ++ space ++ where

    def buildDelete(table: Fragment, where: Fragment): Fragment =
      fr"DELETE FROM" ++ table ++ space ++ where

    final case class Paginate(where: Fragment, orderBy: Fragment, limit: Fragment) {
      def all: Fragment = Seq(where, orderBy, limit).reduce(_ ++ _)
    }

    // TODO improve tests & extract some methods
    def paginate(params: Page.Params, searchFields: Seq[String], defaultSort: Page.OrderBy, whereOpt: Option[Fragment] = None, prefix: Option[String] = None): Paginate = {
      val search = params.search.filter(_ => searchFields.nonEmpty)
        .map { search => searchFields.map(s => Fragment.const(prefix.map(_ + ".").getOrElse("") + s) ++ fr"ILIKE ${"%" + search.value + "%"}").reduce(_ ++ fr"OR" ++ _) }
        .map { search => whereOpt.map(_ ++ fr" AND" ++ fr0"(" ++ search ++ fr")").getOrElse(fr"WHERE" ++ search) }
        .orElse(whereOpt.map(_ ++ space))
        .getOrElse(empty)

      Paginate(
        search,
        orderByFragment(params.orderBy.getOrElse(defaultSort), prefix, params.nullsFirst),
        limitFragment(params.offsetStart, params.pageSize))
    }

    private def orderByFragment(orderBy: Page.OrderBy, prefix: Option[String], nullsFirst: Boolean): Fragment = {
      if (orderBy.nonEmpty) {
        val fields = orderBy.values.map { v =>
          val p = prefix.map(_ + ".").getOrElse("")
          val f = p + v.stripPrefix("-")
          if (nullsFirst) {
            s"$f IS NOT NULL, $f" + (if (v.startsWith("-")) " DESC" else "")

          } else {
            s"$f IS NULL, $f" + (if (v.startsWith("-")) " DESC" else "")
          }
        }
        fr"ORDER BY" ++ Fragment.const(fields.mkString(", "))
      } else {
        fr0""
      }
    }

    private def limitFragment(start: Int, size: Page.Size): Fragment =
      fr"OFFSET" ++ Fragment.const(start.toString) ++ fr"LIMIT" ++ Fragment.const0(size.value.toString)
  }

  object Queries {
    def insertOne[A](insert: A => doobie.Update0)(elt: A)(implicit w: Write[A]): doobie.ConnectionIO[Int] =
      insert(elt).run

    def insertMany[A](insert: A => doobie.Update0)(elts: NonEmptyList[A])(implicit w: Write[A]): doobie.ConnectionIO[Int] =
      Update[A](insert(elts.head).sql).updateMany(elts)

    def selectPage[A](in: Page.Params => (doobie.Query0[A], doobie.Query0[Long]), params: Page.Params): doobie.ConnectionIO[Page[A]] = {
      val (select, count) = in(params)
      for {
        elts <- select.to[List]
        total <- count.unique
      } yield Page(elts, params, Page.Total(total))
    }
  }

  object Mappings {
    import scala.reflect.runtime.universe._

    implicit val timePeriodMeta: Meta[TimePeriod] = Meta[String].timap(TimePeriod.from(_).get)(_.value)
    implicit val finiteDurationMeta: Meta[FiniteDuration] = Meta[Long].timap(Duration.fromNanos)(_.toNanos)
    implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Instant].timap(LocalDateTime.ofInstant(_, ZoneOffset.UTC))(_.toInstant(ZoneOffset.UTC))
    implicit val emailAddressMeta: Meta[EmailAddress] = Meta[String].timap(EmailAddress.from(_).right.get)(_.value)
    implicit val urlMeta: Meta[Url] = Meta[String].timap(Url.from(_).right.get)(_.value)
    implicit val slidesMeta: Meta[Slides] = Meta[String].timap(Slides.from(_).right.get)(_.value)
    implicit val videoMeta: Meta[Video] = Meta[String].timap(Video.from(_).right.get)(_.value)
    implicit val currencyMeta: Meta[Price.Currency] = Meta[String].timap(Price.Currency.from(_).get)(_.value)
    implicit val markdownMeta: Meta[Markdown] = Meta[String].timap(Markdown)(_.value)
    implicit def markdownTemplateMeta[A: TypeTag]: Meta[MarkdownTemplate[A]] = Meta[String].timap(MarkdownTemplate.Mustache[A](_).asInstanceOf[MarkdownTemplate[A]])(_.value)
    implicit val gMapPlaceMeta: Meta[GMapPlace] = Meta[String].timap(fromJson[GMapPlace](_).get)(toJson)
    implicit val avatarSourceMeta: Meta[Avatar.Source] = Meta[String].timap(Avatar.Source.from(_).right.get)(_.toString)
    implicit val tagsMeta: Meta[Seq[Tag]] = Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Tag(_)).toSeq)(_.map(_.value).mkString(","))

     val a = implicitly[Meta[LocalDate]]

    // TODO build Meta[Seq[A]] and Meta[NonEmptyList[A]]
    // implicit def seqMeta[A](implicit m: Meta[A]): Meta[Seq[A]] = ???
    // implicit def nelMeta[A](implicit m: Meta[A]): Meta[NonEmptyList[A]] = ???

    implicit val userIdMeta: Meta[User.Id] = Meta[String].timap(User.Id.from(_).right.get)(_.value)
    implicit val userSlugMeta: Meta[User.Slug] = Meta[String].timap(User.Slug.from(_).right.get)(_.value)
    implicit val userRequestIdMeta: Meta[UserRequest.Id] = Meta[String].timap(UserRequest.Id.from(_).right.get)(_.value)
    implicit val talkIdMeta: Meta[Talk.Id] = Meta[String].timap(Talk.Id.from(_).right.get)(_.value)
    implicit val talkSlugMeta: Meta[Talk.Slug] = Meta[String].timap(Talk.Slug.from(_).right.get)(_.value)
    implicit val talkTitleMeta: Meta[Talk.Title] = Meta[String].timap(Talk.Title)(_.value)
    implicit val talkStatusMeta: Meta[Talk.Status] = Meta[String].timap(Talk.Status.from(_).right.get)(_.toString)
    implicit val groupIdMeta: Meta[Group.Id] = Meta[String].timap(Group.Id.from(_).right.get)(_.value)
    implicit val groupSlugMeta: Meta[Group.Slug] = Meta[String].timap(Group.Slug.from(_).right.get)(_.value)
    implicit val groupNameMeta: Meta[Group.Name] = Meta[String].timap(Group.Name)(_.value)
    implicit val eventIdMeta: Meta[Event.Id] = Meta[String].timap(Event.Id.from(_).right.get)(_.value)
    implicit val eventSlugMeta: Meta[Event.Slug] = Meta[String].timap(Event.Slug.from(_).right.get)(_.value)
    implicit val eventNameMeta: Meta[Event.Name] = Meta[String].timap(Event.Name)(_.value)
    implicit val cfpIdMeta: Meta[Cfp.Id] = Meta[String].timap(Cfp.Id.from(_).right.get)(_.value)
    implicit val cfpSlugMeta: Meta[Cfp.Slug] = Meta[String].timap(Cfp.Slug.from(_).right.get)(_.value)
    implicit val cfpNameMeta: Meta[Cfp.Name] = Meta[String].timap(Cfp.Name)(_.value)
    implicit val proposalIdMeta: Meta[Proposal.Id] = Meta[String].timap(Proposal.Id.from(_).right.get)(_.value)
    implicit val proposalStatusMeta: Meta[Proposal.Status] = Meta[String].timap(Proposal.Status.from(_).right.get)(_.toString)
    implicit val partnerIdMeta: Meta[Partner.Id] = Meta[String].timap(Partner.Id.from(_).right.get)(_.value)
    implicit val partnerSlugMeta: Meta[Partner.Slug] = Meta[String].timap(Partner.Slug.from(_).right.get)(_.value)
    implicit val partnerNameMeta: Meta[Partner.Name] = Meta[String].timap(Partner.Name)(_.value)
    implicit val venueIdMeta: Meta[Venue.Id] = Meta[String].timap(Venue.Id.from(_).right.get)(_.value)
    implicit val sponsorPackIdMeta: Meta[SponsorPack.Id] = Meta[String].timap(SponsorPack.Id.from(_).right.get)(_.value)
    implicit val sponsorPackSlugMeta: Meta[SponsorPack.Slug] = Meta[String].timap(SponsorPack.Slug.from(_).right.get)(_.value)
    implicit val sponsorPackNameMeta: Meta[SponsorPack.Name] = Meta[String].timap(SponsorPack.Name)(_.value)
    implicit val sponsorIdMeta: Meta[Sponsor.Id] = Meta[String].timap(Sponsor.Id.from(_).right.get)(_.value)
    implicit val userIdNelMeta: Meta[NonEmptyList[User.Id]] = Meta[String].timap(
      s => NonEmptyList.fromListUnsafe(s.split(",").filter(_.nonEmpty).map(User.Id.from(_).right.get).toList))(
      _.map(_.value).toList.mkString(","))
    implicit val proposalIdSeqMeta: Meta[Seq[Proposal.Id]] = Meta[String].timap(
      _.split(",").filter(_.nonEmpty).map(Proposal.Id.from(_).right.get).toSeq)(
      _.map(_.value).mkString(","))
  }

}
