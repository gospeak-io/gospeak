package gospeak.core.domain.utils

import java.time.ZoneId

import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{EmailAddress, Url}

object Constants {
  val defaultZoneId: ZoneId = ZoneId.of("Europe/Paris")

  object Images {
    val gospeakLogoSquare = "https://gospeak.io/logo.png"
    val gospeakLogoText = "https://res.cloudinary.com/gospeak/image/upload/gospeak/logo-text.svg"
  }

  object Placeholders {
    val groupLogo = "https://res.cloudinary.com/gospeak/image/upload/placeholders/group-logo.png" // FIXME find better image
    val eventLogo = "https://res.cloudinary.com/gospeak/image/upload/placeholders/group-logo.png" // FIXME find better image
    val unknownUser = "https://res.cloudinary.com/gospeak/image/upload/placeholders/unknown-user.png"
    val unknownPartner = "https://res.cloudinary.com/gospeak/image/upload/placeholders/unknown-user.png" // FIXME find better image
    val noVenueForEvent = "https://res.cloudinary.com/gospeak/image/upload/placeholders/no-venue-for-event.png"
  }

  object Contact {
    val admin: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("contact@gospeak.io").right.get, Some("Gospeak"))
    val noReply: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("noreply@gospeak.io").right.get, Some("Gospeak"))
  }

  object Twitter {
    val gospeakHandle = "@gospeak_io"
    val gospeakUrl: Url = Url.from("https://twitter.com/gospeak_io").get
  }

  object LinkedIn {
    val gospeakUrl: Url = Url.from("https://www.linkedin.com/company/gospeak").get
  }

  object Slack {
    val defaultBotName = "Gospeak bot"
    val defaultBotAvatar = ""
  }

}
