package gospeak.libs.scala.domain

import java.net.URL

import scala.util.Try

sealed class Url private(value: String, protected val parsed: Url.ParsedUrl) extends DataClass(value) {
  def handle: String = value.stripPrefix("http://").stripPrefix("https://")

  def host: String = parsed.host

  def path: Seq[String] = parsed.path
}

object Url {
  def from(in: String): Either[CustomException, Url] = {
    ParsedUrl.from(in).map { parsed =>
      val url = new Url(in, parsed)
      Twitter.from(url).toOption
        .orElse(YouTube.from(url).toOption)
        .orElse(Vimeo.from(url).toOption)
        .orElse(LinkedIn.from(url).toOption)
        .orElse(Meetup.from(url).toOption)
        .orElse(Github.from(url).toOption)
        .getOrElse(url)
    }.toEither.left.map(e => CustomException(s"'$in' is an invalid Url", Seq(CustomError(e.getMessage))))
  }

  sealed trait Video extends Url {
    val platform: String

    def videoId: String
  }

  object Video {
    def from(in: String): Either[CustomException, Url.Video] = Url.from(in).flatMap {
      case u: Url.Video => Right(u)
      case _ => Left(CustomException(s"'$in' is an invalid video Url"))
    }
  }

  final class Twitter private(url: Url) extends Url(url.value, url.parsed) {
    override def handle: String = username.map("@" + _).getOrElse(super.handle)

    def username: Option[String] = parsed.path.lastOption
  }

  object Twitter {
    private[domain] val regex = "https?://twitter\\.com/.*".r

    def from(u: Url): Either[CustomException, Twitter] = u.value match {
      case regex() => Right(new Twitter(u))
      case _ => Left(CustomException(s"'${u.value}' is not a Twitter url"))
    }

    def from(in: String): Either[CustomException, Twitter] = Url.from(in).flatMap(from)
  }

  final class LinkedIn private(url: Url) extends Url(url.value, url.parsed) {
    override def handle: String = parsed.path.filter(_ != "admin").lastOption.getOrElse(super.handle)
  }

  object LinkedIn {
    private val regex = "https?://(?:www\\.|fr\\.)?linkedin\\.com/.*".r

    def from(u: Url): Either[CustomException, LinkedIn] = u.value match {
      case regex() => Right(new LinkedIn(u))
      case _ => Left(CustomException(s"'${u.value}' is not a LinkedIn url"))
    }

    def from(in: String): Either[CustomException, LinkedIn] = Url.from(in).flatMap(from)
  }

  sealed class YouTube private(url: Url) extends Url(url.value, url.parsed) {
    override def handle: String =
      parsed.parameters.get("v")
        .orElse(parsed.parameters.get("list"))
        .orElse(parsed.path.filter(_ != "videos").lastOption)
        .getOrElse(super.handle)
  }

  object YouTube {
    private val regex1 = "https?://(?:www\\.)?youtube\\.com/.*".r
    private val regex2 = "https?://youtu\\.be/.*".r

    def from(u: Url): Either[CustomException, YouTube] = (u.value match {
      case regex1() => Right(new YouTube(u))
      case regex2() => Right(new YouTube(u))
      case _ => Left(CustomException(s"'${u.value}' is not a YouTube url"))
    }).map(y => Video.from(y).getOrElse(y))

    def from(in: String): Either[CustomException, YouTube] = Url.from(in).flatMap(from)

    final class Video private(url: Url) extends YouTube(url) with Url.Video {
      val platform: String = "YouTube"

      def videoId: String = handle
    }

    object Video {
      def from(u: YouTube): Either[CustomException, Video] =
        if (u.parsed.path == List("watch")) Right(new Video(u))
        else if (u.parsed.host == "youtu.be") Right(new Video(u))
        else Left(CustomException(s"'${u.value}' is not a YouTube video url"))

      def from(u: Url): Either[CustomException, Video] = YouTube.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Video] = YouTube.from(u).flatMap(from)
    }

  }

  sealed class Vimeo private(url: Url) extends Url(url.value, url.parsed) {
    override def handle: String = parsed.path.lastOption.getOrElse(super.handle)
  }

  object Vimeo {
    private val regex = "https?://(?:www\\.)?vimeo\\.com.*".r

    def from(u: Url): Either[CustomException, Vimeo] = (u.value match {
      case regex() => Right(new Vimeo(u))
      case _ => Left(CustomException(s"'${u.value}' is not a Vimeo url"))
    }).map(v => Video.from(v).getOrElse(v))

    def from(in: String): Either[CustomException, Vimeo] = Url.from(in).flatMap(from)

    final class Video private(url: Url) extends Vimeo(url) with Url.Video {
      val platform: String = "Vimeo"

      def videoId: String = handle
    }

    object Video {
      private val onlyNumbers = "[0-9]+".r

      def from(u: Vimeo): Either[CustomException, Video] = u.parsed.path match {
        case Seq(onlyNumbers()) => Right(new Video(u))
        case _ :+ "video" :+ onlyNumbers() => Right(new Video(u))
        case _ => Left(CustomException(s"'${u.value}' is not a Vimeo video url"))
      }

      def from(u: Url): Either[CustomException, Video] = Vimeo.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Video] = Vimeo.from(u).flatMap(from)
    }

  }

  final class Meetup private(url: Url) extends Url(url.value, url.parsed) {
    override def handle: String = parsed.path.lastOption.getOrElse(super.handle)
  }

  object Meetup {
    private val regex = "https?://(?:www\\.)?meetup\\.com/.*".r

    def from(u: Url): Either[CustomException, Meetup] = u.value match {
      case regex() => Right(new Meetup(u))
      case _ => Left(CustomException(s"'${u.value}' is not a Meetup url"))
    }

    def from(in: String): Either[CustomException, Meetup] = Url.from(in).flatMap(from)
  }

  final class Github private(url: Url) extends Url(url.value, url.parsed) {
    override def handle: String = parsed.path.lastOption.getOrElse(super.handle)
  }

  object Github {
    private val regex = "https?://github\\.com/.*".r

    def from(u: Url): Either[CustomException, Github] = u.value match {
      case regex() => Right(new Github(u))
      case _ => Left(CustomException(s"'${u.value}' is not a Github url"))
    }

    def from(in: String): Either[CustomException, Github] = Url.from(in).flatMap(from)
  }

  private[domain] case class ParsedUrl(protocol: String,
                                       host: String,
                                       port: Option[Int],
                                       path: List[String],
                                       parameters: Map[String, String],
                                       fragment: Option[String]) {
    def domain: String = host.split('.').takeRight(2).mkString(".")

    def file: Option[String] = path.lastOption.filter(_.contains('.'))
  }

  object ParsedUrl {
    def from(in: String): Try[ParsedUrl] = Try(new URL(in)).map { u =>
      ParsedUrl(
        protocol = u.getProtocol,
        host = u.getHost,
        port = Option(u.getPort).filter(_ != -1),
        path = u.getPath.split('/').filter(_.nonEmpty).toList,
        parameters = Option(u.getQuery).getOrElse("")
          .split('&')
          .filter(_.contains("="))
          .map(p => p.splitAt(p.indexOf('='))).toMap
          .mapValues(_.stripPrefix("=")),
        fragment = Option(u.getRef))
    }

  }

}
