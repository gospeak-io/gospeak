package gospeak.libs.scala.domain

case class CustomException(message: String, errors: List[CustomError] = List()) extends RuntimeException {
  override def getMessage: String =
    if (errors.isEmpty) message
    else s"$message: ${errors.map(_.value).mkString(", ")}"
}
