package fr.gospeak.libs.scalautils.domain

abstract class Image(val url: Url) {
  def value: String = url.value
}

object Image {
  private val cloudinaryRegex = "https://res.cloudinary.com/([^/]+)/image/upload((?:/[^v][^/]+)*)(?:/(v[0-9]{10}))?/([^.]+)\\.([a-z]+)".r

  final case class CloudinaryUrl(cloudName: String,
                                 transformations: Seq[Seq[String]],
                                 version: Option[String],
                                 publicId: String,
                                 format: String)

  private[domain] def parseCloudinaryUrl(url: String): Option[CloudinaryUrl] = url match {
    case cloudinaryRegex(cloudName, transformations, version, id, format) =>
      val tx = Option(transformations).filter(_.nonEmpty).map(_.stripPrefix("/").split("/").toSeq.map(_.split(",").toSeq)).getOrElse(Seq())
      Some(CloudinaryUrl(cloudName, tx, Option(version), id, format))
    case _ => None
  }
}
