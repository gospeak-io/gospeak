package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.sql.dsl.Query.Inner.{OrderByClause, WhereClause}
import gospeak.libs.sql.dsl.Query.{Delete, Insert, Select, Update}
import gospeak.libs.sql.dsl.Table.Inner._
import gospeak.libs.sql.dsl.Table.{Join, JoinTable, Sort}

sealed trait Table {
  def getSchema: String

  def getName: String

  def getAlias: Option[String]

  def getFields: List[Field[_, Table.SqlTable]]

  def getSorts: List[Sort]

  def getJoins: List[Join[Table]]

  def has(f: Field[_, Table.SqlTable]): Boolean

  def fr: Fragment

  // TODO do not lose previous type (chain predefined joins, still use typed fields...)
  def join[T <: Table](table: T): JoinClause[this.type, T] = JoinClause(this, Join.Kind.Inner, table)

  def join[T <: Table](table: T, kind: Join.Kind.type => Join.Kind): JoinClause[this.type, T] = JoinClause(this, kind(Join.Kind), table)

  def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](ref: FieldRef[A, T, T2]): JoinTable = join(ref.references.table).on(ref.is(ref.references))

  def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](ref: FieldRef[A, T, T2], kind: Join.Kind.type => Join.Kind): JoinTable = join(ref.references.table, kind).on(ref.is(ref.references))

  def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](ref: FieldRefOpt[A, T, T2]): JoinTable = join(ref.references.table).on(ref.isOpt(ref.references))

  def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](ref: FieldRefOpt[A, T, T2], kind: Join.Kind.type => Join.Kind): JoinTable = join(ref.references.table, kind).on(ref.isOpt(ref.references))

  def select: Select.Builder[this.type] =
    Select.Builder(
      table = this,
      fields = getFields,
      where = WhereClause(None),
      orderBy = OrderByClause(getSorts.headOption.map(_.fields.toList).getOrElse(List())))
}

object Table {

  abstract class SqlTable(schema: String,
                          name: String,
                          alias: Option[String]) extends Table {
    override def getSchema: String = schema

    override def getName: String = name

    override def getAlias: Option[String] = alias

    override def getJoins: List[Join[Table]] = List()

    override def has(f: Field[_, Table.SqlTable]): Boolean = f.table == this

    override def fr: Fragment = const0(getName + alias.map(" " + _).getOrElse(""))

    def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](f: this.type => FieldRef[A, T, T2]): JoinTable = joinOn(f(this))

    def joinOn[A, T <: Table.SqlTable, T2 <: Table.SqlTable](f: this.type => FieldRef[A, T, T2], kind: Join.Kind.type => Join.Kind): JoinTable = joinOn(f(this), kind)

    def joinOnOpt[A, T <: Table.SqlTable, T2 <: Table.SqlTable](f: this.type => FieldRefOpt[A, T, T2]): JoinTable = joinOn(f(this))

    def joinOnOpt[A, T <: Table.SqlTable, T2 <: Table.SqlTable](f: this.type => FieldRefOpt[A, T, T2], kind: Join.Kind.type => Join.Kind): JoinTable = joinOn(f(this), kind)

    def insert: Insert.Builder[this.type] = Insert.Builder(this)

    def update: Update.Builder[this.type] = Update.Builder(this, List())

    def delete: Delete.Builder[this.type] = Delete.Builder(this)

    override def toString: String = s"SqlTable($schema, $name, $alias, $getFields)"
  }

  case class JoinTable(getSchema: String,
                       getName: String,
                       getAlias: Option[String],
                       getFields: List[Field[_, Table.SqlTable]],
                       getSorts: List[Sort],
                       getJoins: List[Join[Table]]) extends Table {
    override def has(f: Field[_, Table.SqlTable]): Boolean = (f.table.getSchema == getSchema && f.table.getName == getName) || getJoins.exists(j => f.table.getSchema == j.table.getSchema && f.table.getName == j.table.getName)

    override def fr: Fragment = const0(getName + getAlias.map(" " + _).getOrElse("")) ++ getJoins.foldLeft(fr0"")(_ ++ fr0" " ++ _.fr)

    def dropFields(p: Field[_, Table.SqlTable] => Boolean): JoinTable = copy(getFields = getFields.filterNot(p))

    def dropFields(fields: List[Field[_, Table.SqlTable]]): JoinTable = dropFields(fields.contains(_))

    def dropFields(fields: Field[_, Table.SqlTable]*): JoinTable = dropFields(fields.contains(_))
  }

  case class Join[+T <: Table](kind: Join.Kind, table: T, on: Cond) {
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
        Exceptions.check(cond, JoinTable(
          getSchema = left.getSchema,
          getName = left.getName,
          getAlias = left.getAlias,
          getFields = left.getFields ++ right.getFields,
          getSorts = left.getSorts,
          getJoins = left.getJoins ++ List(Join(kind, right, cond)) ++ right.getJoins))

      def on(cond: U => Cond): JoinTable = on(cond(right))

      def on(cond: (T, U) => Cond): JoinTable = on(cond(left, right))
    }

  }

}
