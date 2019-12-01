package fr.gospeak.core.services

import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.libs.scalautils.domain.Markdown
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}

trait TemplateSrv {
  def render[A <: TemplateData](tmpl: MustacheTextTmpl[A], data: A): Either[String, String]

  def render[A <: TemplateData](tmpl: MustacheMarkdownTmpl[A], data: A): Either[String, Markdown]

  def render[A <: TemplateData](tmpl: MustacheTextTmpl[A]): Either[String, String]

  def render[A <: TemplateData](tmpl: MustacheMarkdownTmpl[A]): Either[String, Markdown]
}
