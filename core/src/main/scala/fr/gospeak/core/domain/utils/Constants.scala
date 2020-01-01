package fr.gospeak.core.domain.utils

import java.time.ZoneId

import fr.gospeak.libs.scalautils.domain.EmailAddress

object Constants {
  val defaultZoneId: ZoneId = ZoneId.of("Europe/Paris")

  object Images {
    val gospeakLogoText = "https://res.cloudinary.com/gospeak/image/upload/gospeak/logo-text.svg"
  }

  object Placeholders {
    val groupLogo = "https://res.cloudinary.com/gospeak/image/upload/placeholders/group-logo.png"
    val unknownUser = "https://res.cloudinary.com/gospeak/image/upload/placeholders/unknown-user.png"
    val unknownPartner = "https://res.cloudinary.com/gospeak/image/upload/placeholders/unknown-user.png" // FIXME find better image
    val noVenueForEvent = "https://res.cloudinary.com/gospeak/image/upload/placeholders/no-venue-for-event.png"
  }

  object Contact {
    val admin: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("contact@gospeak.io").right.get, Some("Gospeak"))
    val noReply: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("noreply@gospeak.io").right.get, Some("Gospeak"))
  }

  object Slack {
    val defaultBotName = "Gospeak bot"
    val defaultBotAvatar = ""
  }

}
