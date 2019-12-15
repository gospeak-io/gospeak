package fr.gospeak.libs.scalautils.domain

import fr.gospeak.libs.scalautils.Extensions._

import scala.util.Try

abstract class Image(val url: Url) {
  def value: String = url.value

  def thumbnail: String = transform(Seq("w_50", "c_scale"))

  private def transform(transformations: Seq[String]*): String =
    Image.CloudinaryUrl.parse(url.value).map(_.transform(transformations).value).getOrElse(url.value)
}

object Image {

  final case class CloudinaryUrl(cloudName: String,
                                 resource: String,
                                 kind: String,
                                 transformations: Seq[Seq[String]],
                                 version: Option[Long],
                                 publicId: String,
                                 format: String) {
    def value: String = {
      val txs = transformations.map("/" + _.mkString(",")).mkString
      val v = version.map("/v" + _).getOrElse("")
      s"https://res.cloudinary.com/$cloudName/$resource/$kind$txs$v/$publicId.$format"
    }

    def transform(txs: Seq[Seq[String]]): CloudinaryUrl = copy(transformations = transformations ++ txs)
  }

  object CloudinaryUrl {
    private val cloudinaryRegex = "https://res.cloudinary.com/([^/]+)/([^/]+)/([^/]+)((?:/[a-z]{1,3}_[^/]+)*)(?:/v([0-9]{10}))?/([^.]+)\\.([a-z]+)".r

    private[domain] def parse(url: String): Option[CloudinaryUrl] = url match {
      case cloudinaryRegex(cloudName, resource, kind, transformations, version, id, format) =>
        val txs = Option(transformations).filter(_.nonEmpty).map(_.stripPrefix("/").split("/").toSeq.map(_.split(",").toSeq)).getOrElse(Seq())
        Option(version).map(v => Try(v.toLong)).sequence.toOption.map { v =>
          CloudinaryUrl(cloudName, resource, kind, txs, v, id, format)
        }
      case _ => None
    }
  }

}
