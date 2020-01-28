package fr.gospeak.web.api.domain

import java.time.Instant

import fr.gospeak.core.domain.utils.BasicCtx
import fr.gospeak.core.services.meetup.domain.{MeetupAttendee, MeetupGroup}
import gospeak.libs.scala.domain.Image
import play.api.libs.json.{Json, Writes}

object ApiAttendee {

  // data to display publicly
  final case class Published(name: String,
                             avatar: String,
                             meetupProfile: String,
                             host: Boolean,
                             response: String,
                             updated: Instant)

  object Published {
    implicit val writes: Writes[Published] = Json.writes[Published]
  }

  def published(attendee: MeetupAttendee, group: MeetupGroup.Slug)(implicit ctx: BasicCtx): Published =
    new Published(
      name = attendee.name,
      avatar = attendee.avatar.map(_.value).getOrElse(Image.AdorableUrl(attendee.id.value.toString, None).value),
      meetupProfile = s"https://www.meetup.com/${group.value}/members/${attendee.id.value}",
      host = attendee.host,
      response = attendee.response,
      updated = attendee.updated)
}
