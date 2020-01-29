package gospeak.core.services.meetup.domain

import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.domain.Crypted

import scala.util.Try

final case class MeetupToken(accessToken: Crypted,
                             refreshToken: Crypted)

object MeetupToken {
  def from(accessToken: String, refreshToken: String, key: AesSecretKey): Try[MeetupToken] = for {
    access <- Crypted.from(accessToken, key)
    refresh <- Crypted.from(refreshToken, key)
  } yield MeetupToken(access, refresh)
}
