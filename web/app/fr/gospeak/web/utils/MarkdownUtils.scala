package fr.gospeak.web.utils

import com.vladsch.flexmark.ext.emoji.{EmojiExtension, EmojiImageType}
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import fr.gospeak.libs.scalautils.domain.{Html, Markdown}

import scala.collection.JavaConverters._

object MarkdownUtils {
  private val options = new MutableDataSet()
    // https://github.com/vsch/flexmark-java/wiki/Extensions#emoji
    .set(Parser.EXTENSIONS, Seq(EmojiExtension.create()).asJava)
    .set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_ONLY)
  private val parser = Parser.builder(options).build
  private val renderer = HtmlRenderer.builder(options).escapeHtml(true).build
  private val rendererHtml = HtmlRenderer.builder(options).escapeHtml(false).build

  def render(md: Markdown): Html = {
    val content = renderer.render(parser.parse(md.value)).trim
    Html(s"""<div class="markdown">$content</div>""")
  }

  def renderHtml(md: Markdown): Html = {
    Html(rendererHtml.render(parser.parse(md.value)))
  }
}
