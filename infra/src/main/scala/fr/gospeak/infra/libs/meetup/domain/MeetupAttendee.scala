package fr.gospeak.infra.libs.meetup.domain

final case class MeetupAttendee(member: MeetupAttendee.Member,
                                rsvp: MeetupAttendee.Rsvp)

object MeetupAttendee {

  final case class Member(id: Long,
                          name: String,
                          bio: Option[String],
                          photo: Option[MeetupPhoto],
                          role: Option[String], // ex: "coorganizer"
                          event_context: Option[Context])

  final case class Context(host: Boolean)

  final case class Rsvp(id: Long,
                        response: String, // ex: "no", "yes"
                        guests: Int,
                        updated: Long)

}
