package fr.gospeak.infra.services.storage.sql.utils

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{CustomException, Done}

trait GenericRepo {
  protected[sql] val xa: doobie.Transactor[IO]

  protected def run[A](i: A => doobie.Update0, v: A): IO[A] =
    i(v).run.transact(xa).mapFailure(e => new Exception(s"Unable to insert $v", e)).flatMap {
      case 1 => IO.pure(v)
      case code => IO.raiseError(CustomException(s"Failed to insert $v (code: $code)"))
    }

  protected def run(i: => doobie.Update0): IO[Done] =
    i.run.transact(xa).flatMap {
      case 1 => IO.pure(Done)
      case code => IO.raiseError(CustomException(s"Failed to update $i (code: $code)"))
    }

  protected def run[A](v: doobie.ConnectionIO[A]): IO[A] =
    v.transact(xa)

  protected def runIn[Id, A](selectAll: NonEmptyList[Id] => doobie.Query0[A])(ids: Seq[Id]): IO[Seq[A]] =
    NonEmptyList.fromList(ids.toList)
      .map(nel => run(selectAll(nel).to[List]))
      .getOrElse(IO.pure(Seq()))
}
