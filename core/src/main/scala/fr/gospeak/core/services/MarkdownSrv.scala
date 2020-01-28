package fr.gospeak.core.services

import gospeak.libs.scala.domain.{Html, Markdown}

trait MarkdownSrv {
  def render(md: Markdown, classes: String = ""): Html
}
