package gospeak.core.services.meetup.domain

import java.time.Instant

import gospeak.libs.scala.domain.Avatar

final case class MeetupAttendee(id: MeetupUser.Id,
                                name: String,
                                bio: Option[String],
                                avatar: Option[Avatar],
                                host: Boolean,
                                response: String,
                                guests: Int,
                                updated: Instant) {
  def meetupUrl(group: MeetupGroup.Slug): String = s"https://www.meetup.com/${group.value}/members/${id.value}/profile"
}
