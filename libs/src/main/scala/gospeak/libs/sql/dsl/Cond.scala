package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.syntax.string._
import doobie.util.Put
import doobie.util.fragment.Fragment
import gospeak.libs.sql.dsl.Cond.{And, Or, Parentheses}
import gospeak.libs.sql.dsl.Extensions._

sealed abstract class Cond(fields: List[Field[_]]) {
  def fr: Fragment

  def sql: String = fr.query.sql

  def getFields: List[Field[_]] = fields

  def and(c: Cond): Cond = And(this, c)

  def or(c: Cond): Cond = Or(this, c)

  def par: Cond = this match {
    case p: Parentheses => p
    case c => Parentheses(c)
  }
}

object Cond {

  final case class IsValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0"=$value"
  }

  final case class IsValueOpt[A: Put](f: Field[Option[A]], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0"=$value"
  }

  final case class IsField[A](f1: Field[A], f2: Field[A]) extends Cond(List(f1, f2)) {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  final case class IsFieldLeftOpt[A](f1: Field[Option[A]], f2: Field[A]) extends Cond(List(f1, f2)) {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  final case class IsFieldRightOpt[A](f1: Field[A], f2: Field[Option[A]]) extends Cond(List(f1, f2)) {
    override def fr: Fragment = f1.fr ++ fr0"=" ++ f2.fr
  }

  final case class IsQuery[A](f: Field[A], s: Query.Select[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0"=(" ++ s.fr ++ fr0")"
  }

  final case class IsNotValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" != $value"
  }

  final case class Like[A](f: Field[A], value: String) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" LIKE $value"
  }

  final case class ILike[A](f: Field[A], value: String) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" ILIKE $value"
  }

  final case class LikeExpr(e: Expr, value: String) extends Cond(e.getFields) {
    override def fr: Fragment = e.fr ++ fr0" LIKE $value"
  }

  final case class NotLike[A](f: Field[A], value: String) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" NOT LIKE $value"
  }

  final case class GtValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" > $value"
  }

  final case class GteValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" >= $value"
  }

  final case class LtValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" < $value"
  }

  final case class LteValue[A: Put](f: Field[A], value: A) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" <= $value"
  }

  final case class IsNull[A](f: Field[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IS NULL"
  }

  final case class NotNull[A](f: Field[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IS NOT NULL"
  }

  final case class InValues[A: Put](f: Field[A], values: NonEmptyList[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0")"
  }

  final case class NotInValues[A: Put](f: Field[A], values: NonEmptyList[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ values.map(v => fr0"$v").mkFragment(", ") ++ fr0")"
  }

  final case class InQuery[A](f: Field[A], q: Query.Select[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" IN (" ++ q.fr ++ fr0")"
  }

  final case class NotInQuery[A](f: Field[A], q: Query.Select[A]) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ fr0" NOT IN (" ++ q.fr ++ fr0")"
  }

  final case class CustomCond[A](f: Field[A], cond: Fragment) extends Cond(List(f)) {
    override def fr: Fragment = f.fr ++ cond
  }

  final case class And(left: Cond, right: Cond) extends Cond(left.getFields ++ right.getFields) {
    override def fr: Fragment = left.fr ++ fr0" AND " ++ right.fr
  }

  final case class Or(left: Cond, right: Cond) extends Cond(left.getFields ++ right.getFields) {
    override def fr: Fragment = left.fr ++ fr0" OR " ++ right.fr
  }

  final case class Parentheses(cond: Cond) extends Cond(cond.getFields) {
    override def fr: Fragment = fr0"(" ++ cond.fr ++ fr0")"
  }

}
