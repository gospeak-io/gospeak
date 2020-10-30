package gospeak.infra.services.storage.sql.utils

import java.time.{Instant, LocalDate, LocalDateTime}

import cats.data.NonEmptyList
import doobie.util.Read
import doobie.util.meta.Meta
import fr.loicknuchel.safeql.{Page => SqlPage, Query => SqlQuery}
import gospeak.core.ApplicationConf
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.BasicCtx
import gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup, MeetupUser, MeetupVenue}
import gospeak.core.services.slack.domain.{SlackAction, SlackToken}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.duration.{Duration, FiniteDuration}

object DoobieMappings {

  implicit class BasicCtxConverter(val ctx: BasicCtx) extends AnyVal {
    def toSql: SqlQuery.Ctx = SqlQuery.Ctx.Basic(ctx.now)
  }

  implicit class PageParamsConverter(val params: Page.Params) extends AnyVal {
    def toSql: SqlPage.Params = SqlPage.Params(
      page = params.page.value,
      pageSize = params.pageSize.value,
      search = params.search.map(_.value),
      orderBy = params.orderBy.map(_.values.toList).getOrElse(List()),
      filters = params.filters,
      nullsFirst = params.nullsFirst)
  }

  implicit class SqlPageConverter[A](val page: SqlPage[A]) extends AnyVal {
    def fromSql: Page[A] = Page[A](
      items = page.items,
      params = Page.Params(
        page = Page.No(page.params.page),
        pageSize = Page.Size(page.params.pageSize.toInt),
        search = page.params.search.map(Page.Search(_)),
        orderBy = Page.OrderBy.from(page.params.orderBy),
        filters = page.params.filters,
        nullsFirst = page.params.nullsFirst),
      total = Page.Total(page.total))
  }

  import scala.reflect.runtime.universe._

  // https://tpolecat.github.io/doobie/docs/12-Custom-Mappings.html#single-column-type-mappings
  // https://github.com/tpolecat/doobie/tree/f04a7a3cab5aecb50be0d1ad10fbdae6b8db5ec2/modules/core/src/main/scala/doobie/util/meta
  implicit val stringMeta: Meta[String] = Meta.StringMeta
  implicit val intMeta: Meta[Int] = Meta.IntMeta
  implicit val longMeta: Meta[Long] = Meta.LongMeta
  implicit val instantMeta: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta // required by postgres (cf https://github.com/tpolecat/doobie/releases/tag/v0.8.8)
  implicit val localDateMeta: Meta[LocalDate] = doobie.implicits.legacy.localdate.JavaTimeLocalDateMeta // required by postgres (cf https://github.com/tpolecat/doobie/releases/tag/v0.8.8)
  implicit val localDateTimeMeta: Meta[LocalDateTime] = doobie.implicits.javatime.JavaTimeLocalDateTimeMeta
  implicit val timePeriodMeta: Meta[TimePeriod] = stringMeta.timap(TimePeriod.from(_).get)(_.value)
  implicit val finiteDurationMeta: Meta[FiniteDuration] = longMeta.timap(Duration.fromNanos)(_.toNanos)
  implicit val envMeta: Meta[ApplicationConf.Env] = stringMeta.timap(ApplicationConf.Env.from(_).get)(_.value)
  implicit val emailAddressMeta: Meta[EmailAddress] = stringMeta.timap(EmailAddress.from(_).get)(_.value)
  implicit val urlMeta: Meta[Url] = stringMeta.timap(Url.from(_).get)(_.value)
  implicit val urlSlidesMeta: Meta[Url.Slides] = stringMeta.timap(Url.Slides.from(_).get)(_.value)
  implicit val urlVideoMeta: Meta[Url.Video] = stringMeta.timap(Url.Video.from(_).get)(_.value)
  implicit val urlVideoIdMeta: Meta[Url.Video.Id] = stringMeta.timap(Url.Video.Id)(_.value)
  implicit val urlVideosMeta: Meta[Url.Videos] = stringMeta.timap(Url.Videos.from(_).get)(_.value)
  implicit val urlVideosChannelIdMeta: Meta[Url.Videos.Channel.Id] = stringMeta.timap(Url.Videos.Channel.Id)(_.value)
  implicit val urlVideosPlaylistIdMeta: Meta[Url.Videos.Playlist.Id] = stringMeta.timap(Url.Videos.Playlist.Id)(_.value)
  implicit val urlTwitterMeta: Meta[Url.Twitter] = stringMeta.timap(Url.Twitter.from(_).get)(_.value)
  implicit val urlLinkedInMeta: Meta[Url.LinkedIn] = stringMeta.timap(Url.LinkedIn.from(_).get)(_.value)
  implicit val urlYouTubeMeta: Meta[Url.YouTube] = stringMeta.timap(Url.YouTube.from(_).get)(_.value)
  implicit val urlMeetupMeta: Meta[Url.Meetup] = stringMeta.timap(Url.Meetup.from(_).get)(_.value)
  implicit val urlGithubMeta: Meta[Url.Github] = stringMeta.timap(Url.Github.from(_).get)(_.value)
  implicit val logoMeta: Meta[Logo] = urlMeta.timap(Logo)(_.url)
  implicit val bannerMeta: Meta[Banner] = urlMeta.timap(Banner)(_.url)
  implicit val avatarMeta: Meta[Avatar] = urlMeta.timap(Avatar)(_.url)
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
  implicit val twitterHashtagMeta: Meta[TwitterHashtag] = stringMeta.timap(TwitterHashtag.from(_).get)(_.value)
  implicit val currencyMeta: Meta[Price.Currency] = stringMeta.timap(Price.Currency.from(_).get)(_.value)
  implicit val markdownMeta: Meta[Markdown] = stringMeta.timap(Markdown(_))(_.value)
  implicit val cryptedMeta: Meta[Crypted] = stringMeta.timap(Crypted(_))(_.value)

  implicit def liquidMeta[A: TypeTag]: Meta[Liquid[A]] = stringMeta.timap(Liquid[A])(_.value)

  implicit def liquidMarkdownMeta[A: TypeTag]: Meta[LiquidMarkdown[A]] = stringMeta.timap(LiquidMarkdown[A])(_.value)

  // "take(150)": I prefer truncated tags than failing request
  implicit val tagsMeta: Meta[List[Tag]] = stringMeta.timap(_.split(",").filter(_.nonEmpty).map(Tag(_)).toList)(_.map(_.value).mkString(",").take(150))
  implicit val gMapPlaceMeta: Meta[GMapPlace] = {
    implicit val geoDecoder: Decoder[Geo] = deriveDecoder[Geo]
    implicit val geoEncoder: Encoder[Geo] = deriveEncoder[Geo]
    implicit val gMapPlaceDecoder: Decoder[GMapPlace] = deriveDecoder[GMapPlace]
    implicit val gMapPlaceEncoder: Encoder[GMapPlace] = deriveEncoder[GMapPlace]
    stringMeta.timap(fromJson[GMapPlace](_).get)(toJson)
  }
  implicit val groupSettingsEventTemplatesMeta: Meta[Map[String, Liquid[Message.EventInfo]]] = {
    implicit val liquidDecoder: Decoder[Liquid[Message.EventInfo]] = deriveDecoder[Liquid[Message.EventInfo]]
    implicit val liquidEncoder: Encoder[Liquid[Message.EventInfo]] = deriveEncoder[Liquid[Message.EventInfo]]
    stringMeta.timap(fromJson[Map[String, Liquid[Message.EventInfo]]](_).get)(toJson)
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
    stringMeta.timap(fromJson[Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]](_).get)(toJson)
  }

  // TODO build Meta[List[A]] and Meta[NonEmptyList[A]]
  // implicit def listMeta[A](implicit m: Meta[A]): Meta[List[A]] = ???
  // implicit def nelMeta[A](implicit m: Meta[A]): Meta[NonEmptyList[A]] = ???

  implicit val userProviderIdMeta: Meta[User.ProviderId] = stringMeta.timap(User.ProviderId)(_.value)
  implicit val userProviderKeyMeta: Meta[User.ProviderKey] = stringMeta.timap(User.ProviderKey)(_.value)
  implicit val userHasherMeta: Meta[User.Hasher] = stringMeta.timap(User.Hasher)(_.value)
  implicit val userPasswordValueMeta: Meta[User.PasswordValue] = stringMeta.timap(User.PasswordValue)(_.value)
  implicit val userSaltMeta: Meta[User.Salt] = stringMeta.timap(User.Salt)(_.value)
  implicit val userIdMeta: Meta[User.Id] = stringMeta.timap(User.Id.from(_).get)(_.value)
  implicit val userSlugMeta: Meta[User.Slug] = stringMeta.timap(User.Slug.from(_).get)(_.value)
  implicit val userStatusMeta: Meta[User.Status] = stringMeta.timap(User.Status.from(_).get)(_.value)
  implicit val userRequestIdMeta: Meta[UserRequest.Id] = stringMeta.timap(UserRequest.Id.from(_).get)(_.value)
  implicit val talkIdMeta: Meta[Talk.Id] = stringMeta.timap(Talk.Id.from(_).get)(_.value)
  implicit val talkSlugMeta: Meta[Talk.Slug] = stringMeta.timap(Talk.Slug.from(_).get)(_.value)
  implicit val talkTitleMeta: Meta[Talk.Title] = stringMeta.timap(Talk.Title)(_.value)
  implicit val talkStatusMeta: Meta[Talk.Status] = stringMeta.timap(Talk.Status.from(_).get)(_.value)
  implicit val groupIdMeta: Meta[Group.Id] = stringMeta.timap(Group.Id.from(_).get)(_.value)
  implicit val groupSlugMeta: Meta[Group.Slug] = stringMeta.timap(Group.Slug.from(_).get)(_.value)
  implicit val groupStatusMeta: Meta[Group.Status] = stringMeta.timap(Group.Status.from(_).get)(_.value)
  implicit val groupNameMeta: Meta[Group.Name] = stringMeta.timap(Group.Name)(_.value)
  implicit val eventIdMeta: Meta[Event.Id] = stringMeta.timap(Event.Id.from(_).get)(_.value)
  implicit val eventSlugMeta: Meta[Event.Slug] = stringMeta.timap(Event.Slug.from(_).get)(_.value)
  implicit val eventNameMeta: Meta[Event.Name] = stringMeta.timap(Event.Name)(_.value)
  implicit val eventKindMeta: Meta[Event.Kind] = stringMeta.timap(Event.Kind.from(_).get)(_.value)
  implicit val cfpIdMeta: Meta[Cfp.Id] = stringMeta.timap(Cfp.Id.from(_).get)(_.value)
  implicit val cfpSlugMeta: Meta[Cfp.Slug] = stringMeta.timap(Cfp.Slug.from(_).get)(_.value)
  implicit val cfpNameMeta: Meta[Cfp.Name] = stringMeta.timap(Cfp.Name)(_.value)
  implicit val proposalIdMeta: Meta[Proposal.Id] = stringMeta.timap(Proposal.Id.from(_).get)(_.value)
  implicit val proposalStatusMeta: Meta[Proposal.Status] = stringMeta.timap(Proposal.Status.from(_).get)(_.value)
  implicit val partnerIdMeta: Meta[Partner.Id] = stringMeta.timap(Partner.Id.from(_).get)(_.value)
  implicit val partnerSlugMeta: Meta[Partner.Slug] = stringMeta.timap(Partner.Slug.from(_).get)(_.value)
  implicit val partnerNameMeta: Meta[Partner.Name] = stringMeta.timap(Partner.Name)(_.value)
  implicit val venueIdMeta: Meta[Venue.Id] = stringMeta.timap(Venue.Id.from(_).get)(_.value)
  implicit val sponsorPackIdMeta: Meta[SponsorPack.Id] = stringMeta.timap(SponsorPack.Id.from(_).get)(_.value)
  implicit val sponsorPackSlugMeta: Meta[SponsorPack.Slug] = stringMeta.timap(SponsorPack.Slug.from(_).get)(_.value)
  implicit val sponsorPackNameMeta: Meta[SponsorPack.Name] = stringMeta.timap(SponsorPack.Name)(_.value)
  implicit val sponsorIdMeta: Meta[Sponsor.Id] = stringMeta.timap(Sponsor.Id.from(_).get)(_.value)
  implicit val contactIdMeta: Meta[Contact.Id] = stringMeta.timap(Contact.Id.from(_).right.get)(_.value)
  implicit val commentIdMeta: Meta[Comment.Id] = stringMeta.timap(Comment.Id.from(_).get)(_.value)
  implicit val commentKindMeta: Meta[Comment.Kind] = stringMeta.timap(Comment.Kind.from(_).get)(_.value)
  implicit val meetupGroupSlugMeta: Meta[MeetupGroup.Slug] = stringMeta.timap(MeetupGroup.Slug.from(_).get)(_.value)
  implicit val meetupEventIdMeta: Meta[MeetupEvent.Id] = longMeta.timap(MeetupEvent.Id(_))(_.value)
  implicit val meetupUserIdMeta: Meta[MeetupUser.Id] = longMeta.timap(MeetupUser.Id)(_.value)
  implicit val meetupVenueIdMeta: Meta[MeetupVenue.Id] = longMeta.timap(MeetupVenue.Id(_))(_.value)
  implicit val slackTokenMeta: Meta[SlackToken] = cryptedMeta.timap(SlackToken(_))(_.value)
  implicit val memberRoleMeta: Meta[Group.Member.Role] = stringMeta.timap(Group.Member.Role.from(_).get)(_.value)
  implicit val rsvpAnswerMeta: Meta[Event.Rsvp.Answer] = stringMeta.timap(Event.Rsvp.Answer.from(_).get)(_.value)
  implicit val externalEventIdMeta: Meta[ExternalEvent.Id] = stringMeta.timap(ExternalEvent.Id.from(_).get)(_.value)
  implicit val externalCfpIdMeta: Meta[ExternalCfp.Id] = stringMeta.timap(ExternalCfp.Id.from(_).get)(_.value)
  implicit val externalProposalIdMeta: Meta[ExternalProposal.Id] = stringMeta.timap(ExternalProposal.Id.from(_).get)(_.value)
  implicit val voteMeta: Meta[Proposal.Rating.Grade] = intMeta.timap(Proposal.Rating.Grade.from(_).get)(_.value)

  implicit val userIdNelMeta: Meta[NonEmptyList[User.Id]] = stringMeta.timap(
    _.split(",").filter(_.nonEmpty).map(User.Id.from(_).get).toNelUnsafe)(
    _.map(_.value).toList.mkString(","))
  implicit val proposalIdListMeta: Meta[List[Proposal.Id]] = stringMeta.timap(
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
