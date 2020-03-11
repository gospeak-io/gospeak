package gospeak.libs.scala.domain

import io.circe.Encoder

final case class Tweet(text: String,
                       url: Option[String],
                       related: Option[String])

object Tweet {
  def from[A](tmpl: Mustache.Text[A], data: A, url: String)(implicit e: Encoder[A]): Either[Mustache.Error, Tweet] =
    tmpl.render(data).map(new Tweet(_, Some(url), None))
}
