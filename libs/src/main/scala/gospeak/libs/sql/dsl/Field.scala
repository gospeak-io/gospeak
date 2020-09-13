package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.Put
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.sql.dsl.Field.Order

sealed trait Field[A] {
  val name: String
  val alias: Option[String]

  def fr: Fragment

  def as(alias: String): Field[A]

  def is(value: A)(implicit p: Put[A]): Cond = Cond.is(this, value)

  def is(field: Field[A]): Cond = Cond.is(this, field)

  def isOpt(field: Field[Option[A]]): Cond = Cond.isOpt(this, field)

  // TODO restrict to fields with sql string type
  def like(value: String): Cond = Cond.like(this, value)

  def gt(value: A)(implicit p: Put[A]): Cond = Cond.gt(this, value)

  def gte(value: A)(implicit p: Put[A]): Cond = Cond.gte(this, value)

  def lt(value: A)(implicit p: Put[A]): Cond = Cond.lt(this, value)

  def lte(value: A)(implicit p: Put[A]): Cond = Cond.lte(this, value)

  def isNull: Cond = Cond.isNull(this)

  def notNull: Cond = Cond.notNull(this)

  def in(values: NonEmptyList[A])(implicit p: Put[A]): Cond = Cond.in(this, values)

  def notIn(values: NonEmptyList[A])(implicit p: Put[A]): Cond = Cond.notIn(this, values)

  def in(q: Query.Select[A]): Cond = Cond.in(this, q)

  def notIn(q: Query.Select[A]): Cond = Cond.notIn(this, q)

  def asc: Order[A] = Order(this, asc = true)

  def desc: Order[A] = Order(this, asc = false)
}

object Field {

  case class Order[A](field: Field[A], asc: Boolean) {
    def fr: Fragment = field.fr ++ fr0" IS NULL, " ++ field.fr ++ (if (asc) fr0"" else fr0" DESC")
  }

}

class SqlField[A, +T <: Table.SqlTable](val table: T,
                                        val name: String,
                                        val alias: Option[String] = None) extends Field[A] {
  def fr: Fragment = const0(s"${table.getAlias.getOrElse(table.getName)}.$name${alias.map(" as " + _).getOrElse("")}")

  def as(alias: String): SqlField[A, T] = new SqlField[A, T](table, name, Some(alias))

  // create a null TableField based on a sql field, useful on union when a field is available on one side only
  def asNull: TableField[A] = TableField[A]("null", alias = Some(alias.getOrElse(name)))

  def asNull(alias: String): TableField[A] = TableField[A]("null", alias = Some(alias))

  def value: String = s"${table.getName}.$name"

  override def toString: String = s"SqlField($value)"

  def canEqual(other: Any): Boolean = other.isInstanceOf[SqlField[_, _]]

  override def equals(other: Any): Boolean = other match {
    case that: SqlField[_, _] =>
      (that canEqual this) &&
        table == that.table &&
        name == that.name
    case _ => false
  }

  override def hashCode(): Int = {
    val state = List(table, name)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

class SqlFieldOpt[A, T <: Table.SqlTable](override val table: T,
                                          override val name: String) extends SqlField[Option[A], T](table, name) {
  def isOpt(value: A)(implicit p: Put[A]): Cond = Cond.isOpt(this, value)

  def isOpt[T2 <: Table.SqlTable](field: SqlField[A, T2]): Cond = Cond.isOpt(this, field)

  override def toString: String = s"SqlFieldOpt(${table.getName}.$name)"
}

class SqlFieldRef[A, T <: Table.SqlTable, T2 <: Table.SqlTable](override val table: T,
                                                                override val name: String,
                                                                val references: SqlField[A, T2]) extends SqlField[A, T](table, name) {
  override def toString: String = s"SqlFieldRef(${table.getName}.$name, ${references.table.getName}.${references.name})"

  override def canEqual(other: Any): Boolean = other.isInstanceOf[SqlFieldRef[_, _, _]]

  override def equals(other: Any): Boolean = other match {
    case that: SqlFieldRef[_, _, _] =>
      super.equals(that) &&
        (that canEqual this) &&
        table == that.table &&
        name == that.name &&
        references == that.references
    case _ => false
  }

  override def hashCode(): Int = {
    val state = List(super.hashCode(), table, name, references)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

class SqlFieldRefOpt[A, T <: Table.SqlTable, T2 <: Table.SqlTable](override val table: T,
                                                                   override val name: String,
                                                                   val references: SqlField[A, T2]) extends SqlFieldOpt[A, T](table, name) {
  override def toString: String = s"SqlFieldRefOpt(${table.getName}.$name, ${references.table.getName}.${references.name})"

  override def canEqual(other: Any): Boolean = other.isInstanceOf[SqlFieldRefOpt[_, _, _]]

  override def equals(other: Any): Boolean = other match {
    case that: SqlFieldRefOpt[_, _, _] =>
      super.equals(that) &&
        (that canEqual this) &&
        table == that.table &&
        name == that.name &&
        references == that.references
    case _ => false
  }

  override def hashCode(): Int = {
    val state = List(super.hashCode(), table, name, references)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

case class TableField[A](name: String, table: Option[String] = None, alias: Option[String] = None) extends Field[A] {
  override def fr: Fragment = const0(table.map(_ + ".").getOrElse("") + name + alias.map(" as " + _).getOrElse(""))

  override def as(alias: String): TableField[A] = copy(alias = Some(alias))
}
