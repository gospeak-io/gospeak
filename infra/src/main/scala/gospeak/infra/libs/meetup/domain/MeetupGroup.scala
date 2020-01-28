package gospeak.infra.libs.meetup.domain

final case class MeetupGroup(id: Long,
                             status: String,
                             join_mode: String,
                             visibility: String,
                             name: String,
                             urlname: String,
                             group_photo: MeetupPhoto,
                             key_photo: MeetupPhoto,
                             description: String,
                             organizer: MeetupUser.Basic,
                             link: String,
                             lat: Double,
                             lon: Double,
                             city: String,
                             country: String,
                             timezone: String,
                             members: Int,
                             created: Long)

object MeetupGroup {

  final case class Basic(id: Long,
                         name: String,
                         urlname: String,
                         join_mode: String, // "open"
                         lat: Double,
                         lon: Double,
                         country: String,
                         timezone: String,
                         created: Long)

}
