package gospeak.web.utils

import java.net.URL

import play.api.mvc.{AnyContent, Call, Headers}

import scala.util.Try

object HttpUtils {
  // ex: http://localhost:9000/u/groups/ht-paris/events/2019-02/edit
  def getReferer(req: BasicReq[AnyContent]): Option[String] = getReferer(req.headers)

  def getReferer(headers: Headers): Option[String] = headers.get("Referer")

  // ex: /u/groups/ht-paris/events/2019-02/edit
  def getRequestUri(req: BasicReq[AnyContent]): Option[String] = getRequestUri(req.headers)

  def getRequestUri(headers: Headers): Option[String] = headers.get("Raw-Request-URI")

  // return Referer if present except if it's the same than Request-URI, or else it returns the provided Call
  def cancelUrl(req: BasicReq[AnyContent], call: => Call): String = {
    getReferer(req).filter { ref =>
      val refPath = getUrlPath(ref)
      val reqPath = getRequestUri(req).map(getUriPath).getOrElse("")
      refPath != reqPath
    }.getOrElse(call.toString)
  }

  private[utils] def getUrlPath(url: String): String = Try(new URL(url).getPath).getOrElse(url)

  private[utils] def getUriPath(uri: String): String = uri.split("\\?").head.split("#").head
}
