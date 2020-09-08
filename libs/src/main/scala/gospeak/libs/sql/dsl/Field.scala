package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.Put
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.sql.dsl.Field.Order

class Field[A, T <: Table.SqlTable](val table: T,
                                    val name: String) {
  def fr: Fragment = const0(s"${table.getAlias.getOrElse(table.getName)}.$name")

  def is(value: A)(implicit p: Put[A]): Cond = Cond.is(this, value)

  def is[T2 <: Table.SqlTable](field: Field[A, T2]): Cond = Cond.is(this, field)

  def isOpt[T2 <: Table.SqlTable](field: Field[Option[A], T2]): Cond = Cond.isOpt(this, field)

  def gt(value: A)(implicit p: Put[A]): Cond = Cond.gt(this, value)

  def lt(value: A)(implicit p: Put[A]): Cond = Cond.lt(this, value)

  def isNull: Cond = Cond.isNull(this)

  def notNull: Cond = Cond.notNull(this)

  def in(values: NonEmptyList[A])(implicit p: Put[A]): Cond = Cond.in(this, values)

  def notIn(values: NonEmptyList[A])(implicit p: Put[A]): Cond = Cond.notIn(this, values)

  def in(q: Query.Select[A]): Cond = Cond.in(this, q)

  def notIn(q: Query.Select[A]): Cond = Cond.notIn(this, q)

  def asc: Order[A, T] = Order(this, asc = true)

  def desc: Order[A, T] = Order(this, asc = false)

  override def toString: String = s"Field(${table.getName}.$name)"

  def canEqual(other: Any): Boolean = other.isInstanceOf[Field[_, _]]

  override def equals(other: Any): Boolean = other match {
    case that: Field[_, _] =>
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

object Field {

  case class Order[A, T <: Table.SqlTable](field: Field[A, T], asc: Boolean) {
    def fr: Fragment = field.fr ++ fr0" IS NULL, " ++ field.fr ++ (if (asc) fr0"" else fr0" DESC")
  }

}

class FieldOpt[A, T <: Table.SqlTable](override val table: T,
                                       override val name: String) extends Field[Option[A], T](table, name) {
  def isOpt(value: A)(implicit p: Put[A]): Cond = Cond.isOpt(this, value)

  def isOpt[T2 <: Table.SqlTable](field: Field[A, T2]): Cond = Cond.isOpt(this, field)

  override def toString: String = s"FieldOpt(${table.getName}.$name)"
}

class FieldRef[A, T <: Table.SqlTable, T2 <: Table.SqlTable](override val table: T,
                                                             override val name: String,
                                                             val references: Field[A, T2]) extends Field[A, T](table, name) {
  override def toString: String = s"FieldRef(${table.getName}.$name, ${references.table.getName}.${references.name})"

  override def canEqual(other: Any): Boolean = other.isInstanceOf[FieldRef[_, _, _]]

  override def equals(other: Any): Boolean = other match {
    case that: FieldRef[_, _, _] =>
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

class FieldRefOpt[A, T <: Table.SqlTable, T2 <: Table.SqlTable](override val table: T,
                                                                override val name: String,
                                                                val references: Field[A, T2]) extends FieldOpt[A, T](table, name) {
  override def toString: String = s"FieldRefOpt(${table.getName}.$name, ${references.table.getName}.${references.name})"

  override def canEqual(other: Any): Boolean = other.isInstanceOf[FieldRefOpt[_, _, _]]

  override def equals(other: Any): Boolean = other match {
    case that: FieldRefOpt[_, _, _] =>
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
