package gospeak.infra.services

import cats.effect.IO
import gospeak.infra.services.ScraperSrv._
import gospeak.libs.http.HttpClient
import gospeak.libs.http.HttpClient.Response
import gospeak.libs.scala.domain.{PageData, Url}
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import scala.collection.JavaConverters._

class ScraperSrv(http: HttpClient) {
  def fetchMetas(url: Url): IO[PageData] = http.get(url.value).map(extract(url, _))
}

object ScraperSrv {
  private val validLinks = Set("canonical", "alternate", "image_src", "shortcut icon", "icon", "apple-touch-icon")
  private val validHeaders = Set("Date", "Expires")

  private def extract(url: Url, r: Response): PageData = {
    val doc = Jsoup.parse(getHead(r.body))
    val title = doc.select("title").asScala.map(parseElement)
    val metas = doc.select("meta").asScala.map(parseElement)
    val links = doc.select("link").asScala.map(parseElement).filter(m => validLinks.contains(m.key))
    val headers = r.headers.filterKeys(validHeaders.contains).map { case (k, v) => PageData.FullItem(k, v, Map()) }
    val data = (title ++ metas ++ links ++ headers).toList.groupBy(_.key).mapValues(_.map(m => PageData.RowItem(m.value, m.attrs)))
    PageData.from(url, data)
  }

  // useful to not fetch link & meta tags from body but without relying on jsoup parsing as it fails on youtube pages (the end of head is treated as body because some div are present in head)
  private def getHead(html: String): String = html.split("<head>")(1).split("</head>")(0)

  private[services] def parseElement(e: Element): PageData.FullItem = {
    if (e.tagName() == "title") {
      PageData.FullItem("html:title", e.text(), Map())
    } else {
      val attrs = e.attributes().asScala.map(a => a.getKey.trim -> a.getValue.trim).filter { case (k, v) => k.nonEmpty && v.nonEmpty }.toMap
      if (e.tagName() == "meta") {
        attrs.get("charset").map { value =>
          PageData.FullItem("charset", value, attrs - "charset")
        }.getOrElse {
          val nameKey = Seq("property", "name", "id", "http-equiv", "itemprop").find(attrs.contains).getOrElse("")
          val name = attrs.getOrElse(nameKey, "")
          val value = attrs.getOrElse("content", "")
          if (name.nonEmpty) {
            PageData.FullItem(name, value, attrs - "content" - nameKey)
          } else {
            PageData.FullItem("", "", attrs)
          }
        }
      } else {
        val name = attrs.getOrElse("rel", "")
        val value = attrs.getOrElse("href", "")
        PageData.FullItem(name, value, attrs - "rel" - "href")
      }
    }
  }
}
