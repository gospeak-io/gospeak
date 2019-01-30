package fr.gospeak.libs.scalautils

case class CustomException(message: String) extends RuntimeException {
  override def getMessage: String = message
}
