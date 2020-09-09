package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.Put
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.sql.dsl.Field.Order

sealed trait Field[A] {
  val name: String

  def fr: Fragment
}

object Field {

  case class Order[A, T <: Table.SqlTable](field: SqlField[A, T], asc: Boolean) {
    def fr: Fragment = field.fr ++ fr0" IS NULL, " ++ field.fr ++ (if (asc) fr0"" else fr0" DESC")
  }

}

class SqlField[A, +T <: Table.SqlTable](val table: T,
                                        val name: String) extends Field[A] {
  def fr: Fragment = const0(s"${table.getAlias.getOrElse(table.getName)}.$name")

  def is(value: A)(implicit p: Put[A]): Cond = Cond.is(this, value)

  def is[T2 <: Table.SqlTable](field: SqlField[A, T2]): Cond = Cond.is(this, field)

  def isOpt[T2 <: Table.SqlTable](field: SqlField[Option[A], T2]): Cond = Cond.isOpt(this, field)

  // TODO restrict to string fields ?
  def like(value: String): Cond = Cond.like(this, value)

  def gt(value: A)(implicit p: Put[A]): Cond = Cond.gt(this, value)

  def lt(value: A)(implicit p: Put[A]): Cond = Cond.lt(this, value)

  def isNull: Cond = Cond.isNull(this)

  def notNull: Cond = Cond.notNull(this)

  def in(values: NonEmptyList[A])(implicit p: Put[A]): Cond = Cond.in(this, values)

  def notIn(values: NonEmptyList[A])(implicit p: Put[A]): Cond = Cond.notIn(this, values)

  def in(q: Query.Select[A]): Cond = Cond.in(this, q)

  def notIn(q: Query.Select[A]): Cond = Cond.notIn(this, q)

  def asc: Order[A, Table.SqlTable] = Order(this, asc = true)

  def desc: Order[A, Table.SqlTable] = Order(this, asc = false)

  def value: String = s"${table.getName}.$name"

  override def toString: String = s"Field($value)"

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

  override def toString: String = s"FieldOpt(${table.getName}.$name)"
}

class SqlFieldRef[A, T <: Table.SqlTable, T2 <: Table.SqlTable](override val table: T,
                                                                override val name: String,
                                                                val references: SqlField[A, T2]) extends SqlField[A, T](table, name) {
  override def toString: String = s"FieldRef(${table.getName}.$name, ${references.table.getName}.${references.name})"

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
  override def toString: String = s"FieldRefOpt(${table.getName}.$name, ${references.table.getName}.${references.name})"

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

case class AggField[A](formula: String, name: String) extends Field[A] {
  def fr: Fragment = if (name == formula) const0(formula) else const0(formula + " as " + name)
}

object AggField {
  def apply[A](formula: String): AggField[A] = new AggField(formula, formula)
}
