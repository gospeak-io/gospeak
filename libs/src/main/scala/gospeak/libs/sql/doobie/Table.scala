package gospeak.libs.sql.doobie

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.Read
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomError, CustomException, Page}

import scala.language.dynamics

final case class Table(name: String,
                       prefix: String,
                       joins: List[Table.Join],
                       fields: List[Field],
                       aggFields: List[AggregateField],
                       customFields: List[CustomField],
                       sorts: Table.Sorts,
                       search: List[Field],
                       filters: List[Table.Filter]) extends Dynamic {
  def value: Fragment = const0(s"$name $prefix") ++ joins.foldLeft(fr0"")(_ ++ fr0" " ++ _.value)

  def setPrefix(value: String): Table = copy(
    prefix = value,
    fields = fields.map(_.copy(prefix = value)),
    search = search.map(_.copy(prefix = value)))

  private def field(field: Field): Either[CustomException, Field] = fields.find(_ == field).toEither(CustomException(s"Unable to find field '${field.value}' in table '${value.query.sql}'"))

  def field(name: String, prefix: String): Either[CustomException, Field] = field(Field(name, prefix))

  def field(name: String): Either[CustomException, Field] =
    fields.filter(_.name == name) match {
      case List() => Left(CustomException(s"Unable to find field '$name' in table '${value.query.sql}'"))
      case List(f) => Right(f)
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
      joins = joins ++ List(Table.Join(kind, rightTable.name, rightTable.prefix, joinFields, where)) ++ rightTable.joins,
      fields = fields ++ rightTable.fields,
      aggFields = aggFields ++ rightTable.aggFields,
      customFields = customFields ++ rightTable.customFields,
      sorts = sorts,
      search = search ++ rightTable.search,
      filters = filters ++ rightTable.filters)
  } yield res

  def aggregate(formula: String, name: String): Table = copy(aggFields = aggFields :+ AggregateField(formula, name))

  def setSorts(first: Table.Sort, others: Table.Sort*): Table = copy(sorts = Table.Sorts(first, others: _*))

  def filters(f: Table.Filter*): Table = copy(filters = f.toList)

  def insert[A](elt: A, build: A => Fragment): Query.Insert[A] = Query.Insert[A](name, fields, elt, build)

  def insertPartial[A](fields: List[Field], elt: A, build: A => Fragment): Query.Insert[A] = Query.Insert[A](name, fields, elt, build)

  def update(fields: Fragment): Query.Update = Query.Update(value, fields, fr0"")

  def delete: Query.Delete = Query.Delete(value, fr0"")

  def select[A: Read]: Query.Select[A] = Query.Select[A](value, fields, aggFields, customFields, None, sorts, None, None)

  def selectPage[A: Read](params: Page.Params, ctx: DbCtx): Query.SelectPage[A] = Query.SelectPage[A](value, prefix, fields, aggFields, customFields, None, None, params, sorts, search, filters, ctx)
}

object Table {
  private type BuildJoinFields = (Table, Table) => (Either[CustomException, Field], Either[CustomException, Field])

  def from(name: String, prefix: String, fields: List[String], sort: Sort, search: List[String], filters: List[Filter]): Either[CustomException, Table] =
    from(
      name = name,
      prefix = prefix,
      joins = List(),
      fields = fields.map(f => Field(f, prefix)),
      customFields = List(),
      aggFields = List(),
      sorts = Sorts(sort),
      search = search.map(f => Field(f, prefix)),
      filters = filters)

  private def from(name: String, prefix: String, joins: List[Join], fields: List[Field], customFields: List[CustomField], aggFields: List[AggregateField], sorts: Sorts, search: List[Field], filters: List[Filter]): Either[CustomException, Table] = {
    val duplicateFields = fields.diff(fields.distinct).distinct
    // val invalidSort = sorts.all.flatten.map(s => s.copy(name = s.name.stripPrefix("-")).value).diff(fields.map(_.value) ++ aggFields.map(_.formula))
    val invalidSearch = search.diff(fields)
    val errors = List(
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

  final case class Join(kind: String,
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

    def filter(value: String)(implicit ctx: DbCtx): Option[Fragment]
  }

  object Filter {

    final case class Bool(key: String, label: String, aggregation: Boolean, onTrue: DbCtx => Fragment, onFalse: DbCtx => Fragment) extends Filter {
      override def filter(value: String)(implicit ctx: DbCtx): Option[Fragment] = value match {
        case "true" => Some(onTrue(ctx))
        case "false" => Some(onFalse(ctx))
        case _ => None
      }
    }

    object Bool {
      def fromNullable(key: String, label: String, field: String, aggregation: Boolean = false): Bool =
        new Bool(key, label, aggregation, onTrue = _ => const0(s"($field IS NOT NULL)"), onFalse = _ => const0(s"($field IS NULL)"))

      def fromCount(key: String, label: String, field: String): Bool =
        fromCountExpr(key, label, s"COALESCE(COUNT(DISTINCT $field), 0)")

      def fromCountExpr(key: String, label: String, expression: String): Bool =
        new Bool(key, label, aggregation = true, onTrue = _ => const0(s"($expression > ") ++ fr0"${0})", onFalse = _ => const0(s"($expression = ") ++ fr0"${0})")

      def fromNow(key: String, label: String, startField: String, endField: String, aggregation: Boolean = false): Bool =
        new Filter.Bool(key, label, aggregation,
          onTrue = ctx => fr0"(" ++ const0(startField) ++ fr0" < ${ctx.now} AND " ++ const0(endField) ++ fr0" > ${ctx.now})",
          onFalse = ctx => fr0"(" ++ const0(startField) ++ fr0" > ${ctx.now} OR " ++ const0(endField) ++ fr0" < ${ctx.now})")
    }

    final case class Enum(key: String, label: String, aggregation: Boolean, values: List[(String, DbCtx => Fragment)]) extends Filter {
      override def filter(value: String)(implicit ctx: DbCtx): Option[Fragment] = values.find(_._1 == value).map(_._2(ctx))
    }

    object Enum {
      def fromEnum(key: String, label: String, field: String, values: List[(String, String)], aggregation: Boolean = false): Enum =
        new Enum(key, label, aggregation, values.map { case (paramValue, fieldValue) => (paramValue, (_: DbCtx) => const0(s"$field=") ++ fr0"$fieldValue") })
    }

    final case class Value(key: String, label: String, aggregation: Boolean, f: String => Fragment) extends Filter {
      override def filter(value: String)(implicit ctx: DbCtx): Option[Fragment] = Some(f(value))
    }

    object Value {
      def fromField(key: String, label: String, field: String, aggregation: Boolean = false): Value =
        new Value(key, label, aggregation, v => fr0"(" ++ v.toLowerCase
          .split(",")
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(f => const0(s"LOWER($field) LIKE ") ++ fr0"${"%" + f + "%"}")
          .reduce((acc, cur) => acc ++ fr0" AND " ++ cur) ++ fr0")")
    }

  }

}
