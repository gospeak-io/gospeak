package fr.gospeak.libs.scalautils.domain

import fr.gospeak.libs.scalautils.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}

sealed trait MustacheTmpl[+A] {
  val value: String

  def asText: MustacheTextTmpl[A] = MustacheTextTmpl[A](value)

  def asMarkdown: MustacheMarkdownTmpl[A] = MustacheMarkdownTmpl[A](value)
}

object MustacheTmpl {

  final case class MustacheTextTmpl[+A](value: String) extends MustacheTmpl[A]

  final case class MustacheMarkdownTmpl[+A](value: String) extends MustacheTmpl[A]

}
