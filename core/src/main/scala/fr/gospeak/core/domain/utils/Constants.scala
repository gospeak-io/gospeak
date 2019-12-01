package fr.gospeak.core.domain.utils

import java.time.ZoneId

import fr.gospeak.libs.scalautils.domain.EmailAddress

object Constants {
  val defaultZoneId: ZoneId = ZoneId.of("Europe/Paris")
  val gravatarStyle = "identicon"

  object Contact {
    val admin: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("contact@gospeak.io").right.get, Some("Gospeak"))
    val noReply: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("noreply@gospeak.io").right.get, Some("Gospeak"))
  }

  object Slack {
    val defaultBotName = "Gospeak bot"
    val defaultBotAvatar = ""
  }

}
