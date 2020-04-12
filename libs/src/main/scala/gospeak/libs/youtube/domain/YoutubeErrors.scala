package gospeak.libs.youtube.domain

import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo
import com.google.api.client.googleapis.json.{GoogleJsonError, GoogleJsonResponseException}

import scala.collection.JavaConverters._

final case class YoutubeErrors(code: Int,
                               errors: Seq[YError],
                               message: String
                             )

object YoutubeErrors {
  def apply(ex: GoogleJsonResponseException) =
    new YoutubeErrors(ex.getStatusCode,
      YError.from(ex.getDetails),
      ex.getMessage)
}

final case class YError(domain: String,
                        location: String,
                        locationType: String,
                        message: String,
                        reason: String)

object YError {

  def from(error: GoogleJsonError): Seq[YError] =
    error.getErrors.asScala.map(YError(_))

  def apply(info: ErrorInfo) =
    new YError(info.getDomain,
      info.getLocation,
      info.getLocationType,
      info.getMessage,
      info.getReason)
}
