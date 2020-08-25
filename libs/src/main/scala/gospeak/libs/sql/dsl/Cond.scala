package gospeak.libs.sql.dsl

import doobie.implicits._
import doobie.util.fragment.Fragment

case class Cond(fr: Fragment) {
  def and(c: Cond): Cond = Cond(fr ++ fr0" AND " ++ c.fr)

  def or(c: Cond): Cond = Cond(fr ++ fr0" OR " ++ c.fr)
}
