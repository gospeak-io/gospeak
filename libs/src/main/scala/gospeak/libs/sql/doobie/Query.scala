package gospeak.libs.sql.doobie

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import doobie.util.Read
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Done, Page}

import scala.util.control.NonFatal

object Query {

  final case class Insert[A](table: String, fields: List[Field], elt: A, build: A => Fragment) {
    def fr: Fragment = const0(s"INSERT INTO $table (${fields.map(_.name).mkString(", ")}) VALUES (") ++ build(elt) ++ fr0")"

    def run(xa: doobie.Transactor[IO]): IO[A] =
      exec(fr, _.update.run, xa).flatMap {
        case 1 => IO.pure(elt)
        case code => IO.raiseError(CustomException(s"Failed to insert $elt in $table (code: $code)"))
      }
  }

  final case class Update(table: Fragment, fields: Fragment, where: Fragment) {
    def where(fr: Fragment): Update = copy(where = fr0" WHERE " ++ fr)

    def fr: Fragment = fr0"UPDATE " ++ table ++ fr0" SET " ++ fields ++ where

    def run(xa: doobie.Transactor[IO]): IO[Done] =
      exec(fr, _.update.run, xa).flatMap {
        case 1 => IO.pure(Done)
        case code => IO.raiseError(CustomException(s"Failed to update ${fr.update.sql} (code: $code)"))
      }
  }

  final case class Delete(table: Fragment, where: Fragment) {
    def where(fr: Fragment): Delete = copy(where = fr0" WHERE " ++ fr)

    def fr: Fragment = fr0"DELETE FROM " ++ table ++ where

    def run(xa: doobie.Transactor[IO]): IO[Done] =
      exec(fr, _.update.run, xa).flatMap {
        case 1 => IO.pure(Done)
        case code => IO.raiseError(CustomException(s"Failed to delete ${fr.update.sql} (code: $code)"))
      }
  }

  final case class Select[A: Read](table: Fragment,
                                   fields: List[Field],
                                   aggFields: List[AggregateField],
                                   customFields: List[CustomField],
                                   whereOpt: Option[Fragment],
                                   sorts: Table.Sorts,
                                   offset: Option[Fragment],
                                   limit: Option[Int]) {
    def fields(fields: Field*): Select[A] = copy(fields = fields.toList)

    def fields(fields: List[Field]): Select[A] = copy(fields = fields)

    def where(fr: Fragment): Select[A] = copy(whereOpt = Some(fr0" WHERE " ++ fr))

    def sort(s: Table.Sort): Select[A] = copy(sorts = Table.Sorts(s))

    def offset(fr: Fragment): Select[A] = copy(offset = Some(fr))

    def one: Select[A] = copy(limit = Some(1))

    def fr: Fragment = {
      val select = const0(s"SELECT ${(fields.map(_.value) ++ aggFields.map(_.value)).mkString(", ")}") ++ customFields.map(fr0", " ++ _.value).foldLeft(fr0"")(_ ++ _) ++ fr0" FROM " ++ table
      val where = whereOpt.getOrElse(fr0"")
      val groupBy = aggFields.headOption.map(_ => const0(s" GROUP BY ${fields.map(_.label).mkString(", ")}")).getOrElse(fr0"")
      val orderBy = orderByFragment(sorts.default)
      select ++ where ++ groupBy ++ orderBy ++
        limit.map(l => const0(s" LIMIT $l")).getOrElse(fr0"") ++
        offset.map(o => const0(s" OFFSET ") ++ o).getOrElse(fr0"")
    }

    def query: doobie.Query0[A] = fr.query[A]

    def runList(xa: doobie.Transactor[IO]): IO[List[A]] = exec(fr, _.query[A].to[List], xa)

    def runOption(xa: doobie.Transactor[IO]): IO[Option[A]] = exec(fr, _.query[A].option, xa)

    def runUnique(xa: doobie.Transactor[IO]): IO[A] = exec(fr, _.query[A].unique, xa)

    def runExists(xa: doobie.Transactor[IO]): IO[Boolean] = exec(fr, _.query[A].option.map(_.isDefined), xa)
  }

  final case class SelectPage[A: Read](table: Fragment,
                                       prefix: String,
                                       fields: List[Field],
                                       aggFields: List[AggregateField],
                                       customFields: List[CustomField],
                                       whereOpt: Option[Fragment],
                                       havingOpt: Option[Fragment],
                                       params: Page.Params,
                                       sorts: Table.Sorts,
                                       searchFields: List[Field],
                                       filters: List[Table.Filter],
                                       ctx: DbCtx) {
    private val select: Fragment = const0(s"SELECT ${(fields.map(_.value) ++ aggFields.map(_.value)).mkString(", ")}") ++ customFields.map(fr0", " ++ _.value).foldLeft(fr0"")(_ ++ _) ++ fr0" FROM " ++ table
    private val groupBy: Fragment = aggFields.headOption.map(_ => const0(s" GROUP BY ${fields.map(_.label).mkString(", ")}")).getOrElse(fr0"")
    private val having: Fragment = filterClause(havingOpt, aggregation = true).getOrElse(fr0"")

    def fields(fields: Field*): SelectPage[A] = copy(fields = fields.toList)

    def fields(fields: List[Field]): SelectPage[A] = copy(fields = fields)

    def where(fr: Fragment): SelectPage[A] = copy(whereOpt = Some(fr0" WHERE " ++ fr))

    private def filterClause(current: Option[Fragment], aggregation: Boolean): Option[Fragment] = {
      val keyword = if (aggregation) "HAVING" else "WHERE"
      filters.filter(_.aggregation == aggregation).flatMap(f => params.filters.get(f.key).flatMap(f.filter(_)(ctx))).foldLeft(current) { (acc, item) =>
        Some(acc.map(w => w ++ fr0" AND (" ++ item ++ fr0")").getOrElse(const0(s" $keyword (") ++ item ++ fr0")"))
      }
    }

    def fr: Fragment = {
      val (where, orderBy, limit) = paginationFragment(prefix, filterClause(whereOpt, aggregation = false), params, sorts, searchFields)
      select ++ where ++ groupBy ++ having ++ orderBy ++ limit
    }

    def countFr: Fragment = {
      val select = const0(s"SELECT ${fields.headOption.map(_.value).getOrElse("*")} FROM ") ++ table
      val where = whereFragment(filterClause(whereOpt, aggregation = false), params.search, searchFields).getOrElse(fr0"")
      fr0"SELECT COUNT(*) FROM (" ++ select ++ where ++ groupBy ++ having ++ fr0") as cnt"
    }

    def query: doobie.Query0[A] = fr.query[A]

    def countQuery: doobie.Query0[Long] = countFr.query[Long]

    def run(xa: doobie.Transactor[IO]): IO[Page[A]] = exec(fr, fr => for {
      elts <- fr.query[A].to[List]
      total <- countFr.query[Long].unique
    } yield Page(elts, params.defaultOrderBy(sorts.sorts.head.key), Page.Total(total)), xa)
  }

  private def exec[A](fr: Fragment, run: Fragment => doobie.ConnectionIO[A], xa: doobie.Transactor[IO]): IO[A] =
    run(fr).transact(xa).recoverWith { case NonFatal(e) => IO.raiseError(new Exception(s"Fail on ${fr.query.sql}: ${e.getMessage}", e)) }

  private[doobie] def whereFragment(whereOpt: Option[Fragment], search: Option[Page.Search], fields: List[Field]): Option[Fragment] = {
    search.filter(_ => fields.nonEmpty)
      .map { search => fields.map(s => const0(s.value + " ") ++ fr0"ILIKE ${"%" + search.value + "%"}").reduce(_ ++ fr0" OR " ++ _) }
      .map { search => whereOpt.map(_ ++ fr0" AND " ++ fr0"(" ++ search ++ fr0")").getOrElse(fr0" WHERE " ++ search) }
      .orElse(whereOpt)
  }

  private[doobie] def orderByFragment(sort: NonEmptyList[Field], nullsFirst: Boolean = false): Fragment = {
    val fields = sort.map { v =>
      val f = v.copy(name = v.name.stripPrefix("-")).value
      if (nullsFirst) {
        s"$f IS NOT NULL, $f" + (if (v.name.startsWith("-")) " DESC" else "")
      } else {
        s"$f IS NULL, $f" + (if (v.name.startsWith("-")) " DESC" else "")
      }
    }
    fr0" ORDER BY " ++ const0(fields.toList.mkString(", "))
  }

  private[doobie] def limitFragment(size: Page.Size, start: Page.Offset): Fragment =
    fr0" LIMIT " ++ const0(size.value.toString) ++ fr0" OFFSET " ++ const0(start.value.toString)

  private[doobie] def paginationFragment(prefix: String, whereOpt: Option[Fragment], params: Page.Params, sorts: Table.Sorts, searchFields: List[Field]): (Fragment, Fragment, Fragment) = {
    val where = whereFragment(whereOpt, params.search, searchFields).getOrElse(fr0"")
    val sortFields = sorts.get(params.orderBy, prefix)
    val orderBy = orderByFragment(sortFields, params.nullsFirst)
    val limit = limitFragment(params.pageSize, params.offset)
    (where, orderBy, limit)
  }
}
