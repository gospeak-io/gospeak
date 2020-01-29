package gospeak.web.api.domain.utils

import gospeak.core.domain.User
import gospeak.web.utils.JsonUtils._
import gospeak.libs.scala.domain.{EmailAddress, Secret}
import play.api.libs.json._

import scala.concurrent.duration._

object JsonFormats {
  private val fString: Format[String] = Format((json: JsValue) => json.validate[String], (o: String) => JsString(o))
  private val fLong: Format[Long] = Format((json: JsValue) => json.validate[Long], (o: Long) => JsNumber(o))
  implicit val fEmailAddress: Format[EmailAddress] = fString.validate2(EmailAddress.from)(_.value)
  implicit val fSecret: Format[Secret] = fString.imap(Secret)(_.decode)

  implicit val fUserSlug: Format[User.Slug] = fString.validate2(User.Slug.from)(_.value)

  implicit val fFiniteDuration: Format[FiniteDuration] = fLong.imap(FiniteDuration.apply(_, MINUTES))(_.toMinutes)
}
