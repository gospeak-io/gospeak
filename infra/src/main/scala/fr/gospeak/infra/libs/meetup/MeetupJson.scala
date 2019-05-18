package fr.gospeak.infra.libs.meetup

import fr.gospeak.infra.libs.meetup.domain.MeetupGroup
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object MeetupJson {
  implicit val meetupGroupDecoder: Decoder[MeetupGroup] = deriveDecoder[MeetupGroup]
}
