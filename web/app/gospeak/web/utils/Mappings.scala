package fr.gospeak.web.utils

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
import fr.gospeak.web.utils.Extensions._
import fr.gospeak.web.utils.Mappings.Utils._
import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}
import gospeak.libs.scala.domain._
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
  val instant: Mapping[Instant] = stringEitherMapping(s => Try(LocalDateTime.parse(s)).map(_.toInstant(Constants.defaultZoneId)).toEither, _.atZone(Constants.defaultZoneId).toLocalDateTime.toString, datetimeError)
  val myLocalDateTime: Mapping[LocalDateTime] = mapping(
    "date" -> localDate("dd/MM/yyyy"),
    "time" -> localTime("HH:mm")
  )({ case (d, t) => LocalDateTime.of(d, t) })(dt => Some(dt.toLocalDate -> dt.toLocalTime))
  val chronoUnit: Mapping[ChronoUnit] = stringEitherMapping(d => Try(ChronoUnit.valueOf(d)).toEither, _.name(), formatError)
  val periodUnit: Mapping[TimePeriod.PeriodUnit] = stringEitherMapping(d => TimePeriod.PeriodUnit.all.find(_.value == d).toEither, _.value, formatError)
  val period: Mapping[TimePeriod] = mapping(
    "length" -> longNumber,
    "unit" -> periodUnit
  )(TimePeriod.apply)(TimePeriod.unapply)
  val timeUnit: Mapping[TimeUnit] = stringEitherMapping(d => Try(TimeUnit.valueOf(d)).toEither, _.name(), formatError)
  val duration: Mapping[FiniteDuration] = mapping(
    "length" -> longNumber,
    "unit" -> timeUnit
  )(new FiniteDuration(_, _))(d => Some(d.length -> d.unit))
  val emailAddress: Mapping[EmailAddress] = WrappedMapping(nonEmptyText.verifying(Constraints.emailAddress(), Constraints.maxLength(Values.maxLength.email)), (s: String) => EmailAddress.from(s).get, _.value)
  val url: Mapping[Url] = stringEitherMapping(Url.from, _.value, formatError, Constraints.maxLength(Values.maxLength.url))
  val avatar: Mapping[Avatar] = url.transform(Avatar, _.url)
  val logo: Mapping[Logo] = url.transform(Logo, _.url)
  val banner: Mapping[Banner] = url.transform(Banner, _.url)
  val slides: Mapping[Slides] = url.transform(Slides.from(_).get, _.url)
  val video: Mapping[Video] = url.transform(Video.from(_).get, _.url)
  val twitterAccount: Mapping[TwitterAccount] = url.transform(TwitterAccount, _.url)
  val twitterHashtag: Mapping[TwitterHashtag] = stringEitherMapping(TwitterHashtag.from, _.value, formatError, Constraints.maxLength(Values.maxLength.title))
  val secret: Mapping[Secret] = textMapping(Secret, _.decode)
  val password: Mapping[Secret] = secret.verifying(Constraint[Secret](passwordConstraint) { o =>
    if (o.decode.length >= 8) PlayValid
    else PlayInvalid(ValidationError(passwordError))
  })
  val markdown: Mapping[Markdown] = textMapping(Markdown, _.value, Constraints.maxLength(Values.maxLength.text))
  val currency: Mapping[Price.Currency] = stringEitherMapping(Price.Currency.from(_).toEither, _.value, formatError)
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
      data.eitherGetAndParse(s"$key.lat", _.tryDouble, numberError).toValidatedNec,
      data.eitherGetAndParse(s"$key.lng", _.tryDouble, numberError).toValidatedNec,
      data.eitherGet(s"$key.url").toValidatedNec,
      data.get(s"$key.website").validNec[FormError],
      data.get(s"$key.phone").validNec[FormError],
      data.eitherGetAndParse(s"$key.utcOffset", _.tryInt, numberError).toValidatedNec
      ).mapN(GMapPlace.apply).toEither.left.map(_.toList)

    override def unbind(key: String, value: GMapPlace): Map[String, String] =
      Seq(
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
    "twitter" -> optional(url.transform[TwitterAccount](TwitterAccount, _.url)),
    "linkedIn" -> optional(url.transform[LinkedInAccount](LinkedInAccount, _.url)),
    "youtube" -> optional(url.transform[YoutubeAccount](YoutubeAccount, _.url)),
    "meetup" -> optional(url.transform[MeetupAccount](MeetupAccount, _.url)),
    "eventbrite" -> optional(url.transform[EventbriteAccount](EventbriteAccount, _.url)),
    "slack" -> optional(url.transform[SlackAccount](SlackAccount, _.url)),
    "discord" -> optional(url.transform[DiscordAccount](DiscordAccount, _.url)),
    "github" -> optional(url.transform[GithubAccount](GithubAccount, _.url))
  )(SocialAccounts.apply)(SocialAccounts.unapply)

  private val tag: Mapping[Tag] = WrappedMapping[String, Tag](text(1, Tag.maxSize), s => Tag(s.trim), _.value)
  val tags: Mapping[Seq[Tag]] = seq(tag).verifying(s"Can't add more than ${Tag.maxNumber} tags", _.length <= Tag.maxNumber)

  val userSlug: Mapping[User.Slug] = slugMapping(User.Slug)
  val userStatus: Mapping[User.Status] = stringEitherMapping(User.Status.from, _.value, formatError)
  val groupSlug: Mapping[Group.Slug] = slugMapping(Group.Slug)
  val groupName: Mapping[Group.Name] = nonEmptyTextMapping(Group.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val eventId: Mapping[Event.Id] = idMapping(Event.Id)
  val eventSlug: Mapping[Event.Slug] = slugMapping(Event.Slug)
  val eventName: Mapping[Event.Name] = nonEmptyTextMapping(Event.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val cfpId: Mapping[Cfp.Id] = idMapping(Cfp.Id)
  val cfpSlug: Mapping[Cfp.Slug] = slugMapping(Cfp.Slug)
  val cfpName: Mapping[Cfp.Name] = nonEmptyTextMapping(Cfp.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val talkSlug: Mapping[Talk.Slug] = slugMapping(Talk.Slug)
  val talkTitle: Mapping[Talk.Title] = nonEmptyTextMapping(Talk.Title, _.value, Constraints.maxLength(Values.maxLength.title))
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
  val externalCfpName: Mapping[ExternalCfp.Name] = nonEmptyTextMapping(ExternalCfp.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val meetupGroupSlug: Mapping[MeetupGroup.Slug] = stringEitherMapping(MeetupGroup.Slug.from, _.value, formatError, Constraints.nonEmpty)
  val meetupEventId: Mapping[MeetupEvent.Id] = stringEitherMapping(MeetupEvent.Id.from, _.value.toString, formatError, Constraints.nonEmpty)
  val meetupVenueId: Mapping[MeetupVenue.Id] = stringEitherMapping(MeetupVenue.Id.from, _.value.toString, formatError, Constraints.nonEmpty)
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

  def slackToken(key: AesSecretKey): Mapping[SlackToken] = stringEitherMapping(SlackToken.from(_, key).toEither, _.decode(key).get, formatError, Constraints.nonEmpty)

  private def templateFormatter[A]: Formatter[MustacheMarkdownTmpl[A]] = new Formatter[MustacheMarkdownTmpl[A]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], MustacheMarkdownTmpl[A]] =
      data.eitherGet(s"$key.kind").left.map(Seq(_)).flatMap {
        case "Mustache" => data.get(s"$key.value").map(v => Right(MustacheMarkdownTmpl[A](v))).getOrElse(Left(Seq(FormError(s"$key.value", s"Missing key '$key.value'"))))
        case v => Left(Seq(FormError(s"$key.kind", s"Invalid value '$v' for key '$key.kind'")))
      }

    override def unbind(key: String, value: MustacheMarkdownTmpl[A]): Map[String, String] = value match {
      case MustacheMarkdownTmpl(v) => Map(s"$key.kind" -> "Mustache", s"$key.value" -> v)
    }
  }

  def template[A]: Mapping[MustacheMarkdownTmpl[A]] = of(templateFormatter)


  private def templateTextFormatter[A]: Formatter[MustacheTextTmpl[A]] = new Formatter[MustacheTextTmpl[A]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], MustacheTextTmpl[A]] =
      data.eitherGet(s"$key.kind").left.map(Seq(_)).flatMap {
        case "Mustache" => data.get(s"$key.value").map(v => Right(MustacheTextTmpl[A](v))).getOrElse(Left(Seq(FormError(s"$key.value", s"Missing key '$key.value'"))))
        case v => Left(Seq(FormError(s"$key.kind", s"Invalid value '$v' for key '$key.kind'")))
      }

    override def unbind(key: String, value: MustacheTextTmpl[A]): Map[String, String] = value match {
      case MustacheTextTmpl(v) => Map(s"$key.kind" -> "Mustache", s"$key.value" -> v)
    }
  }

  def templateText[A]: Mapping[MustacheTextTmpl[A]] = of(templateTextFormatter)

  val groupSettingsEvent: Mapping[Group.Settings.Action.Trigger] = of(new Formatter[Group.Settings.Action.Trigger] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Group.Settings.Action.Trigger] =
      data.eitherGetAndParse(key, v => Group.Settings.Action.Trigger.from(v).asTry(identity), formatError).left.map(Seq(_))

    override def unbind(key: String, trigger: Group.Settings.Action.Trigger): Map[String, String] = Map(key -> trigger.value)
  })

  val groupSettingsAction: Mapping[Group.Settings.Action] = of(new Formatter[Group.Settings.Action] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Group.Settings.Action] = {
      data.eitherGet(s"$key.kind").left.map(Seq(_)).flatMap {
        case "Email.Send" => (
          templateTextFormatter.bind(s"$key.to", data),
          templateTextFormatter.bind(s"$key.subject", data),
          templateFormatter.bind(s"$key.content", data)
          ).mapN(Group.Settings.Action.Email.apply)
        case "Slack.PostMessage" => (
          templateFormatter.bind(s"$key.channel", data),
          templateFormatter.bind(s"$key.message", data),
          implicitly[Formatter[Boolean]].bind(s"$key.createdChannelIfNotExist", data),
          implicitly[Formatter[Boolean]].bind(s"$key.inviteEverybody", data)
          ).mapN(SlackAction.PostMessage.apply).map(Group.Settings.Action.Slack)
        case v => Left(Seq(FormError(s"$key.kind", s"action kind '$v' not found")))
      }
    }

    override def unbind(key: String, value: Group.Settings.Action): Map[String, String] = value match {
      case a: Group.Settings.Action.Email =>
        Map(s"$key.kind" -> "Email.Send") ++
          templateTextFormatter.unbind(s"$key.to", a.to) ++
          templateTextFormatter.unbind(s"$key.subject", a.subject) ++
          templateFormatter.unbind(s"$key.content", a.content)
      case Group.Settings.Action.Slack(p: SlackAction.PostMessage) =>
        Map(s"$key.kind" -> "Slack.PostMessage") ++
          templateFormatter.unbind(s"$key.channel", p.channel) ++
          templateFormatter.unbind(s"$key.message", p.message) ++
          implicitly[Formatter[Boolean]].unbind(s"$key.createdChannelIfNotExist", p.createdChannelIfNotExist) ++
          implicitly[Formatter[Boolean]].unbind(s"$key.inviteEverybody", p.inviteEverybody)
    }
  })

  private[utils] object Utils {
    def textMapping[A](from: String => A, to: A => String, constraints: Constraint[String]*): Mapping[A] =
      WrappedMapping(text.verifying(constraints: _*), from, to)

    def nonEmptyTextMapping[A](from: String => A, to: A => String, constraints: Constraint[String]*): Mapping[A] =
      WrappedMapping(nonEmptyText.verifying(constraints: _*), from, to)

    def stringEitherMapping[A, E](from: String => Either[E, A], to: A => String, errorMessage: String, constraints: Constraint[String]*): Mapping[A] =
      WrappedMapping(text.verifying(constraints: _*).verifying(format(from, errorMessage)), (s: String) => from(s).get, to)

    def idMapping[A <: IId](builder: UuidIdBuilder[A]): Mapping[A] =
      WrappedMapping(text.verifying(Constraints.nonEmpty()), (s: String) => builder.from(s).get, _.value)

    def slugMapping[A <: ISlug](builder: SlugBuilder[A]): Mapping[A] =
      WrappedMapping(text.verifying(Constraints.nonEmpty(), Constraints.pattern(SlugBuilder.pattern), Constraints.maxLength(SlugBuilder.maxLength)), (s: String) => builder.from(s).get, _.value)

    private def format[E, A](parse: String => Either[E, A], errorMessage: String = formatError): Constraint[String] =
      Constraint[String](formatConstraint) { o =>
        if (o == null) PlayInvalid(ValidationError(errorMessage))
        else if (parse(o.trim).isLeft) PlayInvalid(ValidationError(errorMessage))
        else PlayValid
      }
  }

}
