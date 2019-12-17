package fr.gospeak.libs.scalautils.domain

import fr.gospeak.libs.scalautils.Crypto
import fr.gospeak.libs.scalautils.Extensions._

import scala.util.{Failure, Success, Try}

abstract class Image(val url: Url) {
  def value: String = url.value

  def thumbnail: String = transform(Seq("w_50", "c_scale"))

  def isCloudinary: Boolean = Image.CloudinaryUrl.parse(url.value).isSuccess

  def isGravatar: Boolean = Image.GravatarUrl.parse(url.value).isSuccess

  private def transform(transformations: Seq[String]*): String =
    Image.CloudinaryUrl.parse(url.value).map(_.transform(transformations: _*).value).getOrElse(url.value)
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

    def toUrl: Url = Url.from(value).get

    def transform(txs: Seq[String]*): CloudinaryUrl = copy(transformations = transformations ++ txs)
  }

  object CloudinaryUrl {
    private val cloudinaryRegex = "https://res.cloudinary.com/([^/]+)/([^/]+)/([^/]+)((?:/[a-z]{1,3}_[^/]+)*)(?:/v([0-9]{10}))?/([^.]+)\\.([a-z]+)".r

    def parse(url: String): Try[CloudinaryUrl] = url match {
      case cloudinaryRegex(cloudName, resource, kind, transformations, version, id, format) =>
        val txs = Option(transformations).filter(_.nonEmpty).map(_.stripPrefix("/").split("/").toSeq.map(_.split(",").toSeq)).getOrElse(Seq())
        Option(version).map(v => Try(v.toLong)).sequence.map { v =>
          CloudinaryUrl(cloudName, resource, kind, txs, v, id, format)
        }
      case _ => Failure(new IllegalArgumentException(s"Unable to parse '$url' as Image.CloudinaryUrl"))
    }
  }

  final case class GravatarUrl(hash: String,
                               params: Seq[(String, String)]) {
    def value: String = {
      val queryParams = params.map { case (key, value) => s"$key=$value" }.mkString("&")
      s"https://secure.gravatar.com/avatar/$hash" + Some(queryParams).filter(_.nonEmpty).map("?" + _).getOrElse("")
    }
  }

  object GravatarUrl {
    private val gravatarRegex = "https://secure.gravatar.com/avatar/([0-9a-f]{32})(\\?.*)?".r

    def apply(email: EmailAddress, params: Seq[(String, String)]): GravatarUrl =
      GravatarUrl(Crypto.md5(email.value.trim.toLowerCase), params)

    def parse(url: String): Try[GravatarUrl] = url match {
      case gravatarRegex(hash, queryParams) =>
        val params = Option(queryParams)
          .map(_.stripPrefix("?").split("&").toSeq).getOrElse(Seq())
          .map(p => p.splitAt(p.indexOf("=")))
          .map { case (key, value) => (key, value.stripPrefix("=")) }
        Success(GravatarUrl(hash, params))
      case _ => Failure(new IllegalArgumentException(s"Unable to parse '$url' as Image.GravatarUrl"))
    }
  }

}
