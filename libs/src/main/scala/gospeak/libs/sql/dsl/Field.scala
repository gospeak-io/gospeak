package gospeak.libs.sql.dsl

import doobie.util.Put
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0

class Field[A, T <: Table.SqlTable](val table: T,
                                    val name: String) {
  def fr: Fragment = const0(value)

  def eq(v: A)(implicit p: Put[A]): Cond = Cond.eq(this, v)

  def eq(f: Field[A, _ <: Table.SqlTable]): Cond = Cond.eq(this, f)

  private def value = s"${table.getAlias.getOrElse(table.getName)}.$name"

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
    val state = Seq(table, name)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

class FieldRef[A, T <: Table.SqlTable, R <: Table.SqlTable](override val table: T,
                                                            override val name: String,
                                                            val references: Field[A, R]) extends Field[A, T](table, name) {
  def join: Table.JoinTable = table.join(references.table).on(this.eq(references))

  def joinOpt: Table.JoinTable = table.joinOpt(references.table).on(this.eq(references))

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
    val state = Seq(super.hashCode(), table, name, references)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
