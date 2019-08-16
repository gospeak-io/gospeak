package fr.gospeak.core.services.meetup.domain

import fr.gospeak.libs.scalautils.domain.Avatar

final case class MeetupUser(id: MeetupUser.Id,
                            name: String,
                            avatar: Avatar)

object MeetupUser {

  final case class Id(value: Long) extends AnyVal

}
