package fr.gospeak.infra.services.slack

import cats.effect.IO
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.{SlackAction, SlackToken, SlackUser}

class SlackSrvImpl(client: SlackClient) extends SlackSrv {
  override def exec(token: SlackToken, user: SlackUser, action: SlackAction): IO[Unit] = ???
}
