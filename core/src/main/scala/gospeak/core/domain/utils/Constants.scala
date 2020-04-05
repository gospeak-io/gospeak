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

  object Emoji {
    // https://coolsymbol.com/emojis/emoji-for-copy-and-paste.html
    // https://unicode.org/emoji/charts/full-emoji-list.html
    val sparkles = "✨"
    val nerdFace = "\uD83E\uDD13"
    val grinningFace = "\uD83D\uDE00"
    val rocket = "\uD83D\uDE80"
    val speakingHead = "\uD83D\uDDE3"
    val speechBalloon = "\uD83D\uDCAC"
    val directHit = "\uD83C\uDFAF"
    val studioMicrophone = "\uD83C\uDF99"
    val loudSpeaker = "\uD83D\uDCE2"
    val calendar = "\uD83D\uDCC6"

    val gospeak: String = sparkles
    val user: String = nerdFace
    val talk: String = speechBalloon
    val group: String = directHit
    val cfp: String = loudSpeaker
    val proposal: String = studioMicrophone
    val event: String = calendar
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
    val gospeakUrl: Url.Twitter = Url.Twitter.from("https://twitter.com/gospeak_io").get
  }

  object LinkedIn {
    val gospeakUrl: Url.LinkedIn = Url.LinkedIn.from("https://www.linkedin.com/company/gospeak").get
  }

  object Slack {
    val defaultBotName = "Gospeak bot"
    val defaultBotAvatar = ""
  }

}
