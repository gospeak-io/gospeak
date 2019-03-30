package fr.gospeak.web.utils

import java.time.{Instant, LocalDateTime, ZoneOffset}

import cats.implicits._
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain._
import fr.gospeak.web.utils.Extensions._
import fr.gospeak.web.utils.Mappings.Utils._
import play.api.data.Forms._
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

  val instant: Mapping[Instant] = stringEitherMapping(s => Try(LocalDateTime.parse(s).toInstant(ZoneOffset.UTC)).toEither, _.atZone(ZoneOffset.UTC).toLocalDateTime.toString, datetimeError) // FIXME manage timezone
  val duration: Mapping[FiniteDuration] = WrappedMapping(longNumber, (l: Long) => Duration.apply(l, MINUTES), _.toMinutes)

  val mail: Mapping[Email] = WrappedMapping(text.verifying(Constraints.emailAddress(), Constraints.maxLength(100)), (s: String) => Email.from(s).right.get, _.value)
  val url: Mapping[Url] = stringEitherMapping(Url.from, _.value)
  val slides: Mapping[Slides] = stringEitherMapping(Slides.from, _.value)
  val video: Mapping[Video] = stringEitherMapping(Video.from, _.value)
  val secret: Mapping[Secret] = stringMapping(Secret, _.decode)
  val markdown: Mapping[Markdown] = stringMapping(Markdown, _.value)
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
        s"$key.lat" -> Some(value.lat.toString),
        s"$key.lng" -> Some(value.lng.toString),
        s"$key.url" -> Some(value.url),
        s"$key.website" -> value.website,
        s"$key.phone" -> value.phone,
        s"$key.utcOffset" -> Some(value.utcOffset.toString)
      ).collect { case (k, Some(v)) => (k, v) }.toMap
  })

  val userSlug: Mapping[User.Slug] = slugMapping(User.Slug)
  val groupSlug: Mapping[Group.Slug] = slugMapping(Group.Slug)
  val groupName: Mapping[Group.Name] = stringMapping(Group.Name, _.value)
  val eventSlug: Mapping[Event.Slug] = slugMapping(Event.Slug)
  val eventName: Mapping[Event.Name] = stringMapping(Event.Name, _.value)
  val cfpSlug: Mapping[Cfp.Slug] = slugMapping(Cfp.Slug)
  val cfpName: Mapping[Cfp.Name] = stringMapping(Cfp.Name, _.value)
  val talkSlug: Mapping[Talk.Slug] = slugMapping(Talk.Slug)
  val talkTitle: Mapping[Talk.Title] = stringMapping(Talk.Title, _.value)

  private[utils] object Utils {
    def stringMapping[A](from: String => A, to: A => String) =
      WrappedMapping(text, from, to)

    def stringEitherMapping[A, E](from: String => Either[E, A], to: A => String, errorMessage: String = formatError): Mapping[A] =
      WrappedMapping(text.verifying(format(from, errorMessage)), (s: String) => from(s).right.get, to)

    def slugMapping[A <: ISlug](builder: SlugBuilder[A]): Mapping[A] =
      WrappedMapping(text.verifying(Constraints.nonEmpty(), Constraints.pattern(SlugBuilder.pattern), Constraints.maxLength(SlugBuilder.maxLength)), (s: String) => builder.from(s).right.get, _.value)

    private def format[E, A](parse: String => Either[E, A], errorMessage: String = formatError): Constraint[String] =
      Constraint[String](formatConstraint) { o =>
        if (o == null) PlayInvalid(ValidationError(errorMessage))
        else if (parse(o.trim).isLeft) PlayInvalid(ValidationError(errorMessage))
        else PlayValid
      }
  }

}
