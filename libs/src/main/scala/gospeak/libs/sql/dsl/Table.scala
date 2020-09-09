package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.sql.dsl.Query.Inner.{GroupByClause, OrderByClause, WhereClause}
import gospeak.libs.sql.dsl.Query.{Delete, Insert, Select, Update}
import gospeak.libs.sql.dsl.Table.Inner._
import gospeak.libs.sql.dsl.Table.{Join, JoinTable, Sort}

sealed trait Table {
  def getFields: List[Field[_]]

  def getSorts: List[Sort]

  def has(f: SqlField[_, Table.SqlTable]): Boolean

  def fr: Fragment

  // TODO do not lose previous type (chain predefined joins, still use typed fields...)
  def join[T <: Table](table: T): JoinClause[this.type, T] = JoinClause(this, Join.Kind.Inner, table)

  def join[T <: Table](table: T, kind: Join.Kind.type => Join.Kind): JoinClause[this.type, T] = JoinClause(this, kind(Join.Kind), table)

  def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](ref: SqlFieldRef[A, T, T2]): JoinTable = join(ref.references.table).on(ref.is(ref.references))

  def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](ref: SqlFieldRef[A, T, T2], kind: Join.Kind.type => Join.Kind): JoinTable = join(ref.references.table, kind).on(ref.is(ref.references))

  def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](ref: SqlFieldRefOpt[A, T, T2]): JoinTable = join(ref.references.table).on(ref.isOpt(ref.references))

  def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](ref: SqlFieldRefOpt[A, T, T2], kind: Join.Kind.type => Join.Kind): JoinTable = join(ref.references.table, kind).on(ref.isOpt(ref.references))

  def select: Select.Builder[this.type] =
    Select.Builder(
      table = this,
      fields = getFields,
      where = WhereClause(None),
      groupBy = GroupByClause(List()),
      orderBy = OrderByClause(getSorts.headOption.map(_.fields.toList).getOrElse(List())))
}

object Table {

  abstract class SqlTable(val getSchema: String,
                          val getName: String,
                          val getAlias: Option[String]) extends Table {
    override def getFields: List[SqlField[_, SqlTable]] // to specialize return type as SqlField

    override def has(f: SqlField[_, Table.SqlTable]): Boolean = f.table == this

    override def fr: Fragment = const0(getName + getAlias.map(" " + _).getOrElse(""))

    def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](f: this.type => SqlFieldRef[A, T, T2]): JoinTable = joinOn(f(this))

    def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](f: this.type => SqlFieldRef[A, T, T2], kind: Join.Kind.type => Join.Kind): JoinTable = joinOn(f(this), kind)

    def joinOnOpt[A, T <: Table.SqlTable, T2 <: Table.SqlTable](f: this.type => SqlFieldRefOpt[A, T, T2]): JoinTable = joinOn(f(this))

    def joinOnOpt[A, T <: Table.SqlTable, T2 <: Table.SqlTable](f: this.type => SqlFieldRefOpt[A, T, T2], kind: Join.Kind.type => Join.Kind): JoinTable = joinOn(f(this), kind)

    def insert: Insert.Builder[this.type] = Insert.Builder(this)

    def update: Update.Builder[this.type] = Update.Builder(this, List())

    def delete: Delete.Builder[this.type] = Delete.Builder(this)

    override def toString: String = s"SqlTable($getSchema, $getName, $getAlias, $getFields)"
  }

  case class JoinTable(table: Table.SqlTable,
                       joins: List[Join[Table.SqlTable]],
                       getFields: List[Field[_]],
                       getSorts: List[Sort]) extends Table {
    override def has(f: SqlField[_, Table.SqlTable]): Boolean = table.has(f) || joins.exists(_.table.has(f))

    override def fr: Fragment = const0(table.getName + table.getAlias.map(" " + _).getOrElse("")) ++ joins.foldLeft(fr0"")(_ ++ fr0" " ++ _.fr)

    def dropFields(p: Field[_] => Boolean): JoinTable = copy(getFields = getFields.filterNot(p))

    def dropFields(fields: List[Field[_]]): JoinTable = dropFields(fields.contains(_))

    def dropFields(fields: Field[_]*): JoinTable = dropFields(fields.contains(_))
  }

  case class Join[+T <: Table.SqlTable](kind: Join.Kind, table: T, on: Cond) {
    def fr: Fragment = const0(s"${kind.value} ") ++ table.fr ++ const0(" ON ") ++ on.fr
  }

  object Join {

    sealed abstract class Kind(val value: String)

    object Kind {

      case object Inner extends Kind("INNER JOIN")

      case object LeftOuter extends Kind("LEFT OUTER JOIN")

    }

  }

  case class Sort(slug: String, label: String, fields: NonEmptyList[Field.Order[_, Table.SqlTable]])

  object Inner {

    case class JoinClause[T <: Table, U <: Table](left: T, kind: Join.Kind, right: U) {
      def on(cond: Cond): JoinTable =
        Exceptions.check(cond, (left, right) match {
          case (l: SqlTable, r: SqlTable) => JoinTable(
            table = l,
            joins = List(Join(kind, r, cond)),
            getFields = l.getFields ++ r.getFields,
            getSorts = l.getSorts)
          case (l: JoinTable, r: SqlTable) => JoinTable(
            table = l.table,
            joins = l.joins :+ Join(kind, r, cond),
            getFields = l.getFields ++ r.getFields,
            getSorts = l.getSorts)
          case (l: SqlTable, r: JoinTable) => JoinTable(
            table = l,
            joins = Join(kind, r.table, cond) :: r.joins,
            getFields = l.getFields ++ r.getFields,
            getSorts = l.getSorts)
          case (l: JoinTable, r: JoinTable) => JoinTable(
            table = l.table,
            joins = l.joins ++ (Join(kind, r.table, cond) :: r.joins),
            getFields = l.getFields ++ r.getFields,
            getSorts = l.getSorts)
        })

      def on(cond: U => Cond): JoinTable = on(cond(right))

      def on(cond: (T, U) => Cond): JoinTable = on(cond(left, right))
    }

  }

}
