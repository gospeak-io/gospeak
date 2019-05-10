package fr.gospeak.core.services.slack

import cats.effect.IO
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.slack.domain._

trait SlackSrv {
  def getInfos(token: SlackToken): IO[Either[SlackError, SlackTokenInfo]]

  def exec(creds: SlackCredentials, action: SlackAction, data: TemplateData): IO[Unit]
}
