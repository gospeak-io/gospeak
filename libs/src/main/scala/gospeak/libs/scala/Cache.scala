package gospeak.libs.scala

import cats.effect.IO

import scala.collection.mutable

object Cache {
  def memoize[A, B](f: A => B): A => B = new mutable.HashMap[A, B]() {
    override def apply(key: A): B = getOrElseUpdate(key, f(key))
  }

  def memoizeIO[A, B](f: A => IO[B]): A => IO[B] = {
    val cache = mutable.HashMap[A, B]()
    (a: A) => cache.get(a).map(IO.pure).getOrElse(f(a).map { b => cache.put(a, b); b })
  }
}
