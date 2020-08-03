package gospeak.libs.meetup.domain

// see https://www.meetup.com/meetup_api/docs/:urlname#getresponse
final case class MeetupGroup(id: Long,
                             status: String, // ex: "active"
                             join_mode: String, // ex: "open", "approval"
                             visibility: String, // ex: "public", "public_limited"
                             name: String,
                             urlname: String,
                             link: String,
                             description: String,
                             welcome_message: Option[String],
                             group_photo: Option[MeetupPhoto],
                             key_photo: Option[MeetupPhoto],
                             leads: Int,
                             members: Int,
                             past_event_count: Option[Int],
                             organizer: Option[MeetupUser.Basic],
                             city: String, // ex: "Paris"
                             untranslated_city: String, // ex: "Paris"
                             country: String, // ex: "FR"
                             localized_country_name: String, // ex: "France"
                             localized_location: String, // ex: "Paris, France"
                             timezone: String, // ex: "Europe/Paris"
                             lat: Double,
                             lon: Double,
                             category: MeetupCategory,
                             topics: List[MeetupTopic],
                             self: MeetupGroup.Self,
                             created: Long) {
  def isOrga: Boolean = self.profile.isOrga
}

object MeetupGroup {

  final case class Basic(id: Long,
                         name: String,
                         urlname: String,
                         join_mode: String,
                         lat: Double,
                         lon: Double,
                         country: String,
                         timezone: String,
                         created: Long)

  final case class Self(status: String,
                        actions: List[String],
                        visited: Long,
                        profile: MeetupUser.Member)

}
