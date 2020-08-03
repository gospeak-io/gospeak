package gospeak.core.domain.utils

import java.time.ZoneId

import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{EmailAddress, Logo, Url}

object Constants {
  val defaultZoneId: ZoneId = ZoneId.of("Europe/Paris")

  object Gospeak {
    val name = "Gospeak"
    val url: Url = Url.from("https://gospeak.io").get
    val logo: Logo = Logo(Url.from("https://gospeak.io/logo.png").get)
    val logoText: Logo = Logo(Url.from("https://res.cloudinary.com/gospeak/image/upload/gospeak/logo-text.svg").get)
    val contactEmail: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("contact@gospeak.io").get, Some("Gospeak"))
    val noreplyEmail: EmailAddress.Contact = EmailAddress.Contact(EmailAddress.from("noreply@gospeak.io").get, Some("Gospeak"))
    val twitter: Url.Twitter = Url.Twitter.from("https://twitter.com/gospeak_io").get
    val linkedIn: Url.LinkedIn = Url.LinkedIn.from("https://www.linkedin.com/company/gospeak").get
  }

  object Placeholders {
    val groupLogo = "https://res.cloudinary.com/gospeak/image/upload/placeholders/group-logo.png" // FIXME find better image
    val eventLogo = "https://res.cloudinary.com/gospeak/image/upload/placeholders/group-logo.png" // FIXME find better image
    val partnerLogo = "https://res.cloudinary.com/gospeak/image/upload/placeholders/group-logo.png" // FIXME find better image
    val unknownUser = "https://res.cloudinary.com/gospeak/image/upload/placeholders/unknown-user.png"
    val unknownPartner = "https://res.cloudinary.com/gospeak/image/upload/placeholders/unknown-user.png" // FIXME find better image
    val noVenueForEvent = "https://res.cloudinary.com/gospeak/image/upload/placeholders/no-venue-for-event.png"
    val videoCover = "/assets/web/img/placeholders/video-cover.jpg"
  }

  object Emoji {
    // https://coolsymbol.com/emojis/emoji-for-copy-and-paste.html
    // https://unicode.org/emoji/charts/full-emoji-list.html
    val sparkles = "✨"
    val partyPopper = "\uD83C\uDF89"
    val nerdFace = "\uD83E\uDD13"
    val grinningFace = "\uD83D\uDE00"
    val rocket = "\uD83D\uDE80"
    val speakingHead = "\uD83D\uDDE3"
    val speechBalloon = "\uD83D\uDCAC"
    val directHit = "\uD83C\uDFAF"
    val loudSpeaker = "\uD83D\uDCE2"
    val studioMicrophone = "\uD83C\uDF99"
    val clapperBoard = "\uD83C\uDFAC"
    val calendar = "\uD83D\uDCC6"

    val gospeak: String = sparkles
    val user: String = nerdFace
    val talk: String = speechBalloon
    val group: String = directHit
    val cfp: String = loudSpeaker
    val proposal: String = studioMicrophone
    val event: String = calendar
    val video: String = clapperBoard
  }

  object Slack {
    val defaultBotName = "Gospeak bot"
    val defaultBotAvatar = ""
  }

  val devMessage: String =
    """Hi! Nice to meet you ❤
      |Did you know Gospeak is an open source project done in Scala FP: https://github.com/gospeak-io/gospeak ?
      |If you like it, give us a cheer message on twitter @gospeak_io or in our issues ;)
      |""".stripMargin
}
