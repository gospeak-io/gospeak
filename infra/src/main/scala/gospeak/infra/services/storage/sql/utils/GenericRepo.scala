package gospeak.infra.services.storage.sql.utils

import cats.data.NonEmptyList
import cats.effect.IO
import DoobieUtils.Select

trait GenericRepo {
  protected[sql] val xa: doobie.Transactor[IO]

  protected def runNel[Id, A](run: NonEmptyList[Id] => Select[A], ids: Seq[Id]): IO[Seq[A]] =
    NonEmptyList.fromList(ids.toList.distinct).map(run(_).runList(xa)).getOrElse(IO.pure(Seq()))
}
