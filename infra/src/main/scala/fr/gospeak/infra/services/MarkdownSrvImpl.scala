package fr.gospeak.infra.services

import com.vladsch.flexmark.ext.emoji.{EmojiExtension, EmojiImageType}
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import fr.gospeak.core.services.MarkdownSrv
import fr.gospeak.libs.scalautils.domain.{Html, Markdown}

import scala.collection.JavaConverters._

class MarkdownSrvImpl extends MarkdownSrv {
  override def render(md: Markdown): Html = MarkdownSrvImpl.render(md)
}

object MarkdownSrvImpl {
  private val options = new MutableDataSet()
    // https://github.com/vsch/flexmark-java/wiki/Extensions#emoji
    .set(Parser.EXTENSIONS, Seq(EmojiExtension.create()).asJava)
    .set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_ONLY)
  private val parser = Parser.builder(options).build
  private val renderer = HtmlRenderer.builder(options).escapeHtml(true).build

  def render(md: Markdown): Html = {
    val parsed = parser.parse(md.value)
    val content = renderer.render(parsed).trim
    Html(s"""<div class="markdown">$content</div>""")
  }
}