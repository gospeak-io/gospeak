package gospeak.libs.sql.dsl

import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.sql.dsl.Query.{Delete, Insert, Select, Update}
import gospeak.libs.sql.dsl.Table.Inner._

sealed trait Table {
  def getSchema: String

  def getName: String

  def getAlias: Option[String]

  def getFields: List[Field[_, _]]

  def getJoins: List[Join[_]]

  def fr: Fragment

  // TODO do not lose previous type (chain predefined joins, still use typed fields...)
  def join[T <: Table](table: T): JoinClause[this.type, T] = JoinClause(this, "INNER JOIN", table)

  def joinOpt[T <: Table](table: T): JoinClause[this.type, T] = JoinClause(this, "LEFT OUTER JOIN", table)

  def select: Select.Builder[this.type] = Select.Builder(this, getFields)
}

object Table {

  abstract class SqlTable(schema: String, name: String, alias: Option[String]) extends Table {
    override def getSchema: String = schema

    override def getName: String = name

    override def getAlias: Option[String] = alias

    override def getJoins: List[Join[_]] = List()

    override def fr: Fragment = const0(getName + alias.map(" " + _).getOrElse(""))

    def insert: Insert.Builder[this.type] = Insert.Builder(this)

    def update: Update.Builder[this.type] = Update.Builder(this, List())

    def delete: Delete.Builder[this.type] = Delete.Builder(this)

    override def toString: String = s"SqlTable($schema, $name, $alias, $getFields)"
  }

  case class JoinTable(getSchema: String,
                       getName: String,
                       getAlias: Option[String],
                       getFields: List[Field[_, _]],
                       getJoins: List[Join[_]]) extends Table {
    override def fr: Fragment = const0(getName + getAlias.map(" " + _).getOrElse("")) ++ getJoins.foldLeft(fr0"")(_ ++ fr0" " ++ _.fr)
  }

  object Inner {

    case class Join[T <: Table](kind: String, table: T, on: Cond) {
      def fr: Fragment = const0(s"$kind ") ++ table.fr ++ const0(" ON ") ++ on.fr
    }

    case class JoinClause[T <: Table, U <: Table](left: T, kind: String, right: U) {
      def on(cond: Cond): JoinTable =
        JoinTable(
          getSchema = left.getSchema,
          getName = left.getName,
          getAlias = left.getAlias,
          getFields = left.getFields ++ right.getFields,
          getJoins = left.getJoins ++ List(Join(kind, right, cond)) ++ right.getJoins)

      def on(cond: U => Cond): JoinTable = on(cond(right))

      def on(cond: (T, U) => Cond): JoinTable = on(cond(left, right))
    }

  }

}
