package fr.gospeak.infra.libs.cloudinary.domain

import fr.gospeak.libs.scalautils.domain.Image

import scala.util.Try

final case class CloudinaryUploadResponse(public_id: String,
                                          version: Long,
                                          signature: String,
                                          width: Int,
                                          height: Int,
                                          format: String,
                                          resource_type: String,
                                          created_at: String,
                                          tags: Seq[String],
                                          bytes: Long,
                                          `type`: String,
                                          etag: String,
                                          placeholder: Boolean,
                                          url: String,
                                          secure_url: String,
                                          access_mode: Option[String],
                                          original_filename: Option[String]) {
  def toUrl: Try[Image.CloudinaryUrl] = Image.CloudinaryUrl.parse(secure_url)
}
