package gospeak.infra.services.storage.sql.utils

import cats.data.NonEmptyList
import cats.effect.IO
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Select
import gospeak.libs.scala.Extensions._

trait GenericRepo {
  protected[sql] val xa: doobie.Transactor[IO]

  protected def runNel[Id, A](run: NonEmptyList[Id] => Select[A], ids: Seq[Id]): IO[List[A]] =
    ids.distinct.toNel.map(run(_).runList(xa)).getOrElse(IO.pure(List()))
}
