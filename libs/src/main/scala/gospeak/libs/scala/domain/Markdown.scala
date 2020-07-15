package gospeak.libs.scala.domain

import com.vladsch.flexmark.ext.emoji.{EmojiExtension, EmojiImageType}
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension
import org.jsoup.Jsoup

import scala.collection.JavaConverters._

final case class Markdown(value: String) extends AnyVal {
  def isEmpty: Boolean = value.trim.isEmpty

  def nonEmpty: Boolean = !isEmpty

  def toHtml: Html = Markdown.toHtml(this)

  def toText: String = Markdown.toText(this)
}

object Markdown {
  private val options = new MutableDataSet()
    // https://github.com/vsch/flexmark-java/wiki/Extensions#emoji
    .set(Parser.EXTENSIONS, Seq(EmojiExtension.create(): Extension).asJava)
    .set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_ONLY)
  private val parser = Parser.builder(options).build
  private val renderer = HtmlRenderer.builder(options).escapeHtml(true).build

  def toHtml(md: Markdown): Html = {
    val parsed = parser.parse(md.value)
    val html = renderer.render(parsed).trim
    Html(s"""<div class="markdown">$html</div>""")
  }

  def toText(md: Markdown): String = {
    val parsed = parser.parse(md.value)
    val html = renderer.render(parsed).trim
    Jsoup.parse(html).text()
  }
}
