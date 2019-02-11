package fr.gospeak.libs.scalautils

import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

object Extensions {

  implicit class StringExtension(val v: String) extends AnyVal {
    def tryInt: Try[Int] = Try(v.toInt)

    def tryLong: Try[Long] = Try(v.toLong)

    def tryFloat: Try[Float] = Try(v.toFloat)

    def tryDouble: Try[Double] = Try(v.toDouble)
  }

  implicit class TraversableOnceExtension[A, M[X] <: TraversableOnce[X]](val v: M[A]) extends AnyVal {
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

  implicit class OptionExtension[A](val in: Option[A]) extends AnyVal {
    def toTry(e: => Throwable): Try[A] = in match {
      case Some(v) => Success(v)
      case None => Failure(e)
    }
  }

}
