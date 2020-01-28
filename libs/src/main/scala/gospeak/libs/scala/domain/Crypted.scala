package gospeak.libs.scala.domain

import gospeak.libs.scala.Crypto
import gospeak.libs.scala.Crypto.{AesEncrypted, AesSecretKey}

import scala.util.Try

final case class Crypted(value: String) {
  def decode(key: AesSecretKey): Try[String] = Crypto.aesDecrypt(AesEncrypted(value), key)

  override def toString: String = "*****"
}

object Crypted {
  def apply(crypted: AesEncrypted): Crypted = new Crypted(crypted.cipher)

  def from(message: String, key: AesSecretKey): Try[Crypted] =
    Crypto.aesEncrypt(message, key).map(Crypted(_))
}
