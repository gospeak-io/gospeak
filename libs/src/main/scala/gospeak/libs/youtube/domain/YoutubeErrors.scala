package gospeak.libs.youtube.domain

import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo
import com.google.api.client.googleapis.json.{GoogleJsonError, GoogleJsonResponseException}

import scala.collection.JavaConverters._

final case class YoutubeErrors(code: Int,
                               errors: List[YError],
                               message: Option[String]
                              )

object YoutubeErrors {
  def apply(ex: GoogleJsonResponseException) =
    new YoutubeErrors(ex.getStatusCode,
      YError.from(ex.getDetails),
      Option(ex.getMessage))
}

final case class YError(domain: Option[String],
                        location: Option[String],
                        locationType: Option[String],
                        message: Option[String],
                        reason: Option[String])

object YError {

  def from(error: GoogleJsonError): List[YError] =
    error.getErrors.asScala.map(YError(_)).toList

  def apply(info: ErrorInfo) =
    new YError(
      Option(info.getDomain),
      Option(info.getLocation),
      Option(info.getLocationType),
      Option(info.getMessage),
      Option(info.getReason))
}
