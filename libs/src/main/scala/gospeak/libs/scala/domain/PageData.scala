package gospeak.libs.scala.domain

import gospeak.libs.scala.domain.PageData.{RowItem, SizedItem}

import scala.util.Try

/**
 * Metadata about a web page, extracted from html tags (meta, link, title...) and http headers
 */
case class PageData(url: Url,
                    title: Option[String],
                    description: Option[String],
                    image: Option[String],
                    author: Option[String],
                    icons: List[SizedItem],
                    color: Option[String],
                    keywords: List[String],
                    canonical: Option[String],
                    siteName: Option[String],
                    metas: Map[String, List[RowItem]])

object PageData {

  case class Size(width: Int, height: Int)

  case class SizedItem(value: String, size: Option[Size])

  case class RowItem(value: String, attrs: Map[String, String] = Map())

  case class FullItem(key: String, value: String, attrs: Map[String, String])

  def from(url: Url, metas: Map[String, List[RowItem]]): PageData = {
    PageData(
      url = url,
      title = getTitle(metas),
      description = getDescription(metas),
      image = getImage(metas),
      author = getAuthor(metas),
      icons = getIcons(metas),
      color = getColor(metas),
      keywords = getKeywords(metas),
      canonical = getCanonical(metas),
      siteName = getSiteName(metas),
      metas = metas)
  }

  private[domain] def getTitle(metas: Map[String, List[RowItem]]): Option[String] =
    getFirst(metas, List("title", "og:title", "twitter:title", "html:title")).map(_.value)

  private[domain] def getDescription(metas: Map[String, List[RowItem]]): Option[String] =
    getFirst(metas, List("description", "og:description", "twitter:description")).map(_.value)

  private[domain] def getImage(metas: Map[String, List[RowItem]]): Option[String] =
    getFirst(metas, List("image_src", "og:image", "twitter:image", "thumbnail")).map(_.value)

  private[domain] def getAuthor(metas: Map[String, List[RowItem]]): Option[String] =
    getFirst(metas, List("author")).map(_.value)

  private[domain] def getIcons(metas: Map[String, List[RowItem]]): List[SizedItem] =
    getAll(metas, List("shortcut icon", "icon", "apple-touch-icon", "msapplication-TileImage"))
      .map(i => SizedItem(i.value, i.attrs.get("sizes").flatMap(parseSize))).sortBy(-_.size.map(_.width).getOrElse(0))

  private[domain] def getColor(metas: Map[String, List[RowItem]]): Option[String] =
    getFirst(metas, List("theme-color", "msapplication-TileColor")).map(_.value)

  private[domain] def getKeywords(metas: Map[String, List[RowItem]]): List[String] =
    metas.getOrElse("keywords", List()).flatMap(_.value.split(",")).filterNot(_.contains("...")).map(_.trim).distinct

  private[domain] def getCanonical(metas: Map[String, List[RowItem]]): Option[String] =
    getFirst(metas, List("canonical", "og:url", "twitter:url")).map(_.value)

  private[domain] def getSiteName(metas: Map[String, List[RowItem]]): Option[String] =
    getFirst(metas, List("og:site_name", "al:ios:app_name", "al:android:app_name", "twitter:app:name:iphone", "twitter:app:name:ipad", "twitter:app:name:googleplay"))
      .map(_.value)

  private[domain] def getFirst(metas: Map[String, List[RowItem]], keys: List[String]): Option[FullItem] =
    keys.toStream.flatMap(k => metas.get(k).flatMap(_.headOption.map(v => FullItem(k, v.value, v.attrs)))).headOption

  private[domain] def getAll(metas: Map[String, List[RowItem]], keys: List[String]): List[FullItem] =
    keys.flatMap(k => metas.getOrElse(k, List()).map(v => FullItem(k, v.value, v.attrs)))

  private[domain] def parseSize(size: String): Option[Size] = Try {
    val List(x, y) = size.split('x').toList
    Size(x.toInt, y.toInt)
  }.toOption
}
