package gospeak.libs.scala.domain

case class CustomException(message: String, errors: Seq[CustomError] = Seq()) extends RuntimeException {
  override def getMessage: String =
    if (errors.isEmpty) message
    else s"$message: ${errors.map(_.value).mkString(", ")}"
}
