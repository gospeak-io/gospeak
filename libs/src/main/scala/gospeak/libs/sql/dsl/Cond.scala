package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.Put
import doobie.util.fragment.Fragment
import gospeak.libs.sql.dsl.Cond.{And, Or, Parentheses}
import gospeak.libs.sql.dsl.Extensions._

sealed abstract class Cond(fields: List[Field[_]]) {
  def fr: Fragment

  def and(c: Cond): Cond = And(this, c)

  def or(c: Cond): Cond = Or(this, c)

  def par: Cond = Parentheses(this)

  def getFields: List[Field[_]] = fields
}

object Cond {
  def is[A: Put](f: Field[A], v: A): IsValue[A] = IsValue(f, v)

  def is[A](f: Field[A], g: Field[A]): IsField[A] = IsField(f, g)

  def is[A](f: Field[A], g: SqlFieldRefOpt[A, _, _]): IsFieldRightOpt[A] = IsFieldRightOpt(f, g)

  def is[A](f: Field[A], s: Query.Select[A]): IsQuery[A] = IsQuery(f, s)

  def isOpt[A: Put](f: Field[Option[A]], v: A): IsValueOpt[A] = IsValueOpt(f, v)

  def isOpt[A](f: Field[Option[A]], g: Field[A]): IsFieldLeftOpt[A] = IsFieldLeftOpt(f, g)

  def isOpt[A](f: Field[A], g: Field[Option[A]]): IsFieldRightOpt[A] = IsFieldRightOpt(f, g)

  def like[A](f: Field[A], value: String): Like[A] = Like(f, value)

  def notLike[A](f: Field[A], value: String): NotLike[A] = NotLike(f, value)

  def gt[A: Put](f: Field[A], v: A): GtValue[A] = GtValue(f, v)

  def gte[A: Put](f: Field[A], v: A): GteValue[A] = GteValue(f, v)

  def lt[A: Put](f: Field[A], v: A): LtValue[A] = LtValue(f, v)

  def lte[A: Put](f: Field[A], v: A): LteValue[A] = LteValue(f, v)

  def isNull[A](f: Field[A]): IsNull[A] = IsNull(f)

  def notNull[A](f: Field[A]): NotNull[A] = NotNull(f)

  def in[A: Put](f: Field[A], v: NonEmptyList[A]): InValues[A] = InValues(f, v)

  def notIn[A: Put](f: Field[A], v: NonEmptyList[A]): NotInValues[A] = NotInValues(f, v)

  def in[A](f: Field[A], q: Query.Select[A]): InQuery[A] = InQuery(f, q)

  def notIn[A](f: Field[A], q: Query.Select[A]): NotInQuery[A] = NotInQuery(f, q)

  def cond[A](f: Field[A], fr: Fragment): CustomCond[A] = CustomCond(f, fr)

  case class IsValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0"=$value"
  }

  case class IsValueOpt[A: Put](f: Field[Option[A]], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0"=$value"
  }

  case class IsField[A](f1: Field[A], f2: Field[A]) extends Cond(List(f1, f2)) {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  case class IsFieldLeftOpt[A](f1: Field[Option[A]], f2: Field[A]) extends Cond(List(f1, f2)) {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  case class IsFieldRightOpt[A](f1: Field[A], f2: Field[Option[A]]) extends Cond(List(f1, f2)) {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  case class IsQuery[A](f: Field[A], s: Query.Select[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0"=(" ++ s.fr ++ fr0")"
  }

  case class Like[A](f: Field[A], value: String) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" LIKE $value"
  }

  case class NotLike[A](f: Field[A], value: String) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" NOT LIKE $value"
  }

  case class GtValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" > $value"
  }

  case class GteValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" >= $value"
  }

  case class LtValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" < $value"
  }

  case class LteValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" <= $value"
  }

  case class IsNull[A](f: Field[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IS NULL"
  }

  case class NotNull[A](f: Field[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IS NOT NULL"
  }

  case class InValues[A: Put](f: Field[A], values: NonEmptyList[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0")"
  }

  case class NotInValues[A: Put](f: Field[A], values: NonEmptyList[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0")"
  }

  case class InQuery[A](f: Field[A], q: Query.Select[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ q.fr ++ fr0")"
  }

  case class NotInQuery[A](f: Field[A], q: Query.Select[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ q.fr ++ fr0")"
  }

  case class CustomCond[A](f: Field[A], cond: Fragment) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ cond
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
