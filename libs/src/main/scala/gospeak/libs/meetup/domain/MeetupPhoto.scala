package gospeak.libs.meetup.domain

final case class MeetupPhoto(id: Long,
                             `type`: String, // ex: "member"
                             base_url: String,
                             thumb_link: String,
                             photo_link: String,
                             highres_link: Option[String])

object MeetupPhoto {

  final case class Alt(photo_id: Long,
                       `type`: String,
                       base_url: String,
                       thumb_link: String,
                       photo_link: String,
                       highres_link: String)

}
