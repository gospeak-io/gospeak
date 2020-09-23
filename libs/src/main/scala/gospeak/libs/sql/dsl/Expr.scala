package gospeak.libs.sql.dsl

import doobie.implicits._
import doobie.util.Put
import doobie.util.fragment.Fragment

sealed abstract class Expr {
  def fr: Fragment

  def *(e: Expr): Expr = Expr.Times(this, e)
}

object Expr {

  final case class Value[A: Put](v: A) extends Expr {
    override def fr: Fragment = fr0"$v"
  }

  final case class Random() extends Expr {
    override def fr: Fragment = fr0"RANDOM()"
  }

  final case class SubQuery[A](q: Query.Select[A]) extends Expr {
    override def fr: Fragment = fr0"(" ++ q.fr ++ fr0")"
  }

  final case class Floor(e: Expr) extends Expr {
    override def fr: Fragment = fr0"FLOOR(" ++ e.fr ++ fr0")"
  }

  final case class Times(e1: Expr, e2: Expr) extends Expr {
    override def fr: Fragment = e1.fr ++ fr0" * " ++ e2.fr
  }

}
