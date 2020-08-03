package gospeak.libs.meetup

import gospeak.libs.meetup.domain._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object MeetupJson {
  implicit val meetupTokenDecoder: Decoder[MeetupToken] = deriveDecoder[MeetupToken]
  implicit val meetupCategoryDecoder: Decoder[MeetupCategory] = deriveDecoder[MeetupCategory]
  implicit val meetupTopicDecoder: Decoder[MeetupTopic] = deriveDecoder[MeetupTopic]
  implicit val meetupPhotoDecoder: Decoder[MeetupPhoto] = deriveDecoder[MeetupPhoto]
  implicit val meetupPhotoAltDecoder: Decoder[MeetupPhoto.Alt] = deriveDecoder[MeetupPhoto.Alt]
  implicit val meetupUserDecoder: Decoder[MeetupUser] = deriveDecoder[MeetupUser]
  implicit val meetupUserAltDecoder: Decoder[MeetupUser.Alt] = deriveDecoder[MeetupUser.Alt]
  implicit val meetupUserBasicDecoder: Decoder[MeetupUser.Basic] = deriveDecoder[MeetupUser.Basic]
  implicit val meetupUserMemberGroupDetailsDecoder: Decoder[MeetupUser.Member.GroupDetails] = deriveDecoder[MeetupUser.Member.GroupDetails]
  implicit val meetupUserMemberGroupProfileDecoder: Decoder[MeetupUser.Member.GroupProfile] = deriveDecoder[MeetupUser.Member.GroupProfile]
  implicit val meetupUserMemberDecoder: Decoder[MeetupUser.Member] = deriveDecoder[MeetupUser.Member]
  implicit val meetupVenueDecoder: Decoder[MeetupVenue] = deriveDecoder[MeetupVenue]
  implicit val meetupVenueBasicDecoder: Decoder[MeetupVenue.Basic] = deriveDecoder[MeetupVenue.Basic]
  implicit val meetupGroupDecoder: Decoder[MeetupGroup] = deriveDecoder[MeetupGroup]
  implicit val meetupGroupBasicDecoder: Decoder[MeetupGroup.Basic] = deriveDecoder[MeetupGroup.Basic]
  implicit val meetupGroupSelfDecoder: Decoder[MeetupGroup.Self] = deriveDecoder[MeetupGroup.Self]
  implicit val meetupEventDecoder: Decoder[MeetupEvent] = deriveDecoder[MeetupEvent]
  implicit val meetupAttendeeMemberDecoder: Decoder[MeetupAttendee.Member] = deriveDecoder[MeetupAttendee.Member]
  implicit val meetupAttendeeContextDecoder: Decoder[MeetupAttendee.Context] = deriveDecoder[MeetupAttendee.Context]
  implicit val meetupAttendeeRsvpDecoder: Decoder[MeetupAttendee.Rsvp] = deriveDecoder[MeetupAttendee.Rsvp]
  implicit val meetupAttendeeDecoder: Decoder[MeetupAttendee] = deriveDecoder[MeetupAttendee]
  implicit val meetupLocationDecoder: Decoder[MeetupLocation] = deriveDecoder[MeetupLocation]
  implicit val meetupErrorDecoder: Decoder[MeetupError] = deriveDecoder[MeetupError]
  implicit val meetupErrorNotAuthorizedDecoder: Decoder[MeetupError.NotAuthorized] = deriveDecoder[MeetupError.NotAuthorized]
  implicit val meetupErrorCodeDecoder: Decoder[MeetupError.Code] = deriveDecoder[MeetupError.Code]
  implicit val meetupErrorMultiDecoder: Decoder[MeetupError.Multi] = deriveDecoder[MeetupError.Multi]
}
