package fr.gospeak.core.domain.utils

import java.time.ZoneId

import fr.gospeak.libs.scalautils.domain.EmailAddress

object Constants {
  val defaultZoneId: ZoneId = ZoneId.of("Europe/Paris")

  object Contact {
    val admin: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("contact@gospeak.fr").right.get, Some("Gospeak"))
    val noReply: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("noreply@gospeak.fr").right.get, Some("Gospeak"))
  }

  object Slack {
    val defaultBotName = "Gospeak bot"
    val defaultBotAvatar = ""
  }

}
