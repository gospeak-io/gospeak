package gospeak.libs.scala.domain

final class TwitterHashtag private(value: String) extends DataClass(value) {
  def url: String = "https://twitter.com/hashtag/" + value

  def handle: String = "#" + value
}

object TwitterHashtag {
  def from(in: String): Either[CustomException, TwitterHashtag] = {
    val value = in.stripPrefix("#")
    val errs = errors(value)
    if (errs.isEmpty) Right(new TwitterHashtag(value))
    else Left(CustomException(s"'$in' is an invalid TwitterHashtag", errs))
  }

  private def errors(in: String): List[CustomError] =
    List(
      if (in.contains(" ")) Some("Should not contain space") else None
    ).flatten.map(CustomError)
}
