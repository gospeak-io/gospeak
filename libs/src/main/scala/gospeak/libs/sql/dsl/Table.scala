package gospeak.libs.sql.dsl

import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.sql.dsl.Query.SelectBuilder
import gospeak.libs.sql.dsl.Table._

sealed trait Table {
  def getSchema: String

  def getName: String

  def getAlias: Option[String]

  def getFields: List[Field[_, _]]

  def getJoins: List[Join]

  def fr: Fragment

  // TODO do not lose previous type (chain predefined joins, still use fields...)
  def join(table: Table, on: Cond): JoinTable = doJoin("INNER JOIN", table, on)

  def joinOpt(table: Table, on: Cond): JoinTable = doJoin("LEFT OUTER JOIN", table, on)

  private def doJoin(kind: String, table: Table, on: Cond): JoinTable =
    JoinTable(
      getSchema = getSchema,
      getName = getName,
      getFields = getFields ++ table.getFields,
      getJoins = getJoins ++ List(Join(kind, table.getSchema, table.getName, on)) ++ table.getJoins)

  def select: SelectBuilder = SelectBuilder(this, getFields, None)
}

object Table {

  abstract class SqlTable(schema: String, name: String, alias: Option[String]) extends Table {
    override def getSchema: String = schema

    override def getName: String = name

    override def getAlias: Option[String] = alias

    override def getJoins: List[Join] = List()

    override def fr: Fragment = const0(getName)
  }

  case class JoinTable(getSchema: String,
                       getName: String,
                       getFields: List[Field[_, _]],
                       getJoins: List[Join]) extends Table {
    override def getAlias: Option[String] = None

    override def fr: Fragment = const0(getName) ++ getJoins.foldLeft(fr0"")(_ ++ fr0" " ++ _.fr)
  }

  case class Join(kind: String, schema: String, table: String, on: Cond) {
    def fr: Fragment = const0(s"$kind $table ON ") ++ on.fr
  }

}
