package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import doobie.util.{Put, Read}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Page
import gospeak.libs.sql.dsl.Exceptions.{FailedQuery, InvalidNumberOfFields, InvalidNumberOfValues}
import gospeak.libs.sql.dsl.Extensions._
import gospeak.libs.sql.dsl.Query.Inner.{OrderByClause, WhereClause}

import scala.util.control.NonFatal

sealed trait Query[A] {
  def fr: Fragment

  def run(xa: doobie.Transactor[IO]): IO[A]
}

object Query {

  case class Insert[T <: Table.SqlTable](table: T, values: Fragment) extends Query[Unit] {
    def fr: Fragment = const0(s"INSERT INTO ${table.getName} (${table.getFields.map(_.name).mkString(", ")}) VALUES (") ++ values ++ fr0")"

    def run(xa: doobie.Transactor[IO]): IO[Unit] =
      exec(fr, _.update.run, xa).flatMap {
        case 1 => IO.pure(())
        case code => IO.raiseError(new Exception(s"Failed to insert values in $table (code: $code)"))
      }
  }

  object Insert {

    case class Builder[T <: Table.SqlTable](private val table: T) {
      def values[A: Put](a: A): Insert[T] = build(1, fr0"$a")

      def values[A: Put, B: Put](a: A, b: B): Insert[T] = build(2, fr0"$a, $b")

      def values[A: Put, B: Put, C: Put](a: A, b: B, c: C): Insert[T] = build(3, fr0"$a, $b, $c")

      def values[A: Put, B: Put, C: Put, D: Put](a: A, b: B, c: C, d: D): Insert[T] = build(4, fr0"$a, $b, $c, $d")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put](a: A, b: B, c: C, d: D, e: E): Insert[T] = build(5, fr0"$a, $b, $c, $d, $e")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put](a: A, b: B, c: C, d: D, e: E, f: F): Insert[T] = build(6, fr0"$a, $b, $c, $d, $e, $f")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G): Insert[T] = build(7, fr0"$a, $b, $c, $d, $e, $f, $g")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H): Insert[T] = build(8, fr0"$a, $b, $c, $d, $e, $f, $g, $h")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I): Insert[T] = build(9, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J): Insert[T] = build(10, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K): Insert[T] = build(11, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L): Insert[T] = build(12, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M): Insert[T] = build(13, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N): Insert[T] = build(14, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put, O: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O): Insert[T] = build(15, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n, $o")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put, O: Put, P: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P): Insert[T] = build(16, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n, $o, $p")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put, O: Put, P: Put, Q: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q): Insert[T] = build(17, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n, $o, $p, $q")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put, O: Put, P: Put, Q: Put, R: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R): Insert[T] = build(18, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n, $o, $p, $q, $r")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put, O: Put, P: Put, Q: Put, R: Put, S: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R, s: S): Insert[T] = build(19, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n, $o, $p, $q, $r, $s")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put, O: Put, P: Put, Q: Put, R: Put, S: Put, U: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R, s: S, u: U): Insert[T] = build(20, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n, $o, $p, $q, $r, $s, $u")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put, O: Put, P: Put, Q: Put, R: Put, S: Put, U: Put, V: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R, s: S, u: U, v: V): Insert[T] = build(21, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n, $o, $p, $q, $r, $s, $u, $v")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put, O: Put, P: Put, Q: Put, R: Put, S: Put, U: Put, V: Put, W: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R, s: S, u: U, v: V, w: W): Insert[T] = build(22, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n, $o, $p, $q, $r, $s, $u, $v, $w")

      def values[A: Put, B: Put, C: Put, D: Put, E: Put, F: Put, G: Put, H: Put, I: Put, J: Put, K: Put, L: Put, M: Put, N: Put, O: Put, P: Put, Q: Put, R: Put, S: Put, U: Put, V: Put, W: Put, X: Put](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R, s: S, u: U, v: V, w: W, x: X): Insert[T] = build(23, fr0"$a, $b, $c, $d, $e, $f, $g, $h, $i, $j, $k, $l, $m, $n, $o, $p, $q, $r, $s, $u, $v, $w, $x")

      private def build(size: Int, fr: Fragment) =
        if (size == table.getFields.length) Insert(table, fr)
        else throw InvalidNumberOfValues(table, size)
    }

  }

  case class Update[T <: Table.SqlTable](table: T, values: List[Fragment], where: WhereClause) extends Query[Unit] {
    def fr: Fragment = fr0"UPDATE " ++ table.fr ++ fr0" SET " ++ values.mkFragment(", ") ++ where.fr

    def run(xa: doobie.Transactor[IO]): IO[Unit] =
      exec(fr, _.update.run, xa).flatMap {
        case 1 => IO.pure(())
        case code => IO.raiseError(new Exception(s"Failed to update ${fr.update} (code: $code)"))
      }
  }

  object Update {

    case class Builder[T <: Table.SqlTable](private val table: T,
                                            private val values: List[Fragment]) {
      def set[A: Put](field: T => Field[A, _], value: A): Builder[T] = copy(values = values :+ (const0(field(table).name) ++ fr0"=$value"))

      def setOpt[A: Put](field: T => Field[Option[A], _], value: A): Builder[T] = copy(values = values :+ (const0(field(table).name) ++ fr0"=$value"))

      def all: Update[T] = Update(table, values, WhereClause(None))

      def where(cond: Cond): Update[T] = Exceptions.check(cond, table, Update(table, values, WhereClause(Some(cond))))

      def where(cond: T => Cond): Update[T] = where(cond(table))
    }

  }

  case class Delete[T <: Table.SqlTable](table: T, where: WhereClause) extends Query[Unit] {
    def fr: Fragment = fr0"DELETE FROM " ++ table.fr ++ where.fr

    def run(xa: doobie.Transactor[IO]): IO[Unit] =
      exec(fr, _.update.run, xa).flatMap {
        case 1 => IO.pure(())
        case code => IO.raiseError(new Exception(s"Failed to delete ${fr.update} (code: $code)"))
      }
  }

  object Delete {

    case class Builder[T <: Table.SqlTable](private val table: T) {
      def all: Delete[T] = Delete(table, WhereClause(None))

      def where(cond: Cond): Delete[T] = Exceptions.check(cond, table, Delete(table, WhereClause(Some(cond))))

      def where(cond: T => Cond): Delete[T] = where(cond(table))
    }

  }

  sealed abstract class Select[A: Read](table: Table, fields: List[Field[_, Table.SqlTable]], where: WhereClause, orderBy: OrderByClause) {
    def fr: Fragment = fr0"SELECT " ++ fields.map(_.fr).mkFragment(", ") ++ fr0" FROM " ++ table.fr ++ where.fr ++ orderBy.fr
  }

  object Select {

    case class All[A: Read](table: Table, fields: List[Field[_, Table.SqlTable]], where: WhereClause, orderBy: OrderByClause) extends Select[A](table, fields, where, orderBy) with Query[List[A]] {
      def run(xa: doobie.Transactor[IO]): IO[List[A]] = exec(fr, _.query[A].to[List], xa)
    }

    case class Paginated[A: Read](table: Table, fields: List[Field[_, Table.SqlTable]], where: WhereClause, orderBy: OrderByClause, params: Page.Params) extends Select[A](table, fields, where, orderBy) with Query[Page[A]] {
      override def fr: Fragment = super.fr ++ fr0" LIMIT " ++ const0(params.pageSize.value.toString) ++ fr0" OFFSET " ++ const0(params.offset.value.toString)

      def countFr: Fragment = fr0"SELECT COUNT(*) FROM (SELECT " ++ fields.headOption.map(_.fr).getOrElse(fr0"*") ++ fr0" FROM " ++ table.fr ++ where.fr ++ fr0") as cnt"

      def run(xa: doobie.Transactor[IO]): IO[Page[A]] = exec(fr, fr => for {
        elts <- fr.query[A].to[List]
        total <- countFr.query[Long].unique
      } yield Page(elts, params, Page.Total(total)), xa)
    }

    case class Optional[A: Read](table: Table, fields: List[Field[_, Table.SqlTable]], where: WhereClause, orderBy: OrderByClause, limit: Boolean) extends Select[A](table, fields, where, orderBy) with Query[Option[A]] {
      override def fr: Fragment = super.fr ++ (if (limit) fr0" LIMIT 1" else fr0"")

      def run(xa: doobie.Transactor[IO]): IO[Option[A]] = exec(fr, _.query[A].option, xa)
    }

    case class One[A: Read](table: Table, fields: List[Field[_, Table.SqlTable]], where: WhereClause, orderBy: OrderByClause) extends Select[A](table, fields, where, orderBy) with Query[A] {
      def run(xa: doobie.Transactor[IO]): IO[A] = exec(fr, _.query[A].unique, xa)
    }

    case class Exists[A: Read](table: Table, fields: List[Field[_, Table.SqlTable]], where: WhereClause, orderBy: OrderByClause) extends Select[A](table, fields, where, orderBy) with Query[Boolean] {
      def run(xa: doobie.Transactor[IO]): IO[Boolean] = exec(fr, _.query[A].option.map(_.isDefined), xa)
    }

    case class Builder[T <: Table](private val table: T,
                                   private val fields: List[Field[_, Table.SqlTable]],
                                   private val where: WhereClause,
                                   private val orderBy: OrderByClause) {
      def fields(fields: Field[_, Table.SqlTable]*): Builder[T] = copy(fields = fields.toList)

      def fields(fields: List[Field[_, Table.SqlTable]]): Builder[T] = copy(fields = fields)

      def where(cond: Cond): Builder[T] = Exceptions.check(cond, table, copy(where = WhereClause(Some(cond))))

      def where(cond: T => Cond): Builder[T] = where(cond(table))

      def all[A: Read]: Select.All[A] = build[A, Select.All[A]](new Select.All[A](table, fields, where, orderBy))

      def page[A: Read](params: Page.Params): Select.Paginated[A] = build[A, Select.Paginated[A]](new Select.Paginated[A](table, fields, where, orderBy, params))

      def option[A: Read](limit: Boolean = false): Select.Optional[A] = build[A, Select.Optional[A]](new Select.Optional[A](table, fields, where, orderBy, limit))

      def option[A: Read]: Select.Optional[A] = option[A]()

      def one[A: Read]: Select.One[A] = build[A, Select.One[A]](new Select.One[A](table, fields, where, orderBy))

      def exists[A: Read]: Select.Exists[A] = build[A, Select.Exists[A]](new Select.Exists[A](table, fields, where, orderBy))

      private def build[A: Read, S <: Select[A]](res: => S): S =
        if (implicitly[Read[A]].length == fields.length) res else throw InvalidNumberOfFields(implicitly[Read[A]], fields)
    }

  }

  object Inner {

    case class WhereClause(cond: Option[Cond]) {
      def fr: Fragment = cond.map(fr0" WHERE " ++ _.fr).getOrElse(fr0"")
    }

    case class OrderByClause(fields: List[Field.Order[_, Table.SqlTable]]) {
      def fr: Fragment = NonEmptyList.fromList(fields).map(fr0" ORDER BY " ++ _.map(_.fr).mkFragment(", ")).getOrElse(fr0"")
    }

  }

  private def exec[A](fr: Fragment, run: Fragment => doobie.ConnectionIO[A], xa: doobie.Transactor[IO]): IO[A] =
    run(fr).transact(xa).recoverWith { case NonFatal(e) => IO.raiseError(FailedQuery(fr, e)) }
}
