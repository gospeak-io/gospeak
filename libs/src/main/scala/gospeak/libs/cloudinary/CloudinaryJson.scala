package gospeak.libs.cloudinary

import gospeak.libs.cloudinary.domain.CloudinaryUploadResponse
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object CloudinaryJson {
  implicit val cloudinaryUploadResponseDecoder: Decoder[CloudinaryUploadResponse] = deriveDecoder[CloudinaryUploadResponse]
}
