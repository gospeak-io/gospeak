package gospeak.libs.scala.domain

import io.circe.Encoder

final case class Tweet(text: String,
                       url: Option[String],
                       related: Option[String])

object Tweet {
  def from[A](tmpl: Liquid[A], data: A, url: String)(implicit e: Encoder[A]): Either[Liquid.Error, Tweet] =
    tmpl.render(data).map(new Tweet(_, Some(url), None))
}
