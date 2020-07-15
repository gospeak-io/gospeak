package gospeak.libs.scala

final case class Diff[+A](leftOnly: List[A],
                          both: List[(A, A)],
                          rightOnly: List[A])

object Diff {
  def empty[A]: Diff[A] = Diff[A](List(), List(), List())

  def apply(): Diff[Nothing] = new Diff(List(), List(), List())

  def from[A](left: Seq[A], right: Seq[A], eq: (A, A) => Boolean): Diff[A] = {
    val leftAssoc = left.map(a => a -> right.find(b => eq(a, b)))
    val leftOnly = leftAssoc.collect { case (a, None) => a }
    val both = leftAssoc.collect { case (a, Some(b)) => a -> b }
    val rightOnly = right.filter(a => !left.exists(b => eq(a, b)))
    Diff(leftOnly.toList, both.toList, rightOnly.toList)
  }

  def from[A](left: Seq[A], right: Seq[A]): Diff[A] = from[A](left, right, (a: A, b: A) => a == b)
}
