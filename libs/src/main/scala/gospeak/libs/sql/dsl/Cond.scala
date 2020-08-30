package gospeak.libs.sql.dsl

import doobie.implicits._
import doobie.util.Put
import doobie.util.fragment.Fragment
import gospeak.libs.sql.dsl.Cond.{And, Or}

sealed trait Cond {
  def fr: Fragment

  def and(c: Cond): Cond = And(this, c)

  def or(c: Cond): Cond = Or(this, c)
}

object Cond {
  def eq[A, T <: Table.SqlTable, U <: Table.SqlTable](f: Field[A, T], g: Field[A, U]): EqField[A, T, U] = EqField(f, g)

  def eq[A: Put, T <: Table.SqlTable](f: Field[A, T], v: A): EqValue[A, T] = EqValue(f, v)

  case class EqField[A, T <: Table.SqlTable, U <: Table.SqlTable](f1: Field[A, T], f2: Field[A, U]) extends Cond {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  case class EqValue[A: Put, T <: Table.SqlTable](f: Field[A, T], value: A) extends Cond {
    override def fr: Fragment = f.fr ++ fr0"=$value"
  }

  case class And(left: Cond, right: Cond) extends Cond {
    override def fr: Fragment = left.fr ++ fr0" AND " ++ right.fr
  }

  case class Or(left: Cond, right: Cond) extends Cond {
    override def fr: Fragment = left.fr ++ fr0" OR " ++ right.fr
  }

}
