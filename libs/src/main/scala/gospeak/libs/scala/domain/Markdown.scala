package gospeak.libs.scala.domain

final case class Markdown(value: String) extends AnyVal {
  def isEmpty: Boolean = value.trim.isEmpty

  def nonEmpty: Boolean = !isEmpty
}
