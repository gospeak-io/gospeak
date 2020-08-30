package gospeak.infra.services.storage.sql.utils

import java.time.{Instant, LocalDateTime, ZoneOffset}

import cats.data.NonEmptyList
import doobie.util.{Meta, Read}
import gospeak.core.ApplicationConf
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import gospeak.core.domain._
import gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup}
import gospeak.core.services.slack.domain.SlackAction
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe._

import scala.concurrent.duration.{Duration, FiniteDuration}

object DoobieMappings {

  import scala.reflect.runtime.universe._

  implicit val envMeta: Meta[ApplicationConf.Env] = Meta[String].timap(ApplicationConf.Env.from(_).get)(_.value)
  implicit val timePeriodMeta: Meta[TimePeriod] = Meta[String].timap(TimePeriod.from(_).get)(_.value)
  implicit val finiteDurationMeta: Meta[FiniteDuration] = Meta[Long].timap(Duration.fromNanos)(_.toNanos)
  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Instant].timap(LocalDateTime.ofInstant(_, ZoneOffset.UTC))(_.toInstant(ZoneOffset.UTC))
  implicit val emailAddressMeta: Meta[EmailAddress] = Meta[String].timap(EmailAddress.from(_).get)(_.value)
  implicit val urlMeta: Meta[Url] = Meta[String].timap(Url.from(_).get)(_.value)
  implicit val urlSlidesMeta: Meta[Url.Slides] = Meta[String].timap(Url.Slides.from(_).get)(_.value)
  implicit val urlVideoMeta: Meta[Url.Video] = Meta[String].timap(Url.Video.from(_).get)(_.value)
  implicit val urlVideoIdMeta: Meta[Url.Video.Id] = Meta[String].timap(Url.Video.Id)(_.value)
  implicit val urlVideosMeta: Meta[Url.Videos] = Meta[String].timap(Url.Videos.from(_).get)(_.value)
  implicit val urlVideosChannelIdMeta: Meta[Url.Videos.Channel.Id] = Meta[String].timap(Url.Videos.Channel.Id)(_.value)
  implicit val urlVideosPlaylistIdMeta: Meta[Url.Videos.Playlist.Id] = Meta[String].timap(Url.Videos.Playlist.Id)(_.value)
  implicit val urlTwitterMeta: Meta[Url.Twitter] = Meta[String].timap(Url.Twitter.from(_).get)(_.value)
  implicit val urlLinkedInMeta: Meta[Url.LinkedIn] = Meta[String].timap(Url.LinkedIn.from(_).get)(_.value)
  implicit val urlYouTubeMeta: Meta[Url.YouTube] = Meta[String].timap(Url.YouTube.from(_).get)(_.value)
  implicit val urlMeetupMeta: Meta[Url.Meetup] = Meta[String].timap(Url.Meetup.from(_).get)(_.value)
  implicit val urlGithubMeta: Meta[Url.Github] = Meta[String].timap(Url.Github.from(_).get)(_.value)
  implicit val logoMeta: Meta[Logo] = urlMeta.timap(Logo)(_.url)
  implicit val bannerMeta: Meta[Banner] = urlMeta.timap(Banner)(_.url)
  implicit val facebookAccountMeta: Meta[FacebookAccount] = urlMeta.timap(FacebookAccount)(_.url)
  implicit val instagramAccountMeta: Meta[InstagramAccount] = urlMeta.timap(InstagramAccount)(_.url)
  implicit val twitterAccountMeta: Meta[TwitterAccount] = urlTwitterMeta.timap(TwitterAccount)(_.url)
  implicit val linkedInAccountMeta: Meta[LinkedInAccount] = urlLinkedInMeta.timap(LinkedInAccount)(_.url)
  implicit val youtubeAccountMeta: Meta[YoutubeAccount] = urlYouTubeMeta.timap(YoutubeAccount)(_.url)
  implicit val meetupAccountMeta: Meta[MeetupAccount] = urlMeetupMeta.timap(MeetupAccount)(_.url)
  implicit val eventbriteAccountMeta: Meta[EventbriteAccount] = urlMeta.timap(EventbriteAccount)(_.url)
  implicit val slackAccountMeta: Meta[SlackAccount] = urlMeta.timap(SlackAccount)(_.url)
  implicit val discordAccountMeta: Meta[DiscordAccount] = urlMeta.timap(DiscordAccount)(_.url)
  implicit val githubAccountMeta: Meta[GithubAccount] = urlGithubMeta.timap(GithubAccount)(_.url)
  implicit val twitterHashtagMeta: Meta[TwitterHashtag] = Meta[String].timap(TwitterHashtag.from(_).get)(_.value)
  implicit val currencyMeta: Meta[Price.Currency] = Meta[String].timap(Price.Currency.from(_).get)(_.value)
  implicit val markdownMeta: Meta[Markdown] = Meta[String].timap(Markdown(_))(_.value)

  implicit def liquidMeta[A: TypeTag]: Meta[Liquid[A]] = Meta[String].timap(Liquid[A])(_.value)

  implicit def liquidMarkdownMeta[A: TypeTag]: Meta[LiquidMarkdown[A]] = Meta[String].timap(LiquidMarkdown[A])(_.value)

  // "take(150)": I prefer truncated tags than failing request
  implicit val tagsMeta: Meta[List[Tag]] = Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Tag(_)).toList)(_.map(_.value).mkString(",").take(150))
  implicit val gMapPlaceMeta: Meta[GMapPlace] = {
    implicit val geoDecoder: Decoder[Geo] = deriveDecoder[Geo]
    implicit val geoEncoder: Encoder[Geo] = deriveEncoder[Geo]
    implicit val gMapPlaceDecoder: Decoder[GMapPlace] = deriveDecoder[GMapPlace]
    implicit val gMapPlaceEncoder: Encoder[GMapPlace] = deriveEncoder[GMapPlace]
    Meta[String].timap(fromJson[GMapPlace](_).get)(toJson)
  }
  implicit val groupSettingsEventTemplatesMeta: Meta[Map[String, Liquid[Message.EventInfo]]] = {
    implicit val liquidDecoder: Decoder[Liquid[Message.EventInfo]] = deriveDecoder[Liquid[Message.EventInfo]]
    implicit val liquidEncoder: Encoder[Liquid[Message.EventInfo]] = deriveEncoder[Liquid[Message.EventInfo]]
    Meta[String].timap(fromJson[Map[String, Liquid[Message.EventInfo]]](_).get)(toJson)
  }
  implicit val groupSettingsActionsMeta: Meta[Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]] = {
    implicit val liquidDecoder: Decoder[Liquid[Any]] = deriveDecoder[Liquid[Any]]
    implicit val liquidEncoder: Encoder[Liquid[Any]] = deriveEncoder[Liquid[Any]]
    implicit val liquidMarkdownDecoder: Decoder[LiquidMarkdown[Any]] = deriveDecoder[LiquidMarkdown[Any]]
    implicit val liquidMarkdownEncoder: Encoder[LiquidMarkdown[Any]] = deriveEncoder[LiquidMarkdown[Any]]
    implicit val slackActionPostMessageDecoder: Decoder[SlackAction.PostMessage] = deriveDecoder[SlackAction.PostMessage]
    implicit val slackActionPostMessageEncoder: Encoder[SlackAction.PostMessage] = deriveEncoder[SlackAction.PostMessage]
    implicit val slackActionDecoder: Decoder[SlackAction] = deriveDecoder[SlackAction]
    implicit val slackActionEncoder: Encoder[SlackAction] = deriveEncoder[SlackAction]
    implicit val groupSettingsActionEmailDecoder: Decoder[Group.Settings.Action.Email] = deriveDecoder[Group.Settings.Action.Email]
    implicit val groupSettingsActionEmailEncoder: Encoder[Group.Settings.Action.Email] = deriveEncoder[Group.Settings.Action.Email]
    implicit val groupSettingsActionSlackDecoder: Decoder[Group.Settings.Action.Slack] = deriveDecoder[Group.Settings.Action.Slack]
    implicit val groupSettingsActionSlackEncoder: Encoder[Group.Settings.Action.Slack] = deriveEncoder[Group.Settings.Action.Slack]
    implicit val groupSettingsActionDecoder: Decoder[Group.Settings.Action] = deriveDecoder[Group.Settings.Action]
    implicit val groupSettingsActionEncoder: Encoder[Group.Settings.Action] = deriveEncoder[Group.Settings.Action]
    implicit val groupSettingsActionTriggerDecoder: KeyDecoder[Group.Settings.Action.Trigger] = (key: String) => Group.Settings.Action.Trigger.from(key).toOption
    implicit val groupSettingsActionTriggerEncoder: KeyEncoder[Group.Settings.Action.Trigger] = (e: Group.Settings.Action.Trigger) => e.toString
    Meta[String].timap(fromJson[Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]](_).get)(toJson)
  }

  // TODO build Meta[List[A]] and Meta[NonEmptyList[A]]
  // implicit def listMeta[A](implicit m: Meta[A]): Meta[List[A]] = ???
  // implicit def nelMeta[A](implicit m: Meta[A]): Meta[NonEmptyList[A]] = ???

  implicit val userIdMeta: Meta[User.Id] = Meta[String].timap(User.Id.from(_).get)(_.value)
  implicit val userSlugMeta: Meta[User.Slug] = Meta[String].timap(User.Slug.from(_).get)(_.value)
  implicit val userStatusMeta: Meta[User.Status] = Meta[String].timap(User.Status.from(_).get)(_.value)
  implicit val userRequestIdMeta: Meta[UserRequest.Id] = Meta[String].timap(UserRequest.Id.from(_).get)(_.value)
  implicit val talkIdMeta: Meta[Talk.Id] = Meta[String].timap(Talk.Id.from(_).get)(_.value)
  implicit val talkSlugMeta: Meta[Talk.Slug] = Meta[String].timap(Talk.Slug.from(_).get)(_.value)
  implicit val talkTitleMeta: Meta[Talk.Title] = Meta[String].timap(Talk.Title)(_.value)
  implicit val talkStatusMeta: Meta[Talk.Status] = Meta[String].timap(Talk.Status.from(_).get)(_.value)
  implicit val groupIdMeta: Meta[Group.Id] = Meta[String].timap(Group.Id.from(_).get)(_.value)
  implicit val groupSlugMeta: Meta[Group.Slug] = Meta[String].timap(Group.Slug.from(_).get)(_.value)
  implicit val groupStatusMeta: Meta[Group.Status] = Meta[String].timap(Group.Status.from(_).get)(_.value)
  implicit val groupNameMeta: Meta[Group.Name] = Meta[String].timap(Group.Name)(_.value)
  implicit val eventIdMeta: Meta[Event.Id] = Meta[String].timap(Event.Id.from(_).get)(_.value)
  implicit val eventSlugMeta: Meta[Event.Slug] = Meta[String].timap(Event.Slug.from(_).get)(_.value)
  implicit val eventNameMeta: Meta[Event.Name] = Meta[String].timap(Event.Name)(_.value)
  implicit val eventKindMeta: Meta[Event.Kind] = Meta[String].timap(Event.Kind.from(_).get)(_.value)
  implicit val cfpIdMeta: Meta[Cfp.Id] = Meta[String].timap(Cfp.Id.from(_).get)(_.value)
  implicit val cfpSlugMeta: Meta[Cfp.Slug] = Meta[String].timap(Cfp.Slug.from(_).get)(_.value)
  implicit val cfpNameMeta: Meta[Cfp.Name] = Meta[String].timap(Cfp.Name)(_.value)
  implicit val proposalIdMeta: Meta[Proposal.Id] = Meta[String].timap(Proposal.Id.from(_).get)(_.value)
  implicit val proposalStatusMeta: Meta[Proposal.Status] = Meta[String].timap(Proposal.Status.from(_).get)(_.value)
  implicit val partnerIdMeta: Meta[Partner.Id] = Meta[String].timap(Partner.Id.from(_).get)(_.value)
  implicit val partnerSlugMeta: Meta[Partner.Slug] = Meta[String].timap(Partner.Slug.from(_).get)(_.value)
  implicit val partnerNameMeta: Meta[Partner.Name] = Meta[String].timap(Partner.Name)(_.value)
  implicit val venueIdMeta: Meta[Venue.Id] = Meta[String].timap(Venue.Id.from(_).get)(_.value)
  implicit val sponsorPackIdMeta: Meta[SponsorPack.Id] = Meta[String].timap(SponsorPack.Id.from(_).get)(_.value)
  implicit val sponsorPackSlugMeta: Meta[SponsorPack.Slug] = Meta[String].timap(SponsorPack.Slug.from(_).get)(_.value)
  implicit val sponsorPackNameMeta: Meta[SponsorPack.Name] = Meta[String].timap(SponsorPack.Name)(_.value)
  implicit val sponsorIdMeta: Meta[Sponsor.Id] = Meta[String].timap(Sponsor.Id.from(_).get)(_.value)
  implicit val contactIdMeta: Meta[Contact.Id] = Meta[String].timap(Contact.Id.from(_).right.get)(_.value)
  implicit val commentIdMeta: Meta[Comment.Id] = Meta[String].timap(Comment.Id.from(_).get)(_.value)
  implicit val commentKindMeta: Meta[Comment.Kind] = Meta[String].timap(Comment.Kind.from(_).get)(_.value)
  implicit val meetupGroupSlugMeta: Meta[MeetupGroup.Slug] = Meta[String].timap(MeetupGroup.Slug.from(_).get)(_.value)
  implicit val meetupEventIdMeta: Meta[MeetupEvent.Id] = Meta[Long].timap(MeetupEvent.Id(_))(_.value)
  implicit val memberRoleMeta: Meta[Group.Member.Role] = Meta[String].timap(Group.Member.Role.from(_).get)(_.value)
  implicit val rsvpAnswerMeta: Meta[Event.Rsvp.Answer] = Meta[String].timap(Event.Rsvp.Answer.from(_).get)(_.value)
  implicit val externalEventIdMeta: Meta[ExternalEvent.Id] = Meta[String].timap(ExternalEvent.Id.from(_).get)(_.value)
  implicit val externalCfpIdMeta: Meta[ExternalCfp.Id] = Meta[String].timap(ExternalCfp.Id.from(_).get)(_.value)
  implicit val externalProposalIdMeta: Meta[ExternalProposal.Id] = Meta[String].timap(ExternalProposal.Id.from(_).get)(_.value)
  implicit val voteMeta: Meta[Proposal.Rating.Grade] = Meta[Int].timap(Proposal.Rating.Grade.from(_).get)(_.value)

  implicit val userIdNelMeta: Meta[NonEmptyList[User.Id]] = Meta[String].timap(
    _.split(",").filter(_.nonEmpty).map(User.Id.from(_).get).toNelUnsafe)(
    _.map(_.value).toList.mkString(","))
  implicit val proposalIdListMeta: Meta[List[Proposal.Id]] = Meta[String].timap(
    _.split(",").filter(_.nonEmpty).map(Proposal.Id.from(_).get).toList)(
    _.map(_.value).mkString(","))

  implicit def eitherRead[A, B](implicit r: Read[(Option[A], Option[B])]): Read[Either[A, B]] = Read[(Option[A], Option[B])].map {
    case (Some(a), None) => Left(a)
    case (None, Some(b)) => Right(b)
    case (None, None) => throw new Exception(s"Unable to read Either, no side is defined")
    case (Some(a), Some(b)) => throw new Exception(s"Unable to read Either, both sides are defined: Left($a) / Right($b)")
  }

  implicit val userRequestRead: Read[UserRequest] =
    Read[(UserRequest.Id, String, Option[Group.Id], Option[Cfp.Id], Option[Event.Id], Option[Talk.Id], Option[Proposal.Id], Option[ExternalEvent.Id], Option[ExternalCfp.Id], Option[ExternalProposal.Id], Option[EmailAddress], Option[String], Instant, Instant, Option[User.Id], Option[Instant], Option[User.Id], Option[Instant], Option[User.Id], Option[Instant], Option[User.Id])].map {
      case (id, "AccountValidation", _, _, _, _, _, _, _, _, Some(email), _, deadline, created, Some(createdBy), accepted, _, _, _, _, _) =>
        UserRequest.AccountValidationRequest(id, email, deadline, created, createdBy, accepted)
      case (id, "PasswordReset", _, _, _, _, _, _, _, _, Some(email), _, deadline, created, _, accepted, _, _, _, _, _) =>
        UserRequest.PasswordResetRequest(id, email, deadline, created, accepted)
      case (id, "UserAskToJoinAGroup", Some(groupId), _, _, _, _, _, _, _, _, _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
        UserRequest.UserAskToJoinAGroupRequest(id, groupId, deadline, created, createdBy,
          accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
          rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
          canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
      case (id, "GroupInvite", Some(groupId), _, _, _, _, _, _, _, Some(email), _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
        UserRequest.GroupInvite(id, groupId, email, deadline, created, createdBy,
          accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
          rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
          canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
      case (id, "TalkInvite", _, _, _, Some(talkId), _, _, _, _, Some(email), _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
        UserRequest.TalkInvite(id, talkId, email, deadline, created, createdBy,
          accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
          rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
          canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
      case (id, "ProposalInvite", _, _, _, _, Some(proposalId), _, _, _, Some(email), _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
        UserRequest.ProposalInvite(id, proposalId, email, deadline, created, createdBy,
          accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
          rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
          canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
      case (id, "ExternalProposalInvite", _, _, _, _, _, _, _, Some(externalProposalId), Some(email), _, deadline, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
        UserRequest.ExternalProposalInvite(id, externalProposalId, email, deadline, created, createdBy,
          accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
          rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
          canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
      case (id, kind, group, cfp, event, talk, proposal, extEvent, extCfp, extProposal, email, payload, deadline, created, createdBy, accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
        throw new Exception(s"Unable to read UserRequest with ($id, $kind, group=$group, cfp=$cfp, event=$event, talk=$talk, proposal=$proposal, extEvent=$extEvent, extCfp=$extCfp, extProposal=$extProposal, $email, payload=$payload, $deadline, created=($created, $createdBy), accepted=($accepted, $acceptedBy), rejected=($rejected, $rejectedBy), canceled=($canceled, $canceledBy))")
    }

  private def toJson[A](v: A)(implicit e: Encoder[A]): String = e.apply(v).noSpaces

  private def fromJson[A](s: String)(implicit d: Decoder[A]): util.Try[A] = parser.parse(s).flatMap(d.decodeJson).toTry
}
