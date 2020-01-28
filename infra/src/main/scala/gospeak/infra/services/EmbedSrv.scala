package gospeak.infra.services

import cats.effect.IO
import gospeak.infra.utils.HttpClient
import gospeak.libs.scala.domain.{Html, Url}

import scala.util.control.NonFatal

object EmbedSrv {
  def embedCode(url: Url): IO[Html] = {
    embedCodeSync(url).map(IO.pure).getOrElse {
      embedCodeAsync(url)
        .handleErrorWith { case NonFatal(_) => IO.pure(Some(embedCodeDefault(url))) }
        .map(_.getOrElse(embedCodeDefault(url)))
    }
  }

  private def embedCodeSync(url: Url): Option[Html] =
    SyncService.all.foldLeft(Option.empty[Html]) { (acc, cur) =>
      if (acc.isDefined) acc
      else cur.embed(url)
    }

  private def embedCodeAsync(url: Url): IO[Option[Html]] =
    HttpClient.get(url.value).map { res =>
      AsyncService.all.foldLeft(Option.empty[Html]) { (acc, cur) =>
        if (acc.isDefined) acc
        else cur.embed(url, res.body)
      }
    }

  private def embedCodeDefault(url: Url): Html =
    Html(
      s"""<div>
         |  Not embeddable: <a href="${url.value}" target="_blank">${url.value}</a>
         |</div>
       """.stripMargin.trim)

  private val hash = "([^/?&#]+)"

  sealed trait SyncService {
    def embed(url: Url): Option[Html]
  }

  private[services] object SyncService {

    case object YouTube extends SyncService {
      private val templateUrl1 = "https?://www.youtube.com/watch\\?(?:[^=]+=[^&]+&)*v=([^&]+).*".r
      private val templateUrl2 = s"https?://youtu.be/$hash.*".r

      override def embed(url: Url): Option[Html] = url.value match {
        case templateUrl1(id) => embedUrl(id).map(embedCode)
        case templateUrl2(id) => embedUrl(id).map(embedCode)
        case _ => None
      }

      private def embedUrl(id: String): Option[Url] = Url.from(s"https://www.youtube.com/embed/$id").toOption

      private def embedCode(url: Url): Html = Html(s"""<iframe width="560" height="315" src="${url.value}" frameborder="0" allowfullscreen></iframe>""")
    }

    case object Dailymotion extends SyncService {
      private val templateUrl = s"https?://www.dailymotion.com/video/$hash.*".r

      override def embed(url: Url): Option[Html] = url.value match {
        case templateUrl(id) => embedUrl(id).map(embedCode)
        case _ => None
      }

      private def embedUrl(id: String): Option[Url] = Url.from(s"//www.dailymotion.com/embed/video/${id.split("_").headOption.getOrElse(id)}").toOption

      private def embedCode(url: Url): Html = Html(s"""<iframe src="${url.value}" width="560" height="315" frameborder="0" allowfullscreen></iframe>""")
    }

    case object Vimeo extends SyncService {
      private val templateUrl = s"https?://vimeo.com/$hash.*".r

      override def embed(url: Url): Option[Html] = url.value match {
        case templateUrl(id) => embedUrl(id).map(embedCode)
        case _ => None
      }

      private def embedUrl(id: String): Option[Url] = Url.from(s"https://player.vimeo.com/video/$id").toOption

      private def embedCode(url: Url): Html = Html(s"""<iframe src="${url.value}" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>""")
    }

    case object GoogleSlides extends SyncService {
      private val templateUrl = s"https?://docs.google.com/presentation/d/$hash.*".r

      override def embed(url: Url): Option[Html] = url.value match {
        case templateUrl(id) => embedUrl(id).map(embedCode)
        case _ => None
      }

      private def embedUrl(id: String): Option[Url] = Url.from(s"https://docs.google.com/presentation/d/$id/embed").toOption

      private def embedCode(url: Url): Html = Html(s"""<iframe src="${url.value}" width="960" height="569" frameborder="0" allowfullscreen="true" mozallowfullscreen="true" webkitallowfullscreen="true"></iframe>""")
    }

    case object SlidesDotCom extends SyncService {
      private val templateUrl = s"https?://slides.com/$hash/$hash.*".r

      override def embed(url: Url): Option[Html] = url.value match {
        case templateUrl(user, id) => embedUrl(user, id).map(embedCode)
        case _ => None
      }

      private def embedUrl(user: String, id: String): Option[Url] = Url.from(s"//slides.com/$user/$id/embed?style=light").toOption

      private def embedCode(url: Url): Html = Html(s"""<iframe src="${url.value}" width="576" height="420" scrolling="no" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>""")
    }

    case object Pdf extends SyncService {
      private val templateUrl = s".*\\.pdf".r

      override def embed(url: Url): Option[Html] = url.value match {
        case templateUrl() => Some(embedCode(url))
        case _ => None
      }

      private def embedCode(url: Url): Html = Html(s"""<iframe src="${url.value}" width="576" height="420" scrolling="no" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>""")
    }

    val all: Seq[SyncService] = Seq(YouTube, Dailymotion, Vimeo, GoogleSlides, SlidesDotCom, Pdf)
  }

  sealed trait AsyncService {
    def embed(url: Url, content: String): Option[Html]
  }

  private[services] object AsyncService {

    case object SlideShare extends AsyncService {
      private val templateUrl = s"https?://[a-z]+.slideshare.net/$hash/$hash.*".r
      private val templateBody = "(?is).*<meta class=\"twitter_player\" value=\"([^\"]+)\" name=\"twitter:player\" />.*".r

      override def embed(url: Url, content: String): Option[Html] = url.value match {
        case templateUrl(_, _) => content match {
          case templateBody(embedUrl) => Some(embedCode(embedUrl))
          case _ => None
        }
        case _ => None
      }

      private def embedCode(embedUrl: String): Html = Html(s"""<iframe src="$embedUrl" width="595" height="485" frameborder="0" marginwidth="0" marginheight="0" scrolling="no" style="border:1px solid #CCC; border-width:1px; margin-bottom:5px; max-width: 100%;" allowfullscreen></iframe>""")
    }

    case object SpeakerDeck extends AsyncService {
      private val templateUrl = s"https?://speakerdeck.com/$hash/$hash.*".r
      private val templateBody = "(?is).*<div class=\"speakerdeck-embed\" data-id=\"([^\"]+)\" data-ratio=\"([^\"]+)\"></div>.*".r

      override def embed(url: Url, content: String): Option[Html] = url.value match {
        case templateUrl(_, _) => content match {
          case templateBody(embedId, embedRatio) => Some(embedCode(embedId, embedRatio))
          case _ => None
        }
        case _ => None
      }

      private def embedCode(id: String, ratio: String): Html = Html(s"""<script async class="speakerdeck-embed" data-id="$id" data-ratio="$ratio" src="//speakerdeck.com/assets/embed.js"></script>""")
    }

    case object HtmlSlides extends AsyncService {
      private val templateBody = "(?is).*(?:(?:<div class=\"reveal)|(?:<div id=\"impress)|(?:remark.create)).*".r

      override def embed(url: Url, content: String): Option[Html] = content match {
        case templateBody() => Some(embedCode(url))
        case _ => None
      }

      private def embedCode(url: Url): Html = Html(s"""<iframe src="${url.value}" width="595" height="485" frameborder="0"></iframe>""")
    }

    val all: Seq[AsyncService] = Seq(SlideShare, SpeakerDeck, HtmlSlides)
  }

}
