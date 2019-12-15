package fr.gospeak.infra.libs.cloudinary.domain

final case class CloudinaryUploadRequest(file: String,
                                         folder: Option[String],
                                         publicId: Option[String]) {
  def toMap: Map[String, String] = Map(
    "file" -> Some(file),
    "folder" -> folder,
    "public_id" -> publicId
  ).collect { case (key, Some(value)) => (key, value) }
}
