package gospeak.libs.sql.dsl

import cats.effect.IO
import doobie.implicits._
import doobie.util.Read
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import gospeak.libs.scala.Extensions._

import scala.util.control.NonFatal

object Query {

  case class SelectBuilder[T <: Table](table: T,
                                       fields: List[Field[_, _]],
                                       whereOpt: Option[Cond]) {
    def fields(fields: Field[_, _]*): SelectBuilder[T] = copy(fields = fields.toList)

    def fields(fields: List[Field[_, _]]): SelectBuilder[T] = copy(fields = fields)

    def where(cond: Cond): SelectBuilder[T] = copy(whereOpt = Some(cond))

    def where(cond: T => Cond): SelectBuilder[T] = where(cond(table))

    def as[A: Read]: Select[A] =
      if (implicitly[Read[A]].length == fields.length) Select[A](table, fields, whereOpt)
      else throw new Exception(s"Expects ${implicitly[Read[A]].length} fields but got ${fields.length}")
  }

  case class Select[A: Read](private val table: Table,
                             private val fields: List[Field[_, _]],
                             private val whereOpt: Option[Cond]) {
    def fr: Fragment = {
      val fieldsFr = fields.tail.map(const0(", ") ++ _.fr).foldLeft(fields.head.fr)(_ ++ _)
      val whereFr = whereOpt.map(w => fr0" WHERE " ++ w.fr).getOrElse(fr0"")

      fr0"SELECT " ++ fieldsFr ++ fr0" FROM " ++ table.fr ++ whereFr
    }

    def runList(xa: doobie.Transactor[IO]): IO[List[A]] = exec(fr, _.query[A].to[List], xa)

    def runOption(xa: doobie.Transactor[IO]): IO[Option[A]] = exec(fr, _.query[A].option, xa)

    def runUnique(xa: doobie.Transactor[IO]): IO[A] = exec(fr, _.query[A].unique, xa)

    def runExists(xa: doobie.Transactor[IO]): IO[Boolean] = exec(fr, _.query[A].option.map(_.isDefined), xa)
  }

  private def exec[A](fr: Fragment, run: Fragment => doobie.ConnectionIO[A], xa: doobie.Transactor[IO]): IO[A] =
    run(fr).transact(xa).recoverWith { case NonFatal(e) => IO.raiseError(new Exception(s"Fail on ${fr.query.sql}: ${e.getMessage}", e)) }
}
