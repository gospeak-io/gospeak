package fr.gospeak.core.services.upload

sealed trait UploadConf

object UploadConf {

  final case class Url() extends UploadConf

  final case class Cloudinary(cloudName: String,
                              uploadPreset: Option[String]) extends UploadConf

}
