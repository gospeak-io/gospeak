package gospeak.infra.services.storage.sql.utils

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.util.fragment.Fragment
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.StringUtils
import gospeak.libs.sql.doobie.Query

trait GenericRepo {
  protected[sql] val xa: doobie.Transactor[IO]

  protected def runNel[Id, A](run: NonEmptyList[Id] => Query.Select[A], ids: List[Id]): IO[List[A]] =
    ids.distinct.toNel.map(run(_).runList(xa)).getOrElse(IO.pure(List()))
}

object GenericRepo {
  def assertEqual(fr1: Fragment, fr2: Fragment): Unit = {
    val (q1, q2) = (fr1.update.sql, fr2.update.sql)
    if (q1 != q2) {
      val prefix = StringUtils.identicalPrefix(q1, q2)
      val suffix = StringUtils.identicalSuffix(q1.drop(prefix), q2.drop(prefix))
      val d1 = q1.drop(prefix).dropRight(suffix)
      val d2 = q2.drop(prefix).dropRight(suffix)
      if (d1.trim.isEmpty && d2.trim.isEmpty) {
        // do nothing, diff is only white spaces
      } else if (s"($d1)" == d2 || s"($d2)" == d1) {
        // do nothing, only added parenthesis
      } else {
        val p = if (prefix > 0) s"[..$prefix..]" else ""
        val s = if (suffix > 0) s"[..$suffix..]" else ""
        throw new Exception(
          s"""Not identical queries:
             |old: $q1
             |new: $q2
             |old_diff: $p$d1$s
             |new_diff: $p$d2$s
             |""".stripMargin.trim)
      }
    }
  }
}
