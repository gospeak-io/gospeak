package fr.gospeak.libs.scalautils.domain

sealed trait MarkdownTemplate {
  val value: String
}

object MarkdownTemplate {

  final case class Mustache(value: String) extends MarkdownTemplate

}
