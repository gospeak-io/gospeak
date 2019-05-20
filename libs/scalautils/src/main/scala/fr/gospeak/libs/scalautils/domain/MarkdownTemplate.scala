package fr.gospeak.libs.scalautils.domain

sealed trait MarkdownTemplate[+A] {
  val value: String
}

object MarkdownTemplate {

  final case class Mustache[A](value: String) extends MarkdownTemplate[A]

}
