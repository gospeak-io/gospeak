package gospeak.libs.youtube.domain

import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo
import com.google.api.client.googleapis.json.{GoogleJsonError, GoogleJsonResponseException}

import scala.collection.JavaConverters._

final case class YoutubeErrors(code: Int,
                               errors: List[YoutubeError],
                               message: Option[String])

object YoutubeErrors {
  def apply(ex: GoogleJsonResponseException) =
    new YoutubeErrors(
      code = ex.getStatusCode,
      errors = YoutubeError.from(ex.getDetails),
      message = Option(ex.getMessage))

  // used for custom validations
  def apply(message: String) =
    new YoutubeErrors(
      code = 0,
      errors = List(),
      message = Some(message))
}

final case class YoutubeError(domain: Option[String],
                              location: Option[String],
                              locationType: Option[String],
                              message: Option[String],
                              reason: Option[String])

object YoutubeError {
  def apply(info: ErrorInfo) =
    new YoutubeError(
      Option(info.getDomain),
      Option(info.getLocation),
      Option(info.getLocationType),
      Option(info.getMessage),
      Option(info.getReason))

  def from(error: GoogleJsonError): List[YoutubeError] =
    error.getErrors.asScala.map(YoutubeError(_)).toList
}
