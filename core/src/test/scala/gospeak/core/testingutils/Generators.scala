package gospeak.core.testingutils

import java.time._

import gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest, UserAskToJoinAGroupRequest}
import gospeak.core.domain._
import gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import gospeak.core.domain.utils.{Constants, Info}
import gospeak.core.services.meetup.domain.MeetupGroup
import gospeak.libs.scala.Crypto
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.MustacheTmpl.MustacheMarkdownTmpl
import gospeak.libs.scala.domain.TimePeriod.PeriodUnit
import gospeak.libs.scala.domain._
import org.scalacheck.ScalacheckShapeless._
import org.scalacheck.{Arbitrary, Gen}

import scala.collection.JavaConverters._
import scala.concurrent.duration.{FiniteDuration, TimeUnit}
import scala.util.Try

object Generators {
  private val _ = coproductCogen // to keep the `org.scalacheck.ScalacheckShapeless._` import
  private val stringGen = implicitly[Arbitrary[String]].arbitrary
  private val nonEmptyStringGen = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
  private val slugGen = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString.take(SlugBuilder.maxLength).toLowerCase)

  private def buildDuration(length: Long, unit: TimeUnit): FiniteDuration = Try(new FiniteDuration(length, unit)).getOrElse(buildDuration(length / 2, unit))

  implicit val aFiniteDuration: Arbitrary[FiniteDuration] = Arbitrary(for {
    length <- implicitly[Arbitrary[Long]].arbitrary
    unit <- implicitly[Arbitrary[TimeUnit]].arbitrary
  } yield buildDuration(length, unit))
  implicit val aPeriodUnit: Arbitrary[PeriodUnit] = Arbitrary(Gen.oneOf(PeriodUnit.all))
  implicit val aTimePeriod: Arbitrary[TimePeriod] = Arbitrary(for {
    length <- implicitly[Arbitrary[Long]].arbitrary
    unit <- implicitly[Arbitrary[PeriodUnit]].arbitrary
  } yield TimePeriod(length, unit))
  implicit val aInstant: Arbitrary[Instant] = Arbitrary(Gen.calendar.map(_.toInstant))
  implicit val aLocalDate: Arbitrary[LocalDate] = Arbitrary(Gen.calendar.map(_.toInstant.atZone(Constants.defaultZoneId).toLocalDate))
  implicit val aLocalDateTime: Arbitrary[LocalDateTime] = Arbitrary(Gen.calendar.map(_.toInstant.atZone(Constants.defaultZoneId).toLocalDateTime))
  implicit val aZoneId: Arbitrary[ZoneId] = Arbitrary(Gen.oneOf(ZoneId.getAvailableZoneIds.asScala.toSeq.map(ZoneId.of)))
  implicit val aMarkdown: Arbitrary[Markdown] = Arbitrary(stringGen.map(str => Markdown(str)))
  implicit val aSecret: Arbitrary[Secret] = Arbitrary(stringGen.map(str => Secret(str)))
  implicit val aSlides: Arbitrary[Slides] = Arbitrary(slugGen.map(slug => Slides.from(s"http://docs.google.com/presentation/d/${slug.take(82)}").get))
  implicit val aVideo: Arbitrary[Video] = Arbitrary(slugGen.map(slug => Video.from(s"http://youtu.be/${slug.take(104)}").get))
  implicit val aEmailAddress: Arbitrary[EmailAddress] = Arbitrary(nonEmptyStringGen.map(slug => EmailAddress.from(slug.take(110) + "e@mail.com").get)) // TODO improve
  implicit val aUrl: Arbitrary[Url] = Arbitrary(stringGen.map(_ => Url.from("https://www.youtube.com/watch").get)) // TODO improve
  implicit val aLogo: Arbitrary[Logo] = Arbitrary(aUrl.arbitrary.map(Logo))
  implicit val aBanner: Arbitrary[Banner] = Arbitrary(aUrl.arbitrary.map(Banner))
  implicit val aAvatar: Arbitrary[Avatar] = Arbitrary(aEmailAddress.arbitrary.map(e => Avatar(Url.from(s"https://secure.gravatar.com/avatar/${Crypto.md5(e.value)}").get))) // TODO improve
  implicit val aFacebookAccount: Arbitrary[FacebookAccount] = Arbitrary(aUrl.arbitrary.map(FacebookAccount))
  implicit val aInstagramAccount: Arbitrary[InstagramAccount] = Arbitrary(aUrl.arbitrary.map(InstagramAccount))
  implicit val aTwitterAccount: Arbitrary[TwitterAccount] = Arbitrary(aUrl.arbitrary.map(TwitterAccount))
  implicit val aLinkedInAccount: Arbitrary[LinkedInAccount] = Arbitrary(aUrl.arbitrary.map(LinkedInAccount))
  implicit val aYoutubeAccount: Arbitrary[YoutubeAccount] = Arbitrary(aUrl.arbitrary.map(YoutubeAccount))
  implicit val aMeetupAccount: Arbitrary[MeetupAccount] = Arbitrary(aUrl.arbitrary.map(MeetupAccount))
  implicit val aEventbriteAccount: Arbitrary[EventbriteAccount] = Arbitrary(aUrl.arbitrary.map(EventbriteAccount))
  implicit val aSlackAccount: Arbitrary[SlackAccount] = Arbitrary(aUrl.arbitrary.map(SlackAccount))
  implicit val aDiscordAccount: Arbitrary[DiscordAccount] = Arbitrary(aUrl.arbitrary.map(DiscordAccount))
  implicit val aGithubAccount: Arbitrary[GithubAccount] = Arbitrary(aUrl.arbitrary.map(GithubAccount))
  implicit val aTwitterHashtag: Arbitrary[TwitterHashtag] = Arbitrary(nonEmptyStringGen.map(e => TwitterHashtag.from(e.replaceAll(" ", "")).get))
  implicit val aTag: Arbitrary[Tag] = Arbitrary(nonEmptyStringGen.map(str => Tag(str.take(Tag.maxSize))))
  implicit val aTags: Arbitrary[Seq[Tag]] = Arbitrary(Gen.listOf(aTag.arbitrary).map(_.take(Tag.maxNumber)))
  implicit val aCurrency: Arbitrary[Price.Currency] = Arbitrary(Gen.oneOf(Price.Currency.all))
  implicit val aGeo: Arbitrary[Geo] = Arbitrary(for {
    lat <- Gen.chooseNum[Double](-90, 90)
    lng <- Gen.chooseNum[Double](-180, 180)
  } yield Geo(lat, lng))

  implicit val aUserId: Arbitrary[User.Id] = Arbitrary(Gen.uuid.map(id => User.Id.from(id.toString).get))
  implicit val aUserSlug: Arbitrary[User.Slug] = Arbitrary(slugGen.map(slug => User.Slug.from(slug).get))
  implicit val aTalkId: Arbitrary[Talk.Id] = Arbitrary(Gen.uuid.map(id => Talk.Id.from(id.toString).get))
  implicit val aTalkSlug: Arbitrary[Talk.Slug] = Arbitrary(slugGen.map(slug => Talk.Slug.from(slug).get))
  implicit val aTalkTitle: Arbitrary[Talk.Title] = Arbitrary(nonEmptyStringGen.map(str => Talk.Title(str)))
  implicit val aTalkStatus: Arbitrary[Talk.Status] = Arbitrary(Gen.oneOf(Talk.Status.all))
  implicit val aGroupId: Arbitrary[Group.Id] = Arbitrary(Gen.uuid.map(id => Group.Id.from(id.toString).get))
  implicit val aGroupSlug: Arbitrary[Group.Slug] = Arbitrary(slugGen.map(slug => Group.Slug.from(slug).get))
  implicit val aGroupName: Arbitrary[Group.Name] = Arbitrary(nonEmptyStringGen.map(str => Group.Name(str)))
  implicit val aCfpId: Arbitrary[Cfp.Id] = Arbitrary(Gen.uuid.map(id => Cfp.Id.from(id.toString).get))
  implicit val aCfpSlug: Arbitrary[Cfp.Slug] = Arbitrary(slugGen.map(slug => Cfp.Slug.from(slug).get))
  implicit val aCfpName: Arbitrary[Cfp.Name] = Arbitrary(nonEmptyStringGen.map(str => Cfp.Name(str)))
  implicit val aEventId: Arbitrary[Event.Id] = Arbitrary(Gen.uuid.map(id => Event.Id.from(id.toString).get))
  implicit val aEventSlug: Arbitrary[Event.Slug] = Arbitrary(slugGen.map(slug => Event.Slug.from(slug).get))
  implicit val aEventName: Arbitrary[Event.Name] = Arbitrary(nonEmptyStringGen.map(str => Event.Name(str)))
  implicit val aProposalId: Arbitrary[Proposal.Id] = Arbitrary(Gen.uuid.map(id => Proposal.Id.from(id.toString).get))
  implicit val aProposalStatus: Arbitrary[Proposal.Status] = Arbitrary(Gen.oneOf(Proposal.Status.all))
  implicit val aPartnerId: Arbitrary[Partner.Id] = Arbitrary(Gen.uuid.map(id => Partner.Id.from(id.toString).get))
  implicit val aPartnerSlug: Arbitrary[Partner.Slug] = Arbitrary(slugGen.map(slug => Partner.Slug.from(slug).get))
  implicit val aPartnerName: Arbitrary[Partner.Name] = Arbitrary(nonEmptyStringGen.map(str => Partner.Name(str)))
  implicit val aVenueId: Arbitrary[Venue.Id] = Arbitrary(Gen.uuid.map(id => Venue.Id.from(id.toString).get))
  implicit val aSponsorPackId: Arbitrary[SponsorPack.Id] = Arbitrary(Gen.uuid.map(id => SponsorPack.Id.from(id.toString).get))
  implicit val aSponsorPackSlug: Arbitrary[SponsorPack.Slug] = Arbitrary(slugGen.map(slug => SponsorPack.Slug.from(slug).get))
  implicit val aSponsorPackName: Arbitrary[SponsorPack.Name] = Arbitrary(nonEmptyStringGen.map(str => SponsorPack.Name(str)))
  implicit val aSponsorId: Arbitrary[Sponsor.Id] = Arbitrary(Gen.uuid.map(id => Sponsor.Id.from(id.toString).get))
  implicit val aContactId: Arbitrary[Contact.Id] = Arbitrary(Gen.uuid.map(id => Contact.Id.from(id.toString).get))
  implicit val aContactFirstName: Arbitrary[Contact.FirstName] = Arbitrary(nonEmptyStringGen.map(f => Contact.FirstName(f)))
  implicit val aContactLastName: Arbitrary[Contact.LastName] = Arbitrary(nonEmptyStringGen.map(l => Contact.LastName(l)))
  implicit val aCommentId: Arbitrary[Comment.Id] = Arbitrary(Gen.uuid.map(id => Comment.Id.from(id.toString).get))
  implicit val aMeetupGroupSlug: Arbitrary[MeetupGroup.Slug] = Arbitrary(slugGen.map(slug => MeetupGroup.Slug.from(slug).get))
  implicit val aUserRequestId: Arbitrary[UserRequest.Id] = Arbitrary(Gen.uuid.map(id => UserRequest.Id.from(id.toString).get))
  implicit val aExternalEventId: Arbitrary[ExternalEvent.Id] = Arbitrary(Gen.uuid.map(id => ExternalEvent.Id.from(id.toString).get))
  implicit val aExternalEventName: Arbitrary[ExternalEvent.Name] = Arbitrary(nonEmptyStringGen.map(str => ExternalEvent.Name(str)))
  implicit val aExternalCfpId: Arbitrary[ExternalCfp.Id] = Arbitrary(Gen.uuid.map(id => ExternalCfp.Id.from(id.toString).get))
  implicit val aExternalCfpName: Arbitrary[ExternalCfp.Name] = Arbitrary(nonEmptyStringGen.map(str => ExternalCfp.Name(str)))
  implicit val aExternalProposalId: Arbitrary[ExternalProposal.Id] = Arbitrary(Gen.uuid.map(id => ExternalProposal.Id.from(id.toString).get))
  implicit val aExternalProposalTitle: Arbitrary[ExternalProposal.Title] = Arbitrary(nonEmptyStringGen.map(str => ExternalProposal.Title(str)))

  // do not write explicit type, it will throw a NullPointerException
  implicit val aInfo = implicitly[Arbitrary[Info]]
  implicit val aPrice = implicitly[Arbitrary[Price]]
  implicit val aGMapPlace = implicitly[Arbitrary[GMapPlace]]
  implicit val aUser = implicitly[Arbitrary[User]]
  implicit val aTalk = implicitly[Arbitrary[Talk]]
  implicit val aGroup = implicitly[Arbitrary[Group]]
  implicit val aCfp = implicitly[Arbitrary[Cfp]]
  implicit val aEvent = implicitly[Arbitrary[Event]]
  implicit val aProposal = implicitly[Arbitrary[Proposal]]
  implicit val aSponsor = implicitly[Arbitrary[Sponsor]]
  implicit val aAccountValidationRequest = implicitly[Arbitrary[AccountValidationRequest]]
  implicit val aPasswordResetRequest = implicitly[Arbitrary[PasswordResetRequest]]
  implicit val aUserAskToJoinAGroupRequest = implicitly[Arbitrary[UserAskToJoinAGroupRequest]]
  implicit val aUserRequest = implicitly[Arbitrary[UserRequest]]
  implicit val aExternalCfp = implicitly[Arbitrary[ExternalCfp]]

  implicit val aTemplate = implicitly[Arbitrary[MustacheMarkdownTmpl[Any]]]
  implicit val aGroupSettingsActionTrigger = implicitly[Arbitrary[Group.Settings.Action.Trigger]]
  implicit val aGroupSettingsAction = implicitly[Arbitrary[Group.Settings.Action]]
}
