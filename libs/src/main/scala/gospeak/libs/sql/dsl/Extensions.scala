package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.Fragment
import doobie.implicits._
import doobie.util.fragment.Fragment.const0

import scala.language.higherKinds

object Extensions {

  implicit class FragmentNonEmptyListExtension(val in: NonEmptyList[Fragment]) extends AnyVal {
    def mkFragment(sep: Fragment): Fragment = in.tail.foldLeft(in.head)(_ ++ sep ++ _)

    def mkFragment(sep: String): Fragment = mkFragment(const0(sep))
  }

  implicit class FragmentTraversableOnceExtension[M[X] <: TraversableOnce[X]](val in: M[Fragment]) extends AnyVal {
    def mkFragment(sep: Fragment): Fragment = in.toList match {
      case Nil => fr0""
      case head :: Nil => head
      case head :: tail => tail.foldLeft(head)(_ ++ sep ++ _)
    }

    def mkFragment(sep: String): Fragment = mkFragment(const0(sep))
  }

}
