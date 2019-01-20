package fr.gospeak.infra.utils

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import doobie.Transactor
import doobie.implicits._
import doobie.util.Meta
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain._
import fr.gospeak.infra.services.storage.sql.{DbSqlConf, H2, PostgreSQL}

import scala.concurrent.ExecutionContext

object DoobieUtils {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def transactor(conf: DbSqlConf): doobie.Transactor[IO] = conf match {
    case c: H2 => Transactor.fromDriverManager[IO](c.driver, c.url, "", "")
    case c: PostgreSQL => Transactor.fromDriverManager[IO]("org.postgresql.Driver", c.url, c.user, c.pass.decode)
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

    // TODO improve tests & extract some methods
    def paginate(params: Page.Params, searchFields: Seq[String], defaultSort: Page.OrderBy, whereOpt: Option[Fragment] = None, prefix: Option[String] = None): Fragment = {
      val search = params.search.filter(_ => searchFields.nonEmpty)
        .map { search => searchFields.map(s => Fragment.const(prefix.map(_ + ".").getOrElse("") + s) ++ fr"LIKE '%${search.value}%'").reduce(_ ++ fr"OR" ++ _) }
        .map { search => whereOpt.map(_ ++ fr" AND" ++ fr0"(" ++ search ++ fr")").getOrElse(fr"WHERE" ++ search) }
        .orElse(whereOpt.map(_ ++ space))
        .getOrElse(empty)

      Seq(
        search,
        orderByFragment(params.orderBy.getOrElse(defaultSort), prefix),
        limitFragment(params.offsetStart, params.pageSize)
      ).reduce(_ ++ _)
    }

    private def orderByFragment(orderBy: Page.OrderBy, prefix: Option[String]): Fragment =
      if (orderBy.value.startsWith("-")) fr"ORDER BY" ++ Fragment.const(prefix.map(_ + ".").getOrElse("") + orderBy.value.stripPrefix("-") + "DESC")
      else fr"ORDER BY" ++ Fragment.const(prefix.map(_ + ".").getOrElse("") + orderBy.value)

    private def limitFragment(start: Int, size: Page.Size): Fragment =
      fr"OFFSET" ++ Fragment.const(start.toString) ++ fr"LIMIT" ++ Fragment.const0(size.value.toString)
  }

  object Mappings {
    implicit val userIdMeta: Meta[User.Id] = Meta[String].timap(User.Id.from(_).get)(_.value)
    implicit val talkIdMeta: Meta[Talk.Id] = Meta[String].timap(Talk.Id.from(_).get)(_.value)
    implicit val talkSlugMeta: Meta[Talk.Slug] = Meta[String].timap(Talk.Slug)(_.value)
    implicit val talkTitleMeta: Meta[Talk.Title] = Meta[String].timap(Talk.Title)(_.value)
    implicit val groupIdMeta: Meta[Group.Id] = Meta[String].timap(Group.Id.from(_).get)(_.value)
    implicit val groupSlugMeta: Meta[Group.Slug] = Meta[String].timap(Group.Slug)(_.value)
    implicit val groupNameMeta: Meta[Group.Name] = Meta[String].timap(Group.Name)(_.value)
    implicit val eventIdMeta: Meta[Event.Id] = Meta[String].timap(Event.Id.from(_).get)(_.value)
    implicit val eventSlugMeta: Meta[Event.Slug] = Meta[String].timap(Event.Slug)(_.value)
    implicit val eventNameMeta: Meta[Event.Name] = Meta[String].timap(Event.Name)(_.value)
    implicit val proposalIdMeta: Meta[Proposal.Id] = Meta[String].timap(Proposal.Id.from(_).get)(_.value)
    implicit val userIdNelMeta: Meta[NonEmptyList[User.Id]] = Meta[String].timap(
      s => NonEmptyList.fromListUnsafe(s.split(",").filter(_.nonEmpty).map(User.Id.from(_).get).toList))(
      _.map(_.value).toList.mkString(","))
    implicit val poposalIdSeqMeta: Meta[Seq[Proposal.Id]] = Meta[String].timap(
      _.split(",").filter(_.nonEmpty).map(Proposal.Id.from(_).get).toSeq)(
      _.map(_.value).mkString(","))
  }

}
