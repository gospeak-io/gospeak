package gospeak.infra.services.storage.sql.utils

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.util.fragment.Fragment
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.StringUtils
import gospeak.libs.sql.dsl.Query

trait GenericRepo {
  protected[sql] val xa: doobie.Transactor[IO]

  protected def runNel[Id, A](run: NonEmptyList[Id] => Query.Select.All[A], ids: List[Id]): IO[List[A]] =
    ids.distinct.toNel.map(run(_).run(xa)).getOrElse(IO.pure(List()))
}

object GenericRepo {
  def assertEqual(fr1: Fragment, fr2: Fragment, allowReorder: Boolean = false): Unit = {
    val (a1, a2) = (fr1.update.sql, fr2.update.sql)
    if (a1 != a2) {
      val prefix = StringUtils.identicalPrefixLength(a1, a2)
      val (b1, b2) = (a1.drop(prefix), a2.drop(prefix))
      val suffix = StringUtils.identicalSuffixLength(b1, b2)
      val (c1, c2) = (b1.dropRight(suffix), b2.dropRight(suffix))
      if (c1.trim.isEmpty && c2.trim.isEmpty) {
        // do nothing, diff is only white spaces
      } else if (s"(${c1.trim})" == c2.trim || s"(${c2.trim})" == c1.trim) {
        // do nothing, only added parenthesis
      } else if(c1.replaceAll("[()]", "") == c2.replaceAll("[()]", "")) {
        println(s"Parenthesis diff:\n - old: $c1\n - new: $c2")
        // do nothing, only diff is parenthesis
      } else if (allowReorder && c1.sorted == c2.sorted) {
        println(s"Reordered:\n - old: $c1\n - new: $c2")
        // do nothing, same letters but in different order (field list not in the same order)
      } else {
        val p = if (prefix > 0) s"[..$prefix..]" else ""
        val s = if (suffix > 0) s"[..$suffix..]" else ""
        throw new Exception(
          s"""Not identical queries:
             |old: $a1
             |new: $a2
             |old_diff: $p$c1$s
             |new_diff: $p$c2$s
             |""".stripMargin.trim)
      }
    }
  }
}
