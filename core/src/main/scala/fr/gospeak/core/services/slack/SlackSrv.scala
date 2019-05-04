package fr.gospeak.core.services.slack

import cats.effect.IO
import fr.gospeak.core.services.slack.domain.{SlackAction, SlackUser, SlackToken}

trait SlackSrv {
  def exec(token: SlackToken, user: SlackUser, action: SlackAction): IO[Unit]
}
