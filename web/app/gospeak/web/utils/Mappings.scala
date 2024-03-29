package gospeak.web.utils

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime}
import java.util.concurrent.TimeUnit
import cats.implicits._
import gospeak.core.domain._
import gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import gospeak.core.domain.utils.{Constants, SocialAccounts}
import gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup, MeetupVenue}
import gospeak.core.services.slack.domain.{SlackAction, SlackToken}
import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._
import gospeak.web.utils.Mappings.Utils._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Constraints, ValidationError, Invalid => PlayInvalid, Valid => PlayValid}
import play.api.data.{FormError, Mapping, WrappedMapping}

import scala.concurrent.duration._
import scala.util.Try

object Mappings {
  val requiredConstraint = "constraint.required"
  val requiredError = "error.required"
  val patternConstraint = "constraint.pattern"
  val numberError = "error.number"
  val datetimeError = "error.datetime"
  val formatError = "error.format"
  val formatConstraint = "constraint.format"
  val passwordConstraint = "constraint.password"
  val passwordError = "error.password"

  val localDateFormat = "dd/MM/yyyy"
  val localDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(localDateFormat)

  val double: Mapping[Double] = of(doubleFormat)
  val instant: Mapping[Instant] = stringEitherMapping[Instant, Throwable](
    from = s => Try(LocalDateTime.parse(s)).map(_.toInstant(Constants.defaultZoneId)).toEither,
    to = _.atZone(Constants.defaultZoneId).toLocalDateTime.toString,
    errorMessage = datetimeError,
    errorArgs = _.getMessage :: Nil)
  val myLocalDateTime: Mapping[LocalDateTime] = mapping(
    "date" -> localDate("dd/MM/yyyy"),
    "time" -> localTime("HH:mm")
  )({ case (d, t) => LocalDateTime.of(d, t) })(dt => Some(dt.toLocalDate -> dt.toLocalTime))
  val chronoUnit: Mapping[ChronoUnit] = stringEitherMapping[ChronoUnit, Throwable](
    from = d => Try(ChronoUnit.valueOf(d)).toEither,
    to = _.name(),
    errorMessage = formatError,
    errorArgs = _.getMessage :: Nil)
  val periodUnit: Mapping[TimePeriod.PeriodUnit] = stringEitherMapping[TimePeriod.PeriodUnit, String](
    from = d => TimePeriod.PeriodUnit.all.find(_.value == d).toEither(s"No period '$d'"),
    to = _.value,
    errorMessage = formatError,
    errorArgs = List(_))
  val period: Mapping[TimePeriod] = mapping(
    "length" -> longNumber,
    "unit" -> periodUnit
  )(TimePeriod.apply)(TimePeriod.unapply)
  val timeUnit: Mapping[TimeUnit] = stringEitherMapping[TimeUnit, Throwable](d => Try(TimeUnit.valueOf(d)).toEither, _.name(), formatError, _.getMessage :: Nil)
  val duration: Mapping[FiniteDuration] = mapping(
    "length" -> longNumber,
    "unit" -> timeUnit
  )(new FiniteDuration(_, _))(d => Some(d.length -> d.unit))
  val emailAddress: Mapping[EmailAddress] = WrappedMapping(nonEmptyText.verifying(Constraints.emailAddress(), Constraints.maxLength(Values.maxLength.email)), (s: String) => EmailAddress.from(s).get, _.value)
  val url: Mapping[Url] = stringEitherMapping[Url, CustomException](Url.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.url))
  val urlSlides: Mapping[Url.Slides] = stringEitherMapping[Url.Slides, CustomException](Url.Slides.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.url))
  val urlVideo: Mapping[Url.Video] = stringEitherMapping[Url.Video, CustomException](Url.Video.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.url))
  val urlVideos: Mapping[Url.Videos] = stringEitherMapping[Url.Videos, CustomException](Url.Videos.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.url))
  val urlTwitter: Mapping[Url.Twitter] = stringEitherMapping[Url.Twitter, CustomException](Url.Twitter.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.url))
  val urlLinkedIn: Mapping[Url.LinkedIn] = stringEitherMapping[Url.LinkedIn, CustomException](Url.LinkedIn.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.url))
  val urlYouTube: Mapping[Url.YouTube] = stringEitherMapping[Url.YouTube, CustomException](Url.YouTube.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.url))
  val urlMeetup: Mapping[Url.Meetup] = stringEitherMapping[Url.Meetup, CustomException](Url.Meetup.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.url))
  val urlGithub: Mapping[Url.Github] = stringEitherMapping[Url.Github, CustomException](Url.Github.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.url))
  val avatar: Mapping[Avatar] = url.transform(Avatar, _.url)
  val logo: Mapping[Logo] = url.transform(Logo, _.url)
  val banner: Mapping[Banner] = url.transform(Banner, _.url)
  val twitterAccount: Mapping[TwitterAccount] = urlTwitter.transform(TwitterAccount, _.url)
  val twitterHashtag: Mapping[TwitterHashtag] = stringEitherMapping[TwitterHashtag, CustomException](TwitterHashtag.from, _.value, formatError, _.getMessage :: Nil, Constraints.maxLength(Values.maxLength.title))
  val secret: Mapping[Secret] = textMapping(Secret, _.decode)
  val password: Mapping[Secret] = secret.verifying(Constraint[Secret](passwordConstraint) { o =>
    if (o.decode.length >= 8) PlayValid
    else PlayInvalid(ValidationError(passwordError))
  })
  val captcha: Mapping[Secret] = nonEmptyTextMapping(Secret, _.decode)
  val markdown: Mapping[Markdown] = textMapping(Markdown(_), _.value, Constraints.maxLength(Values.maxLength.text))
  val currency: Mapping[Price.Currency] = stringEitherMapping[Price.Currency, String](c => Price.Currency.from(c).toEither(s"No currency '$c'"), _.value, formatError, List(_))
  val price: Mapping[Price] = mapping(
    "amount" -> double,
    "currency" -> currency
  )(Price.apply)(Price.unapply)

  val gMapPlace: Mapping[GMapPlace] = of(new Formatter[GMapPlace] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], GMapPlace] = (
      data.eitherGet(s"$key.id").toValidatedNec,
      data.eitherGet(s"$key.name").toValidatedNec,
      data.get(s"$key.streetNo").validNec[FormError],
      data.get(s"$key.street").validNec[FormError],
      data.get(s"$key.postalCode").validNec[FormError],
      data.get(s"$key.locality").validNec[FormError],
      data.eitherGet(s"$key.country").toValidatedNec,
      data.eitherGet(s"$key.formatted").toValidatedNec,
      data.eitherGet(s"$key.input").toValidatedNec,
      data.eitherGet(s"$key.lat", _.tryDouble, numberError).toValidatedNec,
      data.eitherGet(s"$key.lng", _.tryDouble, numberError).toValidatedNec,
      data.eitherGet(s"$key.url").toValidatedNec,
      data.get(s"$key.website").validNec[FormError],
      data.get(s"$key.phone").validNec[FormError],
      data.eitherGet(s"$key.utcOffset", _.tryInt, numberError).toValidatedNec
      ).mapN(GMapPlace.apply).toEither.left.map(_.toList)

    override def unbind(key: String, value: GMapPlace): Map[String, String] =
      List(
        s"$key.id" -> Some(value.id),
        s"$key.name" -> Some(value.name),
        s"$key.streetNo" -> value.streetNo,
        s"$key.street" -> value.street,
        s"$key.postalCode" -> value.postalCode,
        s"$key.locality" -> value.locality,
        s"$key.country" -> Some(value.country),
        s"$key.formatted" -> Some(value.formatted),
        s"$key.input" -> Some(value.input),
        s"$key.lat" -> Some(value.geo.lat.toString),
        s"$key.lng" -> Some(value.geo.lng.toString),
        s"$key.url" -> Some(value.url),
        s"$key.website" -> value.website,
        s"$key.phone" -> value.phone,
        s"$key.utcOffset" -> Some(value.utcOffset.toString)
      ).collect { case (k, Some(v)) => (k, v) }.toMap
  })

  val socialAccounts: Mapping[SocialAccounts] = mapping(
    "facebook" -> optional(url.transform[FacebookAccount](FacebookAccount, _.url)),
    "instagram" -> optional(url.transform[InstagramAccount](InstagramAccount, _.url)),
    "twitter" -> optional(urlTwitter.transform[TwitterAccount](TwitterAccount, _.url)),
    "linkedIn" -> optional(urlLinkedIn.transform[LinkedInAccount](LinkedInAccount, _.url)),
    "youtube" -> optional(urlYouTube.transform[YoutubeAccount](YoutubeAccount, _.url)),
    "meetup" -> optional(urlMeetup.transform[MeetupAccount](MeetupAccount, _.url)),
    "eventbrite" -> optional(url.transform[EventbriteAccount](EventbriteAccount, _.url)),
    "slack" -> optional(url.transform[SlackAccount](SlackAccount, _.url)),
    "discord" -> optional(url.transform[DiscordAccount](DiscordAccount, _.url)),
    "github" -> optional(urlGithub.transform[GithubAccount](GithubAccount, _.url))
  )(SocialAccounts.apply)(SocialAccounts.unapply)

  private val tag: Mapping[Tag] = WrappedMapping[String, Tag](text(1, Tag.maxSize), s => Tag(s.trim), _.value)
  val tags: Mapping[List[Tag]] = list(tag).verifying(s"Can't add more than ${Tag.maxNumber} tags", _.length <= Tag.maxNumber)

  val userSlug: Mapping[User.Slug] = slugMapping(User.Slug)
  val userStatus: Mapping[User.Status] = stringEitherMapping[User.Status, CustomException](User.Status.from, _.value, formatError, _.getMessage :: Nil)
  val groupSlug: Mapping[Group.Slug] = slugMapping(Group.Slug)
  val groupName: Mapping[Group.Name] = nonEmptyTextMapping(Group.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val eventId: Mapping[Event.Id] = idMapping(Event.Id)
  val eventSlug: Mapping[Event.Slug] = slugMapping(Event.Slug)
  val eventName: Mapping[Event.Name] = nonEmptyTextMapping(Event.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val eventKind: Mapping[Event.Kind] = stringEitherMapping[Event.Kind, CustomException](Event.Kind.from, _.value, formatError, _.getMessage :: Nil, Constraints.nonEmpty)
  val cfpId: Mapping[Cfp.Id] = idMapping(Cfp.Id)
  val cfpSlug: Mapping[Cfp.Slug] = slugMapping(Cfp.Slug)
  val cfpName: Mapping[Cfp.Name] = nonEmptyTextMapping(Cfp.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val talkSlug: Mapping[Talk.Slug] = slugMapping(Talk.Slug)
  val talkTitle: Mapping[Talk.Title] = nonEmptyTextMapping(Talk.Title, _.value, Constraints.maxLength(Values.maxLength.title))
  val proposalStatus: Mapping[Proposal.Status] = stringEitherMapping[Proposal.Status, CustomException](Proposal.Status.from, _.value, formatError, _.getMessage :: Nil, Constraints.nonEmpty)
  val partnerId: Mapping[Partner.Id] = idMapping(Partner.Id)
  val partnerSlug: Mapping[Partner.Slug] = slugMapping(Partner.Slug)
  val partnerName: Mapping[Partner.Name] = nonEmptyTextMapping(Partner.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val contactId: Mapping[Contact.Id] = idMapping(Contact.Id)
  val venueId: Mapping[Venue.Id] = idMapping(Venue.Id)
  val sponsorPackId: Mapping[SponsorPack.Id] = idMapping(SponsorPack.Id)
  val sponsorPackSlug: Mapping[SponsorPack.Slug] = slugMapping(SponsorPack.Slug)
  val sponsorPackName: Mapping[SponsorPack.Name] = nonEmptyTextMapping(SponsorPack.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val contactFirstName: Mapping[Contact.FirstName] = nonEmptyTextMapping(Contact.FirstName, _.value)
  val contactLastName: Mapping[Contact.LastName] = nonEmptyTextMapping(Contact.LastName, _.value)
  val commentId: Mapping[Comment.Id] = idMapping(Comment.Id)
  val meetupGroupSlug: Mapping[MeetupGroup.Slug] = stringEitherMapping[MeetupGroup.Slug, CustomException](MeetupGroup.Slug.from, _.value, formatError, _.getMessage :: Nil, Constraints.nonEmpty)
  val meetupEventId: Mapping[MeetupEvent.Id] = stringEitherMapping[MeetupEvent.Id, CustomException](MeetupEvent.Id.from, _.value.toString, formatError, _.getMessage :: Nil, Constraints.nonEmpty)
  val meetupVenueId: Mapping[MeetupVenue.Id] = stringEitherMapping[MeetupVenue.Id, CustomException](MeetupVenue.Id.from, _.value.toString, formatError, _.getMessage :: Nil, Constraints.nonEmpty)
  val eventRefs: Mapping[Event.ExtRefs] = mapping(
    "meetup" -> optional(mapping(
      "group" -> meetupGroupSlug,
      "event" -> meetupEventId
    )(MeetupEvent.Ref.apply)(MeetupEvent.Ref.unapply))
  )(Event.ExtRefs.apply)(Event.ExtRefs.unapply)
  val venueRefs: Mapping[Venue.ExtRefs] = mapping(
    "meetup" -> optional(mapping(
      "group" -> meetupGroupSlug,
      "venue" -> meetupVenueId
    )(MeetupVenue.Ref.apply)(MeetupVenue.Ref.unapply))
  )(Venue.ExtRefs.apply)(Venue.ExtRefs.unapply)

  def slackToken(key: AesSecretKey): Mapping[SlackToken] = stringEitherMapping[SlackToken, Throwable](SlackToken.from(_, key).toEither, _.decode(key).get, formatError, _.getMessage :: Nil, Constraints.nonEmpty)

  private def liquidFormatter[A]: Formatter[Liquid[A]] = new Formatter[Liquid[A]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Liquid[A]] =
      data.eitherGet(s"$key.kind").left.map(List(_)).flatMap {
        case "Liquid" => data.get(s"$key.value").map(v => Right(Liquid[A](v))).getOrElse(Left(List(FormError(s"$key.value", s"Missing key '$key.value'"))))
        case v => Left(List(FormError(s"$key.kind", s"Invalid value '$v' for key '$key.kind'")))
      }

    override def unbind(key: String, value: Liquid[A]): Map[String, String] = Map(s"$key.kind" -> "Liquid", s"$key.value" -> value.value)
  }

  def liquid[A]: Mapping[Liquid[A]] = of(liquidFormatter)

  private def liquidMarkdownFormatter[A]: Formatter[LiquidMarkdown[A]] = new Formatter[LiquidMarkdown[A]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], LiquidMarkdown[A]] =
      data.eitherGet(s"$key.kind").left.map(List(_)).flatMap {
        case "Liquid" => data.get(s"$key.value").map(v => Right(LiquidMarkdown[A](v))).getOrElse(Left(List(FormError(s"$key.value", s"Missing key '$key.value'"))))
        case v => Left(List(FormError(s"$key.kind", s"Invalid value '$v' for key '$key.kind'")))
      }

    override def unbind(key: String, value: LiquidMarkdown[A]): Map[String, String] = Map(s"$key.kind" -> "Liquid", s"$key.value" -> value.value)
  }

  def liquidMarkdown[A]: Mapping[LiquidMarkdown[A]] = of(liquidMarkdownFormatter)

  val groupSettingsEvent: Mapping[Group.Settings.Action.Trigger] = of(new Formatter[Group.Settings.Action.Trigger] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Group.Settings.Action.Trigger] =
      data.eitherGet(key, v => Group.Settings.Action.Trigger.from(v).asTry(identity), formatError).left.map(List(_))

    override def unbind(key: String, trigger: Group.Settings.Action.Trigger): Map[String, String] = Map(key -> trigger.value)
  })

  val groupSettingsAction: Mapping[Group.Settings.Action] = of(new Formatter[Group.Settings.Action] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Group.Settings.Action] = {
      data.eitherGet(s"$key.kind").left.map(List(_)).flatMap {
        case "Email.Send" => (
          liquidFormatter[Any].bind(s"$key.to", data),
          liquidFormatter[Any].bind(s"$key.subject", data),
          liquidMarkdownFormatter[Any].bind(s"$key.content", data)
          ).mapN(Group.Settings.Action.Email.apply)
        case "Slack.PostMessage" => (
          liquidFormatter[Any].bind(s"$key.channel", data),
          liquidMarkdownFormatter[Any].bind(s"$key.message", data),
          implicitly[Formatter[Boolean]].bind(s"$key.createdChannelIfNotExist", data),
          implicitly[Formatter[Boolean]].bind(s"$key.inviteEverybody", data)
          ).mapN(SlackAction.PostMessage.apply).map(Group.Settings.Action.Slack)
        case v => Left(List(FormError(s"$key.kind", s"action kind '$v' not found")))
      }
    }

    override def unbind(key: String, value: Group.Settings.Action): Map[String, String] = value match {
      case a: Group.Settings.Action.Email =>
        Map(s"$key.kind" -> "Email.Send") ++
          liquidFormatter.unbind(s"$key.to", a.to) ++
          liquidFormatter.unbind(s"$key.subject", a.subject) ++
          liquidMarkdownFormatter.unbind(s"$key.content", a.content)
      case Group.Settings.Action.Slack(p: SlackAction.PostMessage) =>
        Map(s"$key.kind" -> "Slack.PostMessage") ++
          liquidFormatter.unbind(s"$key.channel", p.channel) ++
          liquidMarkdownFormatter.unbind(s"$key.message", p.message) ++
          implicitly[Formatter[Boolean]].unbind(s"$key.createdChannelIfNotExist", p.createdChannelIfNotExist) ++
          implicitly[Formatter[Boolean]].unbind(s"$key.inviteEverybody", p.inviteEverybody)
    }
  })

  private[utils] object Utils {
    def textMapping[A](from: String => A, to: A => String, constraints: Constraint[String]*): Mapping[A] =
      WrappedMapping(text.verifying(constraints: _*), from, to)

    def nonEmptyTextMapping[A](from: String => A, to: A => String, constraints: Constraint[String]*): Mapping[A] =
      WrappedMapping(nonEmptyText.verifying(constraints: _*), from, to)

    def stringEitherMapping[A, E](from: String => Either[E, A], to: A => String, errorMessage: String, errorArgs: E => List[String], constraints: Constraint[String]*): Mapping[A] =
      WrappedMapping(text.verifying(constraints: _*).verifying(format(from, errorMessage, errorArgs)), (s: String) => from(s).get, to)

    def idMapping[A <: IId](builder: UuidIdBuilder[A]): Mapping[A] =
      WrappedMapping(text.verifying(Constraints.nonEmpty()), (s: String) => builder.from(s).get, _.value)

    def slugMapping[A <: ISlug](builder: SlugBuilder[A]): Mapping[A] =
      WrappedMapping(text.verifying(Constraints.nonEmpty(), Constraints.pattern(SlugBuilder.pattern), Constraints.maxLength(SlugBuilder.maxLength)), (s: String) => builder.from(s).get, _.value)

    private def format[E, A](parse: String => Either[E, A], errorMessage: String = formatError, errorArgs: E => List[String] = (_: E) => List()): Constraint[String] =
      Constraint[String](formatConstraint) { o =>
        if (o == null) PlayInvalid(ValidationError(requiredError))
        else parse(o.trim).fold(err => PlayInvalid(ValidationError(errorMessage, errorArgs(err): _*)), _ => PlayValid)
      }
  }

}
