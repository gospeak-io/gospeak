package fr.gospeak.libs.scalautils

import cats.data.NonEmptyList
import fr.gospeak.libs.scalautils.domain.MultiException

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Extensions {

  implicit class BooleanExtension(val v: Boolean) extends AnyVal {
    def cond[A](a: => A): Option[A] = if (v) Some(a) else None

    def cond: Option[Unit] = cond(())
  }

  implicit class StringExtension(val v: String) extends AnyVal {
    def tryInt: Try[Int] = Try(v.toInt)

    def tryLong: Try[Long] = Try(v.toLong)

    def tryFloat: Try[Float] = Try(v.toFloat)

    def tryDouble: Try[Double] = Try(v.toDouble)
  }

  implicit class TraversableOnceExtension[A, M[X] <: TraversableOnce[X]](val v: M[A]) extends AnyVal {
    // move targeted element one place before (or after) in the collection
    def swap(elt: A, before: Boolean = true)(implicit cbf: CanBuildFrom[M[A], A, M[A]]): M[A] = {
      val coll = cbf(v)
      if (v.nonEmpty) {
        val list = v.toVector
        var i = 0
        while (i < list.length) {
          if (before && i < list.length - 1 && list(i + 1) == elt) {
            coll += list(i + 1)
            coll += list(i)
            i += 2
          } else if (!before && i < list.length - 1 && list(i) == elt) {
            coll += list(i + 1)
            coll += list(i)
            i += 2
          } else {
            coll += list(i)
            i += 1
          }
        }
      }
      coll.result()
    }
  }

  implicit class TraversableOnceTryExtension[A, M[X] <: TraversableOnce[X]](val v: M[Try[A]]) extends AnyVal {
    def sequence(implicit cbf: CanBuildFrom[M[Try[A]], A, M[A]]): Try[M[A]] = {
      val init = Try(cbf(v) -> Seq.empty[Throwable])
      v.foldLeft(init) { (acc, cur) =>
        acc.flatMap { case (results, errors) =>
          cur.map { result => (results += result, errors) }
            .recover { case NonFatal(error) => (results, error +: errors) }
        }
      }.flatMap(sequenceResult[A, M])
    }
  }

  implicit class TraversableOnceFutureExtension[A, M[X] <: TraversableOnce[X]](val v: M[Future[A]]) extends AnyVal {
    def sequence(implicit cbf: CanBuildFrom[M[Future[A]], A, M[A]], ec: ExecutionContext): Future[M[A]] = {
      val init = Future.successful(cbf(v) -> Seq.empty[Throwable])
      v.foldLeft(init) { (acc, cur) =>
        acc.flatMap { case (results, errors) =>
          cur.map { result => (results += result, errors) }
            .recover { case NonFatal(error) => (results, error +: errors) }
        }
      }.flatMap(sequenceResult[A, M](_).toFuture)
    }
  }

  implicit class OptionExtension[A](val in: Option[A]) extends AnyVal {
    def toTry(e: => Throwable): Try[A] = in match {
      case Some(v) => Success(v)
      case None => Failure(e)
    }

    def toTry(msg: String): Try[A] = toTry(new NoSuchElementException(msg))

    def toTry: Try[A] = toTry("None.toTry")

    def toEither[E](e: => E): Either[E, A] = in match {
      case Some(v) => Right(v)
      case None => Left(e)
    }

    def toEither: Either[Throwable, A] = toEither(new NoSuchElementException("None.toEither"))
  }

  implicit class TryExtension[A](val v: Try[A]) extends AnyVal {
    def mapFailure(f: Throwable => Throwable): Try[A] = v match {
      case Success(_) => v
      case Failure(e) => Failure(f(e))
    }

    def toFuture: Future[A] = v match {
      case Success(a) => Future.successful(a)
      case Failure(e) => Future.failed(e)
    }
  }

  implicit class FutureExtension[A](val v: Future[A]) extends AnyVal {
    def failWithTry(implicit ec: ExecutionContext): Future[Try[A]] =
      v.map(Success(_)).recover { case NonFatal(e) => Failure(e) }
  }

  private def sequenceResult[A, M[X] <: TraversableOnce[X]](in: (mutable.Builder[A, M[A]], Seq[Throwable])): Try[M[A]] = {
    val (results, errors) = in
    NonEmptyList.fromList(errors.reverse.toList)
      .map(errs => Failure(MultiException(errs)))
      .getOrElse(Success(results.result()))
  }

}
