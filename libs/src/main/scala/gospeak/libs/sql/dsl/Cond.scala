package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.Put
import doobie.util.fragment.Fragment
import gospeak.libs.sql.dsl.Cond.{And, Or, Parentheses}
import gospeak.libs.sql.dsl.Extensions._

sealed abstract class Cond(fields: List[SqlField[_, Table.SqlTable]]) {
  def fr: Fragment

  def and(c: Cond): Cond = And(this, c)

  def or(c: Cond): Cond = Or(this, c)

  def par: Cond = Parentheses(this)

  def getFields: List[SqlField[_, Table.SqlTable]] = fields
}

object Cond {
  def is[T <: Table.SqlTable, A: Put](f: SqlField[A, T], v: A): IsValue[T, A] = IsValue(f, v)

  def is[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f: SqlField[A, T], g: SqlField[A, T2]): IsField[T, A, T2] = IsField(f, g)

  def isOpt[T <: Table.SqlTable, A: Put](f: SqlField[Option[A], T], v: A): IsValueOpt[T, A] = IsValueOpt(f, v)

  def isOpt[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f: SqlField[Option[A], T], g: SqlField[A, T2]): IsFieldLeftOpt[T, A, T2] = IsFieldLeftOpt(f, g)

  def isOpt[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f: SqlField[A, T], g: SqlField[Option[A], T2]): IsFieldRightOpt[T, A, T2] = IsFieldRightOpt(f, g)

  def like[T <: Table.SqlTable, A](f: SqlField[A, T], value: String): Like[T, A] = Like(f, value)

  def gt[T <: Table.SqlTable, A: Put](f: SqlField[A, T], v: A): GtValue[T, A] = GtValue(f, v)

  def lt[T <: Table.SqlTable, A: Put](f: SqlField[A, T], v: A): LtValue[T, A] = LtValue(f, v)

  def isNull[T <: Table.SqlTable, A](f: SqlField[A, T]): IsNull[T, A] = IsNull(f)

  def notNull[T <: Table.SqlTable, A](f: SqlField[A, T]): NotNull[T, A] = NotNull(f)

  def in[T <: Table.SqlTable, A: Put](f: SqlField[A, T], v: NonEmptyList[A]): InValues[T, A] = InValues(f, v)

  def notIn[T <: Table.SqlTable, A: Put](f: SqlField[A, T], v: NonEmptyList[A]): NotInValues[T, A] = NotInValues(f, v)

  def in[T <: Table.SqlTable, A](f: SqlField[A, T], q: Query.Select[A]): InQuery[T, A] = InQuery(f, q)

  def notIn[T <: Table.SqlTable, A](f: SqlField[A, T], q: Query.Select[A]): NotInQuery[T, A] = NotInQuery(f, q)

  case class IsValue[T <: Table.SqlTable, A: Put](f: SqlField[A, T], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0"=$value"
  }

  case class IsValueOpt[T <: Table.SqlTable, A: Put](f: SqlField[Option[A], T], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0"=$value"
  }

  case class IsField[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f1: SqlField[A, T], f2: SqlField[A, T2]) extends Cond(List(f1, f2)) {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  case class IsFieldLeftOpt[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f1: SqlField[Option[A], T], f2: SqlField[A, T2]) extends Cond(List(f1, f2)) {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  case class IsFieldRightOpt[T <: Table.SqlTable, A, T2 <: Table.SqlTable](f1: SqlField[A, T], f2: SqlField[Option[A], T2]) extends Cond(List(f1, f2)) {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  case class Like[T <: Table.SqlTable, A](f: SqlField[A, T], value: String) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" LIKE $value"
  }

  case class GtValue[T <: Table.SqlTable, A: Put](f: SqlField[A, T], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" > $value"
  }

  case class LtValue[T <: Table.SqlTable, A: Put](f: SqlField[A, T], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" < $value"
  }

  case class IsNull[T <: Table.SqlTable, A](f: SqlField[A, T]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IS NULL"
  }

  case class NotNull[T <: Table.SqlTable, A](f: SqlField[A, T]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IS NOT NULL"
  }

  case class InValues[T <: Table.SqlTable, A: Put](f: SqlField[A, T], values: NonEmptyList[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0") " // TODO remove last space when migration is done
  }

  case class NotInValues[T <: Table.SqlTable, A: Put](f: SqlField[A, T], values: NonEmptyList[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0") " // TODO remove last space when migration is done
  }

  case class InQuery[T <: Table.SqlTable, A](f: SqlField[A, T], q: Query.Select[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ q.fr ++ fr0")"
  }

  case class NotInQuery[T <: Table.SqlTable, A](f: SqlField[A, T], q: Query.Select[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ q.fr ++ fr0")"
  }

  case class And(left: Cond, right: Cond) extends Cond(left.getFields ++ right.getFields) {
    override def fr: Fragment = left.fr ++ fr0" AND " ++ right.fr
  }

  case class Or(left: Cond, right: Cond) extends Cond(left.getFields ++ right.getFields) {
    override def fr: Fragment = left.fr ++ fr0" OR " ++ right.fr
  }

  case class Parentheses(cond: Cond) extends Cond(cond.getFields) {
    override def fr: Fragment = fr0"(" ++ cond.fr ++ fr0")"
  }

}
