package gospeak.core.services.slack.domain

import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.domain.Crypted

import scala.util.Try

final case class SlackToken(private val crypted: Crypted) {
  def decode(key: AesSecretKey): Try[String] = crypted.decode(key)
}

object SlackToken {
  def from(message: String, key: AesSecretKey): Try[SlackToken] =
    Crypted.from(message, key).map(new SlackToken(_))
}
