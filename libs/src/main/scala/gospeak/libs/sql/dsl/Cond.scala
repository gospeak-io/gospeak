package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.Put
import doobie.util.fragment.Fragment
import gospeak.libs.sql.dsl.Cond.{And, Or, Parentheses}
import gospeak.libs.sql.dsl.Extensions._

sealed trait Cond {
  def fr: Fragment

  def and(c: Cond): Cond = And(this, c)

  def or(c: Cond): Cond = Or(this, c)

  def par: Cond = Parentheses(this)
}

object Cond {
  def eq[A, T <: Table.SqlTable, U <: Table.SqlTable](f: Field[A, T], g: Field[A, U]): EqField[A, T, U] = EqField(f, g)

  def eq[A: Put, T <: Table.SqlTable](f: Field[A, T], v: A): EqValue[A, T] = EqValue(f, v)

  def gt[A: Put, T <: Table.SqlTable](f: Field[A, T], v: A): GtValue[A, T] = GtValue(f, v)

  def lt[A: Put, T <: Table.SqlTable](f: Field[A, T], v: A): LtValue[A, T] = LtValue(f, v)

  def isNull[A, T <: Table.SqlTable](f: Field[A, T]): IsNull[A, T] = IsNull(f)

  def notNull[A, T <: Table.SqlTable](f: Field[A, T]): NotNull[A, T] = NotNull(f)

  def in[A: Put, T <: Table.SqlTable](f: Field[A, T], v: NonEmptyList[A]): InValues[A, T] = InValues(f, v)

  def notIn[A: Put, T <: Table.SqlTable](f: Field[A, T], v: NonEmptyList[A]): NotInValues[A, T] = NotInValues(f, v)

  def in[A, T <: Table.SqlTable](f: Field[A, T], q: Query.Select[A]): InQuery[A, T] = InQuery(f, q)

  def notIn[A, T <: Table.SqlTable](f: Field[A, T], q: Query.Select[A]): NotInQuery[A, T] = NotInQuery(f, q)

  case class EqField[A, T <: Table.SqlTable, U <: Table.SqlTable](f1: Field[A, T], f2: Field[A, U]) extends Cond {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  case class EqValue[A: Put, T <: Table.SqlTable](f: Field[A, T], value: A) extends Cond {
    override def fr: Fragment = f.fr ++ fr0"=$value"
  }

  case class GtValue[A: Put, T <: Table.SqlTable](f: Field[A, T], value: A) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" > $value"
  }

  case class LtValue[A: Put, T <: Table.SqlTable](f: Field[A, T], value: A) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" < $value"
  }

  case class IsNull[A, T <: Table.SqlTable](f: Field[A, T]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" IS NULL"
  }

  case class NotNull[A, T <: Table.SqlTable](f: Field[A, T]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" IS NOT NULL"
  }

  case class InValues[A: Put, T <: Table.SqlTable](f: Field[A, T], values: NonEmptyList[A]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0") " // TODO remove last space when migration is done
  }

  case class NotInValues[A: Put, T <: Table.SqlTable](f: Field[A, T], values: NonEmptyList[A]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0") " // TODO remove last space when migration is done
  }

  case class InQuery[A, T <: Table.SqlTable](f: Field[A, T], q: Query.Select[A]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ q.fr ++ fr0")"
  }

  case class NotInQuery[A, T <: Table.SqlTable](f: Field[A, T], q: Query.Select[A]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ q.fr ++ fr0")"
  }

  case class And(left: Cond, right: Cond) extends Cond {
    override def fr: Fragment = left.fr ++ fr0" AND " ++ right.fr
  }

  case class Or(left: Cond, right: Cond) extends Cond {
    override def fr: Fragment = left.fr ++ fr0" OR " ++ right.fr
  }

  case class Parentheses(cond: Cond) extends Cond {
    override def fr: Fragment = fr0"(" ++ cond.fr ++ fr0")"
  }

}
