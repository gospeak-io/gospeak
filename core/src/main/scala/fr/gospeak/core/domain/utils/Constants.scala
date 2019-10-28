package fr.gospeak.core.domain.utils

import java.time.ZoneId

object Constants {
  val defaultZoneId: ZoneId = ZoneId.of("Europe/Paris")

  object Slack {
    val defaultBotName = "Gospeak bot"
    val defaultBotAvatar = ""
  }

}
