package gospeak.libs.cloudinary

import java.time.Instant

import cats.effect.IO
import gospeak.libs.cloudinary.CloudinaryJson._
import gospeak.libs.cloudinary.domain.{CloudinaryUploadRequest, CloudinaryUploadResponse}
import gospeak.libs.http.HttpClient
import gospeak.libs.scala.Crypto
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Creds
import io.circe.parser.decode

class CloudinaryClient(conf: CloudinaryClient.Conf, http: HttpClient) {
  private val baseUrl = "https://api.cloudinary.com/v1_1"
  private val ignoreOnSign = Set("api_key", "file")

  // see https://cloudinary.com/documentation/upload_images#generating_authentication_signatures
  def sign(params: Map[String, String]): Either[String, String] =
    withCreds((_, creds) => sign(creds, params))

  // see https://cloudinary.com/documentation/upload_images#uploading_with_a_direct_call_to_the_api
  def upload(req: CloudinaryUploadRequest): IO[Either[String, CloudinaryUploadResponse]] = {
    withCreds { (cloudName, creds) =>
      val uploadUrl = s"$baseUrl/$cloudName/image/upload"
      val allParams = req.toMap ++ Map(
        "api_key" -> creds.key,
        "timestamp" -> Instant.now().getEpochSecond.toString)
      val signature = sign(creds, allParams)
      http.postForm(uploadUrl, allParams ++ Map("signature" -> signature))
        .map(r => decode[CloudinaryUploadResponse](r.body).leftMap(_.getMessage))
    }.sequence.map(_.flatMap(identity))
  }

  private def sign(creds: Creds, queryParams: Map[String, String]): String = {
    val params = queryParams
      .filterKeys(!ignoreOnSign.contains(_))
      .toList.sortBy(_._1)
      .map { case (key, value) => s"$key=$value" }.mkString("&")
    Crypto.sha1(params + creds.secret.decode)
  }

  private def withCreds[A](block: (String, Creds) => A): Either[String, A] =
    conf match {
      case CloudinaryClient.Conf(cloudName, _, Some(creds)) => Right(block(cloudName, creds))
      case _: CloudinaryClient.Conf => Left("No credentials defined for cloudinary")
    }
}

object CloudinaryClient {

  final case class Conf(cloudName: String,
                        uploadPreset: Option[String],
                        creds: Option[Creds])

}
