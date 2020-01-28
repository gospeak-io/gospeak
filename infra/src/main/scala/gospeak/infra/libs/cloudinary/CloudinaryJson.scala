package gospeak.infra.libs.cloudinary

import gospeak.infra.libs.cloudinary.domain.CloudinaryUploadResponse
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object CloudinaryJson {
  implicit val cloudinaryUploadResponseDecoder: Decoder[CloudinaryUploadResponse] = deriveDecoder[CloudinaryUploadResponse]
}
