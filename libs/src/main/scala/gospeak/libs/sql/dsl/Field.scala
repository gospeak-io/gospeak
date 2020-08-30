package gospeak.libs.sql.dsl

import doobie.implicits._
import doobie.util.Put
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0

class Field[A, T <: Table.SqlTable](val table: T,
                                    field: String) {
  def fr: Fragment = const0(value)

  def eq(v: A)(implicit p: Put[A]): Cond = Cond(const0(value) ++ fr0"=$v")

  def eq(f: Field[A, _]): Cond = Cond(const0(s"$value=${f.value}"))

  private def value = s"${table.getAlias.getOrElse(table.getName)}.$field"
}

class FieldRef[A, T1 <: Table.SqlTable, T2 <: Table.SqlTable](table: T1,
                                                              field: String,
                                                              linked: Field[A, T2]) extends Field[A, T1](table, field) {
  def join: Table.JoinTable = table.join(linked.table).on(this.eq(linked))

  def joinOpt: Table.JoinTable = table.joinOpt(linked.table).on(this.eq(linked))
}
