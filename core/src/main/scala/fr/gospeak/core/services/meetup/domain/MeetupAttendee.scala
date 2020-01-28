package fr.gospeak.core.services.meetup.domain

import java.time.Instant

import gospeak.libs.scala.domain.Avatar

final case class MeetupAttendee(id: MeetupUser.Id,
                                name: String,
                                bio: Option[String],
                                avatar: Option[Avatar],
                                host: Boolean,
                                response: String,
                                guests: Int,
                                updated: Instant)
