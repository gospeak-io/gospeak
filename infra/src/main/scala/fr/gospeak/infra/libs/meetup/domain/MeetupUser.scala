package fr.gospeak.infra.libs.meetup.domain

final case class MeetupUser(id: Long,
                            name: String,
                            bio: Option[String],
                            photo: MeetupPhoto,
                            link: Option[String],
                            city: String,
                            hometown: Option[String],
                            country: String,
                            lon: Double,
                            lat: Double,
                            lang: Option[String],
                            status: String)

object MeetupUser {

  final case class Alt(id: Long,
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

  final case class Basic(id: Long,
                         name: String,
                         bio: String,
                         photo: MeetupPhoto)

  final case class Member(id: Long,
                          name: String,
                          email: Option[String],
                          bio: Option[String],
                          status: String, // ex: "active"
                          joined: Long,
                          city: String,
                          country: String,
                          localized_country_name: String,
                          lat: Double,
                          lon: Double,
                          photo: Option[MeetupPhoto],
                          group_profile: Member.GroupProfile,
                          is_pro_admin: Boolean) {
    def isOrga: Boolean = group_profile.isOrga
  }

  object Member {

    final case class GroupProfile(status: String, // ex: "active"
                                  visited: Long,
                                  created: Long,
                                  updated: Long,
                                  role: Option[String], // ex: "organizer", "coorganizer"
                                  group: GroupDetails,
                                  intro: Option[String],
                                  link: String) {
      def isOrga: Boolean = role.exists(r => r == "organizer" || r == "coorganizer")
    }

    final case class GroupDetails(id: Long,
                                  urlname: String,
                                  name: String,
                                  status: String, // ex: "active"
                                  who: String,
                                  members: Int,
                                  join_mode: String, // ex: "open"
                                  localized_location: String,
                                  group_photo: MeetupPhoto)

  }

}
