package gospeak.infra.services.storage.sql.utils

import java.time.{Instant, LocalDateTime, ZoneOffset}

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import doobie.util.transactor.Transactor
import doobie.util.{Meta, Read}
import gospeak.core.ApplicationConf
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.BasicCtx
import gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup}
import gospeak.core.services.slack.domain.SlackAction
import gospeak.core.services.storage.DbConf
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.dynamics
import scala.util.control.NonFatal

object DoobieUtils {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def transactor(conf: DbConf): doobie.Transactor[IO] = conf match {
    case c: DbConf.H2 => Transactor.fromDriverManager[IO]("org.h2.Driver", c.url, "", "")
    case c: DbConf.PostgreSQL => Transactor.fromDriverManager[IO]("org.postgresql.Driver", c.url, c.user, c.pass.decode)
  }

  final case class Field(name: String, prefix: String, alias: String = "") {
    def fullName: String = if (prefix.isEmpty) name else s"$prefix.$name"

    def value: String = fullName + (if (alias.isEmpty) "" else s" as $alias")

    def label: String = if (alias.isEmpty) fullName else alias
  }

  final case class CustomField(formula: Fragment, name: String) {
    val value: Fragment = formula ++ const0(s" as $name")
  }

  final case class AggregateField(formula: String, name: String) {
    def value: String = s"$formula as $name"
  }

  final case class TableJoin(kind: String,
                             name: String,
                             prefix: String,
                             on: List[(Field, Field)],
                             where: Option[Fragment]) {
    def value: Fragment = {
      val join = on.map { case (l, r) => const0(s"${l.value}=${r.value}") } ++ where.toList
      const0(s"$kind $name $prefix ON ") ++ join.reduce(_ ++ fr0" AND " ++ _)
    }
  }

  final case class Sort(key: String, label: String, fields: NonEmptyList[Field]) {
    def is(value: String): Boolean = key == value.stripPrefix("-")

    def keyDesc: String = s"-$key"
  }

  object Sort {
    def apply(key: String, fields: NonEmptyList[Field]): Sort = new Sort(key, key, fields)

    def apply(key: String, field: Field, others: Field*): Sort = new Sort(key, key, NonEmptyList.of(field, others: _*))

    def apply(key: String, label: String, field: Field, others: Field*): Sort = new Sort(key, label, NonEmptyList.of(field, others: _*))

    def apply(field: String, prefix: String): Sort = new Sort(field, field, NonEmptyList.of(Field(field, prefix)))
  }

  final case class Sorts(sorts: NonEmptyList[Sort]) {
    def default: NonEmptyList[Field] = sorts.head.fields

    def get(orderBy: Option[Page.OrderBy], prefix: String): NonEmptyList[Field] = {
      orderBy.map { o =>
        o.values.flatMap { sort =>
          sorts.find(_.is(sort)).map(_.fields.map { field =>
            (sort.startsWith("-"), field.name.startsWith("-")) match {
              case (true, true) => field.copy(name = field.name.stripPrefix("-"))
              case (true, false) => field.copy(name = "-" + field.name)
              case (false, _) => field
            }
          }).getOrElse(NonEmptyList.of(Field(sort, if (sort.contains(".")) "" else prefix)))
        }
      }.getOrElse(sorts.head.fields)
    }

    def toList: List[Sort] = sorts.toList
  }

  object Sorts {
    def apply(first: Sort, others: Sort*): Sorts = Sorts(NonEmptyList.of(first, others: _*))

    def apply(key: String, field: Field, others: Field*): Sorts = Sorts(Sort(key, NonEmptyList.of(field, others: _*)))

    def apply(key: String, label: String, field: Field, others: Field*): Sorts = Sorts(Sort(key, label, NonEmptyList.of(field, others: _*)))

    def apply(field: String, prefix: String): Sorts = Sorts(Sort(field, prefix))
  }

  sealed trait Filter {
    val key: String
    val label: String
    val aggregation: Boolean

    def filter(value: String)(implicit ctx: BasicCtx): Option[Fragment]
  }

  object Filter {

    final case class Bool(key: String, label: String, aggregation: Boolean, onTrue: BasicCtx => Fragment, onFalse: BasicCtx => Fragment) extends Filter {
      override def filter(value: String)(implicit ctx: BasicCtx): Option[Fragment] = value match {
        case "true" => Some(onTrue(ctx))
        case "false" => Some(onFalse(ctx))
        case _ => None
      }
    }

    object Bool {
      def fromNullable(key: String, label: String, field: String, aggregation: Boolean = false): Bool =
        new Bool(key, label, aggregation, onTrue = _ => const0(s"$field IS NOT NULL"), onFalse = _ => const0(s"$field IS NULL"))

      def fromCount(key: String, label: String, field: String): Bool =
        fromCountExpr(key, label, s"COALESCE(COUNT(DISTINCT $field), 0)")

      def fromCountExpr(key: String, label: String, expression: String): Bool =
        new Bool(key, label, aggregation = true, onTrue = _ => const0(s"$expression > 0"), onFalse = _ => const0(s"$expression = 0"))

      def fromNow(key: String, label: String, startField: String, endField: String, aggregation: Boolean = false): Bool =
        new Filter.Bool(key, label, aggregation, onTrue = ctx => const0(startField) ++ fr0" < ${ctx.now} AND ${ctx.now} < " ++ const0(endField), onFalse = ctx => fr0"${ctx.now}" ++ const0(s" < $startField OR $endField < ") ++ fr0"${ctx.now}")
    }

    final case class Enum(key: String, label: String, aggregation: Boolean, values: Seq[(String, BasicCtx => Fragment)]) extends Filter {
      override def filter(value: String)(implicit ctx: BasicCtx): Option[Fragment] = values.find(_._1 == value).map(_._2(ctx))
    }

    object Enum {
      def fromEnum(key: String, label: String, field: String, values: Seq[(String, String)], aggregation: Boolean = false): Enum =
        new Enum(key, label, aggregation, values.map { case (paramValue, fieldValue) => (paramValue, (_: BasicCtx) => const0(s"$field='$fieldValue'")) })
    }

    final case class Value(key: String, label: String, aggregation: Boolean, f: String => Fragment) extends Filter {
      override def filter(value: String)(implicit ctx: BasicCtx): Option[Fragment] = Some(f(value))
    }

    object Value {
      def fromField(key: String, label: String, field: String, aggregation: Boolean = false): Value =
        new Value(key, label, aggregation, v => v.toLowerCase
          .split(",")
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(f => const0(s"LOWER($field) LIKE ") ++ fr0"${"%" + f + "%"}")
          .reduce((acc, cur) => acc ++ fr0" AND " ++ cur))
    }

  }

  final case class Table(name: String,
                         prefix: String,
                         joins: Seq[TableJoin],
                         fields: Seq[Field],
                         aggFields: Seq[AggregateField],
                         customFields: Seq[CustomField],
                         sorts: Sorts,
                         search: Seq[Field],
                         filters: Seq[Filter]) extends Dynamic {
    def value: Fragment = const0(s"$name $prefix") ++ joins.foldLeft(fr0"")(_ ++ fr0" " ++ _.value)

    def setPrefix(value: String): Table = copy(
      prefix = value,
      fields = fields.map(_.copy(prefix = value)),
      search = search.map(_.copy(prefix = value)))

    private def field(field: Field): Either[CustomException, Field] = fields.find(_ == field).toEither(CustomException(s"Unable to find field '${field.value}' in table '${value.query.sql}'"))

    def field(name: String, prefix: String): Either[CustomException, Field] = field(Field(name, prefix))

    def field(name: String): Either[CustomException, Field] =
      fields.filter(_.name == name) match {
        case Seq() => Left(CustomException(s"Unable to find field '$name' in table '${value.query.sql}'"))
        case Seq(f) => Right(f)
        case l => Left(CustomException(s"Ambiguous field '$name' (possible values: ${l.map(f => s"'${f.value}'").mkString(", ")}) in table '${value.query.sql}'"))
      }

    def selectDynamic(name: String): Either[CustomException, Field] = field(name)

    def applyDynamic(name: String)(prefix: String): Either[CustomException, Field] = field(name, prefix)

    def addField(f: CustomField): Table = copy(customFields = customFields :+ f)

    def dropFields(p: Field => Boolean): Table = copy(fields = fields.filterNot(p), search = search.filterNot(p))

    def dropField(field: Field): Either[CustomException, Table] =
      this.field(field).map(_ => dropFields(f => f.name == field.name && f.prefix == field.prefix))

    def dropField(f: Table => Either[CustomException, Field]): Either[CustomException, Table] = f(this).flatMap(dropField)

    def join(rightTable: Table, on: Table.BuildJoinFields*): Either[CustomException, Table] =
      doJoin("INNER JOIN", rightTable, on.toList, None)

    def join(rightTable: Table, where: Fragment, on: Table.BuildJoinFields*): Either[CustomException, Table] =
      doJoin("INNER JOIN", rightTable, on.toList, Some(where))

    def joinOpt(rightTable: Table, on: Table.BuildJoinFields*): Either[CustomException, Table] =
      doJoin("LEFT OUTER JOIN", rightTable, on.toList, None)

    def joinOpt(rightTable: Table, where: Fragment, on: Table.BuildJoinFields*): Either[CustomException, Table] =
      doJoin("LEFT OUTER JOIN", rightTable, on.toList, Some(where))

    private def doJoin(kind: String, rightTable: Table, on: List[Table.BuildJoinFields], where: Option[Fragment]): Either[CustomException, Table] = for {
      joinFields <- on
        .map(f => f(this, rightTable))
        .map { case (leftField, rightField) => leftField.flatMap(lf => rightField.map(rf => (lf, rf))) }.sequence
      res <- Table.from(
        name = name,
        prefix = prefix,
        joins = joins ++ Seq(TableJoin(kind, rightTable.name, rightTable.prefix, joinFields, where)) ++ rightTable.joins,
        fields = fields ++ rightTable.fields,
        aggFields = aggFields ++ rightTable.aggFields,
        customFields = customFields ++ rightTable.customFields,
        sorts = sorts,
        search = search ++ rightTable.search,
        filters = filters ++ rightTable.filters)
    } yield res

    def aggregate(formula: String, name: String): Table = copy(aggFields = aggFields :+ AggregateField(formula, name))

    def setSorts(first: Sort, others: Sort*): Table = copy(sorts = Sorts(first, others: _*))

    def insert[A](elt: A, build: A => Fragment): Insert[A] = Insert[A](name, fields, elt, build)

    def insertPartial[A](fields: Seq[Field], elt: A, build: A => Fragment): Insert[A] = Insert[A](name, fields, elt, build)

    def update(fields: Fragment, where: Fragment): Update = Update(value, fields, fr0" " ++ where)

    def delete(where: Fragment): Delete = Delete(value, fr0" " ++ where)

    def select[A: Read](): Select[A] = Select[A](value, fields, aggFields, customFields, None, sorts, None)

    def select[A: Read](where: Fragment): Select[A] = Select[A](value, fields, aggFields, customFields, Some(fr0" " ++ where), sorts, None)

    def select[A: Read](where: Fragment, sort: Sort): Select[A] = Select[A](value, fields, aggFields, customFields, Some(fr0" " ++ where), Sorts(sort), None)

    def select[A: Read](fields: Seq[Field]): Select[A] = Select[A](value, fields, aggFields, customFields, None, sorts, None)

    def select[A: Read](fields: Seq[Field], sort: Sort): Select[A] = Select[A](value, fields, aggFields, customFields, None, Sorts(sort), None)

    def select[A: Read](fields: Seq[Field], where: Fragment): Select[A] = Select[A](value, fields, aggFields, customFields, Some(fr0" " ++ where), sorts, None)

    def select[A: Read](fields: Seq[Field], where: Fragment, sort: Sort): Select[A] = Select[A](value, fields, aggFields, customFields, Some(fr0" " ++ where), Sorts(sort), None)

    def selectOne[A: Read](where: Fragment): Select[A] = Select[A](value, fields, aggFields, customFields, Some(fr0" " ++ where), sorts, Some(1))

    def selectOne[A: Read](where: Fragment, sort: Sort): Select[A] = Select[A](value, fields, aggFields, customFields, Some(fr0" " ++ where), Sorts(sort), Some(1))

    def selectOne[A: Read](fields: Seq[Field], where: Fragment): Select[A] = Select[A](value, fields, aggFields, customFields, Some(fr0" " ++ where), sorts, Some(1))

    def selectOne[A: Read](fields: Seq[Field], where: Fragment, sort: Sort): Select[A] = Select[A](value, fields, aggFields, customFields, Some(fr0" " ++ where), Sorts(sort), Some(1))

    def selectPage[A: Read, C <: BasicCtx](params: Page.Params)(implicit ctx: C): SelectPage[A, C] = SelectPage[A, C](value, prefix, fields, aggFields, customFields, None, None, params, sorts, search, filters, ctx)

    def selectPage[A: Read, C <: BasicCtx](params: Page.Params, where: Fragment)(implicit ctx: C): SelectPage[A, C] = SelectPage[A, C](value, prefix, fields, aggFields, customFields, Some(fr0" " ++ where), None, params, sorts, search, filters, ctx)

    def selectPage[A: Read, C <: BasicCtx](params: Page.Params, where: Fragment, having: Fragment)(implicit ctx: C): SelectPage[A, C] = SelectPage[A, C](value, prefix, fields, aggFields, customFields, Some(fr0" " ++ where), Some(fr0" " ++ having), params, sorts, search, filters, ctx)

    def selectPage[A: Read, C <: BasicCtx](fields: Seq[Field], params: Page.Params)(implicit ctx: C): SelectPage[A, C] = SelectPage[A, C](value, prefix, fields, aggFields, customFields, None, None, params, sorts, search, filters, ctx)

    def selectPage[A: Read, C <: BasicCtx](fields: Seq[Field], params: Page.Params, where: Fragment)(implicit ctx: C): SelectPage[A, C] = SelectPage[A, C](value, prefix, fields, aggFields, customFields, Some(fr0" " ++ where), None, params, sorts, search, filters, ctx)
  }

  object Table {
    private type BuildJoinFields = (Table, Table) => (Either[CustomException, Field], Either[CustomException, Field])

    def from(name: String, prefix: String, fields: Seq[String], sort: Sort, search: Seq[String], filters: Seq[Filter]): Either[CustomException, Table] =
      from(
        name = name,
        prefix = prefix,
        joins = Seq(),
        fields = fields.map(f => Field(f, prefix)),
        customFields = Seq(),
        aggFields = Seq(),
        sorts = Sorts(sort),
        search = search.map(f => Field(f, prefix)),
        filters = filters)

    private def from(name: String, prefix: String, joins: Seq[TableJoin], fields: Seq[Field], customFields: Seq[CustomField], aggFields: Seq[AggregateField], sorts: Sorts, search: Seq[Field], filters: Seq[Filter]): Either[CustomException, Table] = {
      val duplicateFields = fields.diff(fields.distinct).distinct
      // val invalidSort = sorts.all.flatten.map(s => s.copy(name = s.name.stripPrefix("-")).value).diff(fields.map(_.value) ++ aggFields.map(_.formula))
      val invalidSearch = search.diff(fields)
      val errors = Seq(
        duplicateFields.headOption.map(_ => s"fields ${duplicateFields.map(s"'" + _.value + "'").mkString(", ")} are duplicated"),
        // invalidSort.headOption.map(_ => s"sorts ${invalidSort.map(s"'" + _ + "'").mkString(", ")} do not exist in fields"),
        invalidSearch.headOption.map(_ => s"searches ${invalidSearch.map(s"'" + _.value + "'").mkString(", ")} do not exist in fields")
      ).flatten.map(CustomError)
      errors.headOption.map(_ => CustomException("Invalid Table", errors)).map(Left(_)).getOrElse(Right(new Table(
        name = name,
        prefix = prefix,
        joins = joins,
        fields = fields,
        customFields = customFields,
        aggFields = aggFields,
        sorts = sorts,
        search = search,
        filters = filters)))
    }
  }

  final case class Insert[A](table: String, fields: Seq[Field], elt: A, build: A => Fragment) {
    def fr: Fragment = const0(s"INSERT INTO $table (${fields.map(_.name).mkString(", ")}) VALUES (") ++ build(elt) ++ fr0")"

    def run(xa: doobie.Transactor[IO]): IO[A] =
      exec(fr, _.update.run, xa).flatMap {
        case 1 => IO.pure(elt)
        case code => IO.raiseError(CustomException(s"Failed to insert $elt in $table (code: $code)"))
      }
  }

  final case class Update(table: Fragment, fields: Fragment, where: Fragment) {
    def fr: Fragment = fr0"UPDATE " ++ table ++ fr0" SET " ++ fields ++ where

    def run(xa: doobie.Transactor[IO]): IO[Done] =
      exec(fr, _.update.run, xa).flatMap {
        case 1 => IO.pure(Done)
        case code => IO.raiseError(CustomException(s"Failed to update ${fr.update} (code: $code)"))
      }
  }

  final case class Delete(table: Fragment, where: Fragment) {
    def fr: Fragment = fr0"DELETE FROM " ++ table ++ where

    def run(xa: doobie.Transactor[IO]): IO[Done] =
      exec(fr, _.update.run, xa).flatMap {
        case 1 => IO.pure(Done)
        case code => IO.raiseError(CustomException(s"Failed to delete ${fr.update} (code: $code)"))
      }
  }

  final case class Select[A: Read](table: Fragment,
                                   fields: Seq[Field],
                                   aggFields: Seq[AggregateField],
                                   customFields: Seq[CustomField],
                                   whereOpt: Option[Fragment],
                                   sorts: Sorts,
                                   limit: Option[Int]) {
    def fr: Fragment = {
      val select = const0(s"SELECT ${(fields.map(_.value) ++ aggFields.map(_.value)).mkString(", ")}") ++ customFields.map(fr0", " ++ _.value).foldLeft(fr0"")(_ ++ _) ++ fr0" FROM " ++ table
      val where = whereOpt.getOrElse(fr0"")
      val groupBy = aggFields.headOption.map(_ => const0(s" GROUP BY ${fields.map(_.label).mkString(", ")}")).getOrElse(fr0"")
      val orderBy = orderByFragment(sorts.default)
      select ++ where ++ groupBy ++ orderBy ++ limit.map(l => const0(s" LIMIT $l")).getOrElse(fr0"")
    }

    def query: doobie.Query0[A] = fr.query[A]

    def runList(xa: doobie.Transactor[IO]): IO[List[A]] = exec(fr, _.query[A].to[List], xa)

    def runOption(xa: doobie.Transactor[IO]): IO[Option[A]] = exec(fr, _.query[A].option, xa)

    def runUnique(xa: doobie.Transactor[IO]): IO[A] = exec(fr, _.query[A].unique, xa)

    def runExists(xa: doobie.Transactor[IO]): IO[Boolean] = exec(fr, _.query[A].option.map(_.isDefined), xa)
  }

  final case class SelectPage[A: Read, C <: BasicCtx](table: Fragment,
                                                      prefix: String,
                                                      fields: Seq[Field],
                                                      aggFields: Seq[AggregateField],
                                                      customFields: Seq[CustomField],
                                                      whereOpt: Option[Fragment],
                                                      havingOpt: Option[Fragment],
                                                      params: Page.Params,
                                                      sorts: Sorts,
                                                      searchFields: Seq[Field],
                                                      filters: Seq[Filter],
                                                      ctx: C) {
    private val select: Fragment = const0(s"SELECT ${(fields.map(_.value) ++ aggFields.map(_.value)).mkString(", ")}") ++ customFields.map(fr0", " ++ _.value).foldLeft(fr0"")(_ ++ _) ++ fr0" FROM " ++ table
    private val groupBy: Fragment = aggFields.headOption.map(_ => const0(s" GROUP BY ${fields.map(_.label).mkString(", ")}")).getOrElse(fr0"")
    private val having: Fragment = filterClause(havingOpt, aggregation = true).getOrElse(fr0"")

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

  def whereFragment(whereOpt: Option[Fragment], search: Option[Page.Search], fields: Seq[Field]): Option[Fragment] = {
    search.filter(_ => fields.nonEmpty)
      .map { search => fields.map(s => const0(s.value + " ") ++ fr0"ILIKE ${"%" + search.value + "%"}").reduce(_ ++ fr0" OR " ++ _) }
      .map { search => whereOpt.map(_ ++ fr0" AND " ++ fr0"(" ++ search ++ fr0")").getOrElse(fr0" WHERE " ++ search) }
      .orElse(whereOpt)
  }

  def orderByFragment(sort: NonEmptyList[Field], nullsFirst: Boolean = false): Fragment = {
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

  def limitFragment(size: Page.Size, start: Page.Offset): Fragment =
    fr0" LIMIT " ++ const0(size.value.toString) ++ fr0" OFFSET " ++ const0(start.value.toString)

  def paginationFragment(prefix: String, whereOpt: Option[Fragment], params: Page.Params, sorts: Sorts, searchFields: Seq[Field]): (Fragment, Fragment, Fragment) = {
    val where = whereFragment(whereOpt, params.search, searchFields).getOrElse(fr0"")
    val sortFields = sorts.get(params.orderBy, prefix)
    val orderBy = orderByFragment(sortFields, params.nullsFirst)
    val limit = limitFragment(params.pageSize, params.offset)
    (where, orderBy, limit)
  }

  object Mappings {

    import scala.reflect.runtime.universe._

    implicit val envMeta: Meta[ApplicationConf.Env] = Meta[String].timap(ApplicationConf.Env.from(_).get)(_.value)
    implicit val timePeriodMeta: Meta[TimePeriod] = Meta[String].timap(TimePeriod.from(_).get)(_.value)
    implicit val finiteDurationMeta: Meta[FiniteDuration] = Meta[Long].timap(Duration.fromNanos)(_.toNanos)
    implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Instant].timap(LocalDateTime.ofInstant(_, ZoneOffset.UTC))(_.toInstant(ZoneOffset.UTC))
    implicit val emailAddressMeta: Meta[EmailAddress] = Meta[String].timap(EmailAddress.from(_).get)(_.value)
    implicit val urlMeta: Meta[Url] = Meta[String].timap(Url.from(_).get)(_.value)
    implicit val urlVideoMeta: Meta[Url.Video] = Meta[String].timap(Url.Video.from(_).get)(_.value)
    implicit val urlVideosMeta: Meta[Url.Videos] = Meta[String].timap(Url.Videos.from(_).get)(_.value)
    implicit val twitterUrlMeta: Meta[Url.Twitter] = Meta[String].timap(Url.Twitter.from(_).get)(_.value)
    implicit val linkedInUrlMeta: Meta[Url.LinkedIn] = Meta[String].timap(Url.LinkedIn.from(_).get)(_.value)
    implicit val youTubeUrlMeta: Meta[Url.YouTube] = Meta[String].timap(Url.YouTube.from(_).get)(_.value)
    implicit val meetupUrlMeta: Meta[Url.Meetup] = Meta[String].timap(Url.Meetup.from(_).get)(_.value)
    implicit val githubUrlMeta: Meta[Url.Github] = Meta[String].timap(Url.Github.from(_).get)(_.value)
    implicit val logoMeta: Meta[Logo] = urlMeta.timap(Logo)(_.url)
    implicit val bannerMeta: Meta[Banner] = urlMeta.timap(Banner)(_.url)
    implicit val facebookAccountMeta: Meta[FacebookAccount] = urlMeta.timap(FacebookAccount)(_.url)
    implicit val instagramAccountMeta: Meta[InstagramAccount] = urlMeta.timap(InstagramAccount)(_.url)
    implicit val twitterAccountMeta: Meta[TwitterAccount] = twitterUrlMeta.timap(TwitterAccount)(_.url)
    implicit val linkedInAccountMeta: Meta[LinkedInAccount] = linkedInUrlMeta.timap(LinkedInAccount)(_.url)
    implicit val youtubeAccountMeta: Meta[YoutubeAccount] = youTubeUrlMeta.timap(YoutubeAccount)(_.url)
    implicit val meetupAccountMeta: Meta[MeetupAccount] = meetupUrlMeta.timap(MeetupAccount)(_.url)
    implicit val eventbriteAccountMeta: Meta[EventbriteAccount] = urlMeta.timap(EventbriteAccount)(_.url)
    implicit val slackAccountMeta: Meta[SlackAccount] = urlMeta.timap(SlackAccount)(_.url)
    implicit val discordAccountMeta: Meta[DiscordAccount] = urlMeta.timap(DiscordAccount)(_.url)
    implicit val githubAccountMeta: Meta[GithubAccount] = githubUrlMeta.timap(GithubAccount)(_.url)
    implicit val slidesUrlMeta: Meta[SlidesUrl] = urlMeta.timap(SlidesUrl.from(_).get)(_.url)
    implicit val videoUrlMeta: Meta[VideoUrl] = urlMeta.timap(VideoUrl.from(_).get)(_.url)
    implicit val twitterHashtagMeta: Meta[TwitterHashtag] = Meta[String].timap(TwitterHashtag.from(_).get)(_.value)
    implicit val currencyMeta: Meta[Price.Currency] = Meta[String].timap(Price.Currency.from(_).get)(_.value)
    implicit val markdownMeta: Meta[Markdown] = Meta[String].timap(Markdown(_))(_.value)

    implicit def mustacheMarkdownMeta[A: TypeTag]: Meta[Mustache.Markdown[A]] = Meta[String].timap(Mustache.Markdown[A])(_.value)

    implicit def mustacheTextMeta[A: TypeTag]: Meta[Mustache.Text[A]] = Meta[String].timap(Mustache.Text[A])(_.value)

    implicit val tagsMeta: Meta[Seq[Tag]] = Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Tag(_)).toSeq)(_.map(_.value).mkString(","))
    implicit val gMapPlaceMeta: Meta[GMapPlace] = {
      implicit val geoDecoder: Decoder[Geo] = deriveDecoder[Geo]
      implicit val geoEncoder: Encoder[Geo] = deriveEncoder[Geo]
      implicit val gMapPlaceDecoder: Decoder[GMapPlace] = deriveDecoder[GMapPlace]
      implicit val gMapPlaceEncoder: Encoder[GMapPlace] = deriveEncoder[GMapPlace]
      Meta[String].timap(fromJson[GMapPlace](_).get)(toJson)
    }
    implicit val groupSettingsEventTemplatesMeta: Meta[Map[String, Mustache.Text[Message.EventInfo]]] = {
      implicit val textTemplateDecoder: Decoder[Mustache.Text[Message.EventInfo]] = deriveDecoder[Mustache.Text[Message.EventInfo]]
      implicit val textTemplateEncoder: Encoder[Mustache.Text[Message.EventInfo]] = deriveEncoder[Mustache.Text[Message.EventInfo]]
      Meta[String].timap(fromJson[Map[String, Mustache.Text[Message.EventInfo]]](_).get)(toJson)
    }
    implicit val groupSettingsActionsMeta: Meta[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]] = {
      implicit val mustacheTextDecoder: Decoder[Mustache.Text[Any]] = deriveDecoder[Mustache.Text[Any]]
      implicit val mustacheTextEncoder: Encoder[Mustache.Text[Any]] = deriveEncoder[Mustache.Text[Any]]
      implicit val mustacheMarkdownDecoder: Decoder[Mustache.Markdown[Any]] = deriveDecoder[Mustache.Markdown[Any]]
      implicit val mustacheMarkdownEncoder: Encoder[Mustache.Markdown[Any]] = deriveEncoder[Mustache.Markdown[Any]]
      implicit val slackActionPostMessageDecoder: Decoder[SlackAction.PostMessage] = deriveDecoder[SlackAction.PostMessage]
      implicit val slackActionPostMessageEncoder: Encoder[SlackAction.PostMessage] = deriveEncoder[SlackAction.PostMessage]
      implicit val slackActionDecoder: Decoder[SlackAction] = deriveDecoder[SlackAction]
      implicit val slackActionEncoder: Encoder[SlackAction] = deriveEncoder[SlackAction]
      implicit val groupSettingsActionEmailDecoder: Decoder[Group.Settings.Action.Email] = deriveDecoder[Group.Settings.Action.Email]
      implicit val groupSettingsActionEmailEncoder: Encoder[Group.Settings.Action.Email] = deriveEncoder[Group.Settings.Action.Email]
      implicit val groupSettingsActionSlackDecoder: Decoder[Group.Settings.Action.Slack] = deriveDecoder[Group.Settings.Action.Slack]
      implicit val groupSettingsActionSlackEncoder: Encoder[Group.Settings.Action.Slack] = deriveEncoder[Group.Settings.Action.Slack]
      implicit val groupSettingsActionDecoder: Decoder[Group.Settings.Action] = deriveDecoder[Group.Settings.Action]
      implicit val groupSettingsActionEncoder: Encoder[Group.Settings.Action] = deriveEncoder[Group.Settings.Action]
      implicit val groupSettingsActionTriggerDecoder: KeyDecoder[Group.Settings.Action.Trigger] = (key: String) => Group.Settings.Action.Trigger.from(key).toOption
      implicit val groupSettingsActionTriggerEncoder: KeyEncoder[Group.Settings.Action.Trigger] = (e: Group.Settings.Action.Trigger) => e.toString
      Meta[String].timap(fromJson[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]](_).get)(toJson)
    }

    // TODO build Meta[Seq[A]] and Meta[NonEmptyList[A]]
    // implicit def seqMeta[A](implicit m: Meta[A]): Meta[Seq[A]] = ???
    // implicit def nelMeta[A](implicit m: Meta[A]): Meta[NonEmptyList[A]] = ???

    implicit val userIdMeta: Meta[User.Id] = Meta[String].timap(User.Id.from(_).get)(_.value)
    implicit val userSlugMeta: Meta[User.Slug] = Meta[String].timap(User.Slug.from(_).get)(_.value)
    implicit val userStatusMeta: Meta[User.Status] = Meta[String].timap(User.Status.from(_).get)(_.value)
    implicit val userRequestIdMeta: Meta[UserRequest.Id] = Meta[String].timap(UserRequest.Id.from(_).get)(_.value)
    implicit val talkIdMeta: Meta[Talk.Id] = Meta[String].timap(Talk.Id.from(_).get)(_.value)
    implicit val talkSlugMeta: Meta[Talk.Slug] = Meta[String].timap(Talk.Slug.from(_).get)(_.value)
    implicit val talkTitleMeta: Meta[Talk.Title] = Meta[String].timap(Talk.Title)(_.value)
    implicit val talkStatusMeta: Meta[Talk.Status] = Meta[String].timap(Talk.Status.from(_).get)(_.value)
    implicit val groupIdMeta: Meta[Group.Id] = Meta[String].timap(Group.Id.from(_).get)(_.value)
    implicit val groupSlugMeta: Meta[Group.Slug] = Meta[String].timap(Group.Slug.from(_).get)(_.value)
    implicit val groupStatusMeta: Meta[Group.Status] = Meta[String].timap(Group.Status.from(_).get)(_.value)
    implicit val groupNameMeta: Meta[Group.Name] = Meta[String].timap(Group.Name)(_.value)
    implicit val eventIdMeta: Meta[Event.Id] = Meta[String].timap(Event.Id.from(_).get)(_.value)
    implicit val eventSlugMeta: Meta[Event.Slug] = Meta[String].timap(Event.Slug.from(_).get)(_.value)
    implicit val eventNameMeta: Meta[Event.Name] = Meta[String].timap(Event.Name)(_.value)
    implicit val eventKindMeta: Meta[Event.Kind] = Meta[String].timap(Event.Kind.from(_).get)(_.value)
    implicit val cfpIdMeta: Meta[Cfp.Id] = Meta[String].timap(Cfp.Id.from(_).get)(_.value)
    implicit val cfpSlugMeta: Meta[Cfp.Slug] = Meta[String].timap(Cfp.Slug.from(_).get)(_.value)
    implicit val cfpNameMeta: Meta[Cfp.Name] = Meta[String].timap(Cfp.Name)(_.value)
    implicit val proposalIdMeta: Meta[Proposal.Id] = Meta[String].timap(Proposal.Id.from(_).get)(_.value)
    implicit val proposalStatusMeta: Meta[Proposal.Status] = Meta[String].timap(Proposal.Status.from(_).get)(_.value)
    implicit val partnerIdMeta: Meta[Partner.Id] = Meta[String].timap(Partner.Id.from(_).get)(_.value)
    implicit val partnerSlugMeta: Meta[Partner.Slug] = Meta[String].timap(Partner.Slug.from(_).get)(_.value)
    implicit val partnerNameMeta: Meta[Partner.Name] = Meta[String].timap(Partner.Name)(_.value)
    implicit val venueIdMeta: Meta[Venue.Id] = Meta[String].timap(Venue.Id.from(_).get)(_.value)
    implicit val sponsorPackIdMeta: Meta[SponsorPack.Id] = Meta[String].timap(SponsorPack.Id.from(_).get)(_.value)
    implicit val sponsorPackSlugMeta: Meta[SponsorPack.Slug] = Meta[String].timap(SponsorPack.Slug.from(_).get)(_.value)
    implicit val sponsorPackNameMeta: Meta[SponsorPack.Name] = Meta[String].timap(SponsorPack.Name)(_.value)
    implicit val sponsorIdMeta: Meta[Sponsor.Id] = Meta[String].timap(Sponsor.Id.from(_).get)(_.value)
    implicit val contactIdMeta: Meta[Contact.Id] = Meta[String].timap(Contact.Id.from(_).right.get)(_.value)
    implicit val commentIdMeta: Meta[Comment.Id] = Meta[String].timap(Comment.Id.from(_).get)(_.value)
    implicit val commentKindMeta: Meta[Comment.Kind] = Meta[String].timap(Comment.Kind.from(_).get)(_.value)
    implicit val meetupGroupSlugMeta: Meta[MeetupGroup.Slug] = Meta[String].timap(MeetupGroup.Slug.from(_).get)(_.value)
    implicit val meetupEventIdMeta: Meta[MeetupEvent.Id] = Meta[Long].timap(MeetupEvent.Id(_))(_.value)
    implicit val memberRoleMeta: Meta[Group.Member.Role] = Meta[String].timap(Group.Member.Role.from(_).get)(_.value)
    implicit val rsvpAnswerMeta: Meta[Event.Rsvp.Answer] = Meta[String].timap(Event.Rsvp.Answer.from(_).get)(_.value)
    implicit val externalEventIdMeta: Meta[ExternalEvent.Id] = Meta[String].timap(ExternalEvent.Id.from(_).get)(_.value)
    implicit val externalCfpIdMeta: Meta[ExternalCfp.Id] = Meta[String].timap(ExternalCfp.Id.from(_).get)(_.value)
    implicit val externalProposalIdMeta: Meta[ExternalProposal.Id] = Meta[String].timap(ExternalProposal.Id.from(_).get)(_.value)
    implicit val voteMeta: Meta[Proposal.Rating.Grade] = Meta[Int].timap(Proposal.Rating.Grade.from(_).get)(_.value)
    implicit val videoIdMeta: Meta[Video.Id] = Meta[String].timap(Video.Id.from(_).get)(_.value)

    implicit val userIdNelMeta: Meta[NonEmptyList[User.Id]] = Meta[String].timap(
      _.split(",").filter(_.nonEmpty).map(User.Id.from(_).get).toNelUnsafe)(
      _.map(_.value).toList.mkString(","))
    implicit val proposalIdSeqMeta: Meta[Seq[Proposal.Id]] = Meta[String].timap(
      _.split(",").filter(_.nonEmpty).map(Proposal.Id.from(_).get).toSeq)(
      _.map(_.value).mkString(","))

    implicit def eitherRead[A, B](implicit r: Read[(Option[A], Option[B])]): Read[Either[A, B]] = Read[(Option[A], Option[B])].map {
      case (Some(a), None) => Left(a)
      case (None, Some(b)) => Right(b)
      case (None, None) => throw new Exception(s"Unable to read Either, no side is defined")
      case (Some(a), Some(b)) => throw new Exception(s"Unable to read Either, both sides are defined: Left($a) / Right($b)")
    }

    implicit val userRequestRead: Read[UserRequest] =
      Read[(UserRequest.Id, String, Option[Group.Id], Option[Cfp.Id], Option[Event.Id], Option[Talk.Id], Option[Proposal.Id], Option[ExternalEvent.Id], Option[ExternalCfp.Id], Option[ExternalProposal.Id], Option[EmailAddress], Option[String], Instant, Instant, Option[User.Id], Option[Instant], Option[User.Id], Option[Instant], Option[User.Id], Option[Instant], Option[User.Id])].map {
        case (id, "AccountValidation", _, _, _, _, _, _, _, _, Some(email), _, deadline, created, Some(createdBy), accepted, _, _, _, _, _) =>
          UserRequest.AccountValidationRequest(id, email, deadline, created, createdBy, accepted)
        case (id, "PasswordReset", _, _, _, _, _, _, _, _, Some(email), _, deadline, created, _, accepted, _, _, _, _, _) =>
          UserRequest.PasswordResetRequest(id, email, deadline, created, accepted)
        case (id, "UserAskToJoinAGroup", Some(groupId), _, _, _, _, _, _, _, _, _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.UserAskToJoinAGroupRequest(id, groupId, deadline, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "GroupInvite", Some(groupId), _, _, _, _, _, _, _, Some(email), _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.GroupInvite(id, groupId, email, deadline, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "TalkInvite", _, _, _, Some(talkId), _, _, _, _, Some(email), _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.TalkInvite(id, talkId, email, deadline, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "ProposalInvite", _, _, _, _, Some(proposalId), _, _, _, Some(email), _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.ProposalInvite(id, proposalId, email, deadline, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "ExternalProposalInvite", _, _, _, _, _, _, _, Some(externalProposalId), Some(email), _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.ExternalProposalInvite(id, externalProposalId, email, deadline, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, kind, group, cfp, event, talk, proposal, extEvent, extCfp, extProposal, email, payload, deadline, created, createdBy, accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          throw new Exception(s"Unable to read UserRequest with ($id, $kind, group=$group, cfp=$cfp, event=$event, talk=$talk, proposal=$proposal, extEvent=$extEvent, extCfp=$extCfp, extProposal=$extProposal, $email, payload=$payload, $deadline, created=($created, $createdBy), accepted=($accepted, $acceptedBy), rejected=($rejected, $rejectedBy), canceled=($canceled, $canceledBy))")
      }

    private def toJson[A](v: A)(implicit e: Encoder[A]): String = e.apply(v).noSpaces

    private def fromJson[A](s: String)(implicit d: Decoder[A]): util.Try[A] = parser.parse(s).flatMap(d.decodeJson).toTry
  }

}
