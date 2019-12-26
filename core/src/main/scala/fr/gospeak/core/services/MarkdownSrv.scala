package fr.gospeak.core.services

import fr.gospeak.libs.scalautils.domain.{Html, Markdown}

trait MarkdownSrv {
  def render(md: Markdown, classes: String = ""): Html
}
