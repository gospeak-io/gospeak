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

  def getFields: List[Field[_, Table.SqlTable]]
}

object Cond {
  def is[T <: Table.SqlTable, A: Put](f: Field[A, T], v: A): IsValue[T, A] = IsValue(f, v)

  def is[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f: Field[A, T], g: Field[A, T2]): IsField[T, A, T2] = IsField(f, g)

  def isOpt[T <: Table.SqlTable, A: Put](f: Field[Option[A], T], v: A): IsValueOpt[T, A] = IsValueOpt(f, v)

  def isOpt[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f: Field[Option[A], T], g: Field[A, T2]): IsFieldLeftOpt[T, A, T2] = IsFieldLeftOpt(f, g)

  def isOpt[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f: Field[A, T], g: Field[Option[A], T2]): IsFieldRightOpt[T, A, T2] = IsFieldRightOpt(f, g)

  def gt[T <: Table.SqlTable, A: Put](f: Field[A, T], v: A): GtValue[T, A] = GtValue(f, v)

  def lt[T <: Table.SqlTable, A: Put](f: Field[A, T], v: A): LtValue[T, A] = LtValue(f, v)

  def isNull[T <: Table.SqlTable, A](f: Field[A, T]): IsNull[T, A] = IsNull(f)

  def notNull[T <: Table.SqlTable, A](f: Field[A, T]): NotNull[T, A] = NotNull(f)

  def in[T <: Table.SqlTable, A: Put](f: Field[A, T], v: NonEmptyList[A]): InValues[T, A] = InValues(f, v)

  def notIn[T <: Table.SqlTable, A: Put](f: Field[A, T], v: NonEmptyList[A]): NotInValues[T, A] = NotInValues(f, v)

  def in[T <: Table.SqlTable, A](f: Field[A, T], q: Query.Select[A]): InQuery[T, A] = InQuery(f, q)

  def notIn[T <: Table.SqlTable, A](f: Field[A, T], q: Query.Select[A]): NotInQuery[T, A] = NotInQuery(f, q)

  case class IsValue[T <: Table.SqlTable, A: Put](f: Field[A, T], value: A) extends Cond {
    override def fr: Fragment = f.fr ++ fr0"=$value"

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class IsValueOpt[T <: Table.SqlTable, A: Put](f: Field[Option[A], T], value: A) extends Cond {
    override def fr: Fragment = f.fr ++ fr0"=$value"

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class IsField[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f1: Field[A, T], f2: Field[A, T2]) extends Cond {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr

    override def getFields: List[Field[_, Table.SqlTable]] = List(f1, f2)
  }

  case class IsFieldLeftOpt[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f1: Field[Option[A], T], f2: Field[A, T2]) extends Cond {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr

    override def getFields: List[Field[_, Table.SqlTable]] = List(f1, f2)
  }

  case class IsFieldRightOpt[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f1: Field[A, T], f2: Field[Option[A], T2]) extends Cond {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr

    override def getFields: List[Field[_, Table.SqlTable]] = List(f1, f2)
  }

  case class GtValue[T <: Table.SqlTable, A: Put](f: Field[A, T], value: A) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" > $value"

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class LtValue[T <: Table.SqlTable, A: Put](f: Field[A, T], value: A) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" < $value"

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class IsNull[T <: Table.SqlTable, A](f: Field[A, T]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" IS NULL"

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class NotNull[T <: Table.SqlTable, A](f: Field[A, T]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" IS NOT NULL"

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class InValues[T <: Table.SqlTable, A: Put](f: Field[A, T], values: NonEmptyList[A]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0") " // TODO remove last space when migration is done

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class NotInValues[T <: Table.SqlTable, A: Put](f: Field[A, T], values: NonEmptyList[A]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0") " // TODO remove last space when migration is done

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class InQuery[T <: Table.SqlTable, A](f: Field[A, T], q: Query.Select[A]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ q.fr ++ fr0")"

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class NotInQuery[T <: Table.SqlTable, A](f: Field[A, T], q: Query.Select[A]) extends Cond {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ q.fr ++ fr0")"

    override def getFields: List[Field[_, Table.SqlTable]] = List(f)
  }

  case class And(left: Cond, right: Cond) extends Cond {
    override def fr: Fragment = left.fr ++ fr0" AND " ++ right.fr

    override def getFields: List[Field[_, Table.SqlTable]] = left.getFields ++ right.getFields
  }

  case class Or(left: Cond, right: Cond) extends Cond {
    override def fr: Fragment = left.fr ++ fr0" OR " ++ right.fr

    override def getFields: List[Field[_, Table.SqlTable]] = left.getFields ++ right.getFields
  }

  case class Parentheses(cond: Cond) extends Cond {
    override def fr: Fragment = fr0"(" ++ cond.fr ++ fr0")"

    override def getFields: List[Field[_, Table.SqlTable]] = cond.getFields
  }

}
