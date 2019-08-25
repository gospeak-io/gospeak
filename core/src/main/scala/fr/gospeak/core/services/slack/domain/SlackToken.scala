package fr.gospeak.core.services.slack.domain

import fr.gospeak.libs.scalautils.Crypto.AesSecretKey
import fr.gospeak.libs.scalautils.domain.Crypted

import scala.util.Try

final case class SlackToken(private val crypted: Crypted) {
  def decode(key: AesSecretKey): Try[String] = crypted.decode(key)
}

object SlackToken {
  def from(message: String, key: AesSecretKey): Try[SlackToken] =
    Crypted.from(message, key).map(new SlackToken(_))
}
