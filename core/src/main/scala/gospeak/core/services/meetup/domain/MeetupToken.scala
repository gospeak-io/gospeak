package gospeak.core.services.meetup.domain

import gospeak.libs.scala.Crypto
import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.domain.{Crypted, CustomException}

import scala.util.{Failure, Success, Try}

final case class MeetupToken(accessToken: Crypted,
                             refreshToken: Crypted)

object MeetupToken {
  def from(accessToken: String, refreshToken: String, key: AesSecretKey): Try[MeetupToken] = for {
    access <- Crypted.from(accessToken, key)
    refresh <- Crypted.from(refreshToken, key)
  } yield MeetupToken(access, refresh)

  def toText(token: MeetupToken): String =
    Crypto.base64Encode(token.accessToken.value + "~" + token.refreshToken.value)

  def fromText(text: String): Try[MeetupToken] =
    Crypto.base64Decode(text).map(_.split('~')).flatMap {
      case Array(access, refresh) => Success(MeetupToken(Crypted(access), Crypted(refresh)))
      case v => Failure(CustomException(s"Invalid serialization format of MeetupToken: ${v.mkString("~")}"))
    }
}
