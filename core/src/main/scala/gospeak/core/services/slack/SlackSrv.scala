package gospeak.core.services.slack

import cats.effect.IO
import gospeak.core.services.slack.domain._
import gospeak.libs.scala.Crypto.AesSecretKey
import io.circe.Json

trait SlackSrv {
  def getInfos(token: SlackToken, key: AesSecretKey): IO[SlackTokenInfo]

  def exec(action: SlackAction, data: Json, creds: SlackCredentials, key: AesSecretKey): IO[Unit]
}
