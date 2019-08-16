package fr.gospeak.infra.libs.meetup.domain

final case class MeetupUser(id: Long,
                            name: String,
                            bio: String,
                            photo: MeetupPhoto.Alt,
                            link: String,
                            city: String,
                            hometown: String,
                            country: String,
                            lon: Double,
                            lat: Double,
                            lang: String,
                            status: String)

object MeetupUser {

  final case class Basic(id: Long,
                         name: String,
                         bio: String,
                         photo: MeetupPhoto)

}
