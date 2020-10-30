package gospeak.libs.scala.domain

import java.net.URL

import scala.util.Try

sealed class Url private(value: String, protected val parsed: Url.ParsedUrl) extends DataClass(value) {
  def handle: String = value.stripPrefix("http://").stripPrefix("https://")

  def host: String = parsed.host

  def path: List[String] = parsed.path
}

object Url {
  def from(in: String): Either[CustomException, Url] = {
    ParsedUrl.from(in).map { parsed =>
      val url = new Url(in, parsed)
      Twitter.from(url).toOption
        .orElse(YouTube.from(url).toOption)
        .orElse(Vimeo.from(url).toOption)
        .orElse(Infoq.from(url).toOption)
        .orElse(LinkedIn.from(url).toOption)
        .orElse(Meetup.from(url).toOption)
        .orElse(Github.from(url).toOption)
        .getOrElse(url)
    }.toEither.left.map(e => CustomException(s"'$in' is an invalid Url", List(CustomError(e.getMessage))))
  }

  final class Slides private(url: Url) extends Url(url.value, url.parsed)

  object Slides {
    def from(url: Url): Either[CustomException, Url.Slides] = Right(new Slides(url))

    def from(in: String): Either[CustomException, Url.Slides] = Url.from(in).flatMap(from)
  }

  sealed trait Video extends Url {
    val platform: String

    def videoId: Video.Id
  }

  object Video {
    def from(url: Url): Either[CustomException, Url.Video] = url match {
      case u: Url.Video => Right(u)
      case _ => Left(CustomException(s"'${url.value}' is an invalid video Url"))
    }

    def from(in: String): Either[CustomException, Url.Video] = Url.from(in).flatMap(from)

    final case class Id(value: String)

  }

  sealed trait Videos extends Url {
    val platform: String
    val kind: String
  }

  object Videos {
    def from(url: Url): Either[CustomException, Url.Videos] = url match {
      case u: Url.Videos => Right(u)
      case _ => Left(CustomException(s"'${url.value}' is an invalid videos Url"))
    }

    def from(in: String): Either[CustomException, Url.Videos] = Url.from(in).flatMap(from)

    sealed trait Channel extends Videos {
      val kind = "Channel"
    }

    object Channel {
      def from(url: Url): Either[CustomException, Url.Videos.Channel] = url match {
        case u: Url.Videos.Channel => Right(u)
        case _ => Left(CustomException(s"'${url.value}' is an invalid video channel Url"))
      }

      def from(in: String): Either[CustomException, Url.Videos.Channel] = Url.from(in).flatMap(from)

      final case class Id(value: String)

    }

    sealed trait Playlist extends Videos {
      val kind = "Playlist"

      def playlistId: Playlist.Id
    }

    object Playlist {
      def from(url: Url): Either[CustomException, Url.Videos.Playlist] = url match {
        case u: Url.Videos.Playlist => Right(u)
        case _ => Left(CustomException(s"'${url.value}' is an invalid video playlist Url"))
      }

      def from(in: String): Either[CustomException, Url.Videos.Playlist] = Url.from(in).flatMap(from)

      final case class Id(value: String)

    }

    sealed trait Other extends Videos {
      val kind = "Other"
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
    val platform = "YouTube"

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
    }).map { y =>
      Video.from(y).toOption
        .orElse(Playlist.from(y).toOption)
        .orElse(Channel.from(y).toOption)
        .getOrElse(y)
    }

    def from(in: String): Either[CustomException, YouTube] = Url.from(in).flatMap(from)

    final case class Channel private(url: Url) extends YouTube(url) with Url.Videos.Channel {
      // this pattern matching is safe thanks to the Channel.from methods
      def fold[T](channelId: Url.Videos.Channel.Id => T, username: String => T, customUrl: String => T): T = path match {
        case List("channel", id, _*) => channelId(Url.Videos.Channel.Id(id))
        case List("user", user, _*) => username(user)
        case List("c", name) => customUrl(name)
        case List(name) => customUrl(name)
      }
    }

    object Channel {
      def from(u: YouTube): Either[CustomException, Channel] = u.parsed.path match {
        case List("channel", id, _*) => Right(new Channel(u))
        case List("user", user, _*) => Right(new Channel(u))
        case List("c", name) => Right(new Channel(u))
        case List(name) if u.parsed.host != "youtu.be" && !Set("playlist", "watch").contains(name) => Right(new Channel(u))
        case _ => Left(CustomException(s"'${u.value}' is not a YouTube channel url"))
      }

      def from(u: Url): Either[CustomException, Channel] = YouTube.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Channel] = YouTube.from(u).flatMap(from)
    }

    final case class Playlist private(url: Url) extends YouTube(url) with Url.Videos.Playlist {
      override def playlistId: Url.Videos.Playlist.Id = Url.Videos.Playlist.Id(handle)
    }

    object Playlist {
      def from(u: YouTube): Either[CustomException, Playlist] =
        if (u.parsed.path == List("playlist")) Right(new Playlist(u))
        else Left(CustomException(s"'${u.value}' is not a YouTube playlist url"))

      def from(u: Url): Either[CustomException, Playlist] = YouTube.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Playlist] = YouTube.from(u).flatMap(from)
    }

    final class Video private(url: Url) extends YouTube(url) with Url.Video {
      def videoId: Url.Video.Id = Url.Video.Id(handle)
    }

    object Video {
      def from(u: YouTube): Either[CustomException, Video] =
        if (u.parsed.path == List("watch")) Right(new Video(u))
        else if (u.parsed.host == "youtu.be") Right(new Video(u))
        else Left(CustomException(s"'${u.value}' is not a YouTube video url"))

      def from(u: Url): Either[CustomException, Video] = YouTube.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Video] = YouTube.from(u).flatMap(from)

      def fromId(id: Url.Video.Id): Either[CustomException, Video] = from(s"https://www.youtube.com/watch?v=${id.value}")
    }

  }

  sealed class Vimeo private(url: Url) extends Url(url.value, url.parsed) {
    val platform: String = "Vimeo"

    override def handle: String = parsed.path.lastOption.getOrElse(super.handle)
  }

  object Vimeo {
    private val onlyNumbers = "[0-9]+".r
    private val regex = "https?://(?:www\\.)?vimeo\\.com.*".r

    def from(u: Url): Either[CustomException, Vimeo] = (u.value match {
      case regex() => Right(new Vimeo(u))
      case _ => Left(CustomException(s"'${u.value}' is not a Vimeo url"))
    }).map { v =>
      Video.from(v).toOption
        .orElse(Channel.from(v).toOption)
        .orElse(Showcase.from(v).toOption)
        .getOrElse(v)
    }

    def from(in: String): Either[CustomException, Vimeo] = Url.from(in).flatMap(from)

    final case class Channel private(url: Url) extends Vimeo(url) with Url.Videos.Channel

    object Channel {
      def from(u: Vimeo): Either[CustomException, Channel] = u.parsed.path match {
        case List(onlyNumbers()) => Left(CustomException(s"'${u.value}' is not a Vimeo channel url"))
        case List(_) => Right(new Channel(u))
        case _ => Left(CustomException(s"'${u.value}' is not a Vimeo channel url"))
      }

      def from(u: Url): Either[CustomException, Channel] = Vimeo.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Channel] = Vimeo.from(u).flatMap(from)
    }

    final case class Showcase private(url: Url) extends Vimeo(url) with Url.Videos.Playlist {
      override def playlistId: Url.Videos.Playlist.Id = Url.Videos.Playlist.Id(handle)
    }

    object Showcase {
      def from(u: Vimeo): Either[CustomException, Showcase] =
        if (u.parsed.path.length == 2 && u.parsed.path.headOption.contains("showcase")) Right(new Showcase(u))
        else Left(CustomException(s"'${u.value}' is not a Vimeo showcase url"))

      def from(u: Url): Either[CustomException, Showcase] = Vimeo.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Showcase] = Vimeo.from(u).flatMap(from)
    }

    final class Video private(url: Url) extends Vimeo(url) with Url.Video {
      def videoId: Url.Video.Id = Url.Video.Id(handle)
    }

    object Video {
      def from(u: Vimeo): Either[CustomException, Video] = u.parsed.path match {
        case List(onlyNumbers()) => Right(new Video(u))
        case _ :+ "video" :+ onlyNumbers() => Right(new Video(u))
        case _ => Left(CustomException(s"'${u.value}' is not a Vimeo video url"))
      }

      def from(u: Url): Either[CustomException, Video] = Vimeo.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Video] = Vimeo.from(u).flatMap(from)
    }

  }

  sealed class Infoq private(url: Url) extends Url(url.value, url.parsed) {
    val platform: String = "Infoq"

    override def handle: String = parsed.path.lastOption.getOrElse(super.handle)
  }

  object Infoq {
    private val regex = "https?://www\\.infoq\\.com.*".r

    def from(u: Url): Either[CustomException, Infoq] = (u.value match {
      case regex() => Right(new Infoq(u))
      case _ => Left(CustomException(s"'${u.value}' is not an Infoq url"))
    }).map { v =>
      Presentation.from(v).toOption
        .orElse(Topic.from(v).toOption)
        .getOrElse(v)
    }

    def from(in: String): Either[CustomException, Infoq] = Url.from(in).flatMap(from)

    final case class Topic private(url: Url) extends Infoq(url) with Url.Videos.Other

    object Topic {
      def from(u: Infoq): Either[CustomException, Topic] = u.parsed.path match {
        case List(topic) if topic != "search.action" => Right(new Topic(u))
        case List(lang, topic) if lang.length == 2 && topic != "search.action" => Right(new Topic(u))
        case _ => Left(CustomException(s"'${u.value}' is not a Infoq topic url"))
      }

      def from(u: Url): Either[CustomException, Topic] = Infoq.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Topic] = Infoq.from(u).flatMap(from)
    }

    final class Presentation private(url: Url) extends Infoq(url) with Url.Video {
      def videoId: Url.Video.Id = Url.Video.Id(handle)
    }

    object Presentation {
      def from(u: Infoq): Either[CustomException, Presentation] = u.parsed.path match {
        case List("presentations", prez) => Right(new Presentation(u))
        case List(lang, "presentations", prez) if lang.length == 2 => Right(new Presentation(u))
        case _ => Left(CustomException(s"'${u.value}' is not a Infoq presentation url"))
      }

      def from(u: Url): Either[CustomException, Presentation] = Infoq.from(u).flatMap(from)

      def from(u: String): Either[CustomException, Presentation] = Infoq.from(u).flatMap(from)
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
