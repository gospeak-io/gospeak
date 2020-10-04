package gospeak.libs.sql.dsl

import doobie.syntax.string._
import doobie.util.Put
import doobie.util.fragment.Fragment
import gospeak.libs.sql.dsl.Cond.LikeExpr

sealed abstract class Expr(fields: List[Field[_]]) {
  def fr: Fragment

  def sql: String = fr.query.sql

  def getFields: List[Field[_]] = fields

  def *(e: Expr): Expr = Expr.Times(this, e)

  def like(value: String): Cond = LikeExpr(this, value)
}

object Expr {

  final case class Value[A: Put](v: A) extends Expr(List()) {
    override def fr: Fragment = fr0"$v"
  }

  final case class ValueField[A](f: Field[A]) extends Expr(List(f)) {
    override def fr: Fragment = f.fr
  }

  final case class Random() extends Expr(List()) {
    override def fr: Fragment = fr0"RANDOM()"
  }

  final case class SubQuery[A](q: Query.Select[A]) extends Expr(List()) {
    override def fr: Fragment = fr0"(" ++ q.fr ++ fr0")"
  }

  final case class Lower(e: Expr) extends Expr(e.getFields) {
    override def fr: Fragment = fr0"LOWER(" ++ e.fr ++ fr0")"
  }

  final case class Floor(e: Expr) extends Expr(e.getFields) {
    override def fr: Fragment = fr0"FLOOR(" ++ e.fr ++ fr0")"
  }

  final case class Times(e1: Expr, e2: Expr) extends Expr(e1.getFields ++ e2.getFields) {
    override def fr: Fragment = e1.fr ++ fr0" * " ++ e2.fr
  }

}
