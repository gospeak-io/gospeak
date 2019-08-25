package fr.gospeak.core.services.slack

import cats.effect.IO
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.slack.domain._
import fr.gospeak.libs.scalautils.Crypto.AesSecretKey

trait SlackSrv {
  def getInfos(token: SlackToken, key: AesSecretKey): IO[SlackTokenInfo]

  def exec(action: SlackAction, data: TemplateData, creds: SlackCredentials, key: AesSecretKey): IO[Unit]
}
