package gospeak.infra.services.storage.sql.utils

import cats.data.NonEmptyList
import cats.effect.IO
import gospeak.libs.scala.Extensions._
import gospeak.libs.sql.doobie.Query

trait GenericRepo {
  protected[sql] val xa: doobie.Transactor[IO]

  protected def runNel[Id, A](run: NonEmptyList[Id] => Query.Select[A], ids: Seq[Id]): IO[List[A]] =
    ids.distinct.toNel.map(run(_).runList(xa)).getOrElse(IO.pure(List()))
}
