package fr.gospeak.web.utils

import java.time.{Instant, LocalDateTime, ZoneOffset}

import cats.implicits._
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.utils.Extensions._
import fr.gospeak.web.utils.Mappings.Utils._
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Constraints, ValidationError, Invalid => PlayInvalid, Valid => PlayValid}
import play.api.data.{FormError, Mapping, WrappedMapping}

import scala.concurrent.duration._
import scala.util.Try
import scala.util.matching.Regex

object Mappings {
  val requiredConstraint = "constraint.required"
  val requiredError = "error.required"
  val patternConstraint = "constraint.pattern"
  val patternError = "error.pattern"
  val numberError = "error.number"
  val datetimeError = "error.datetime"
  val formatError = "error.format"

  lazy val instant: Mapping[Instant] = of(instantFormatter) // lazy is needed because Utils object is created after which leads to NullPointerException :(
  val duration: Mapping[FiniteDuration] = longMapping(Duration.apply(_, MINUTES), _.toMinutes)

  val mail: Mapping[Email] = WrappedMapping(text.verifying(Constraints.emailAddress(), Constraints.maxLength(100)), (s: String) => Email.from(s).right.get, _.value)
  val url: Mapping[Url] = stringEitherMapping(Url.from, _.value)
  val slides: Mapping[Slides] = stringEitherMapping(Slides.from, _.value)
  val video: Mapping[Video] = stringEitherMapping(Video.from, _.value)
  val secret: Mapping[Secret] = stringMapping(Secret, _.decode, required)
  val markdown: Mapping[Markdown] = stringMapping(Markdown, _.value, required)
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

  val userSlug: Mapping[User.Slug] = WrappedMapping(text.verifying(Constraints.nonEmpty(), Constraints.pattern(SlugBuilder.pattern), Constraints.maxLength(30)), (s: String) => User.Slug.from(s).right.get, _.value)
  val groupSlug: Mapping[Group.Slug] = stringEitherMapping(Group.Slug.from, _.value, required, pattern(SlugBuilder.pattern))
  val groupName: Mapping[Group.Name] = stringMapping(Group.Name, _.value, required)
  val eventSlug: Mapping[Event.Slug] = stringEitherMapping(Event.Slug.from, _.value, required, pattern(SlugBuilder.pattern))
  val eventName: Mapping[Event.Name] = stringMapping(Event.Name, _.value, required)
  val cfpSlug: Mapping[Cfp.Slug] = stringEitherMapping(Cfp.Slug.from, _.value, required, pattern(SlugBuilder.pattern))
  val cfpName: Mapping[Cfp.Name] = stringMapping(Cfp.Name, _.value, required)
  val talkSlug: Mapping[Talk.Slug] = stringEitherMapping(Talk.Slug.from, _.value, required, pattern(SlugBuilder.pattern))
  val talkTitle: Mapping[Talk.Title] = stringMapping(Talk.Title, _.value, required)

  private[utils] object Utils {
    def required[A](stringify: A => String): Constraint[A] = Constraint[A](requiredConstraint) { o =>
      val str = stringify(o)
      if (str.trim.isEmpty) {
        PlayInvalid(ValidationError(requiredError))
      } else {
        PlayValid
      }
    }

    def pattern[A](regex: Regex)(stringify: A => String): Constraint[A] = Constraint[A](patternConstraint, regex) { o =>
      if (o == null) PlayInvalid(ValidationError(patternError, regex))
      else regex.unapplySeq(stringify(o)).map(_ => PlayValid).getOrElse(PlayInvalid(ValidationError(patternError, regex)))
    }

    def stringFormatter[A](from: String => A, to: A => String): Formatter[A] = new Formatter[A] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
        data.get(key).map(from).toRight(Seq(FormError(key, requiredError)))

      override def unbind(key: String, value: A): Map[String, String] =
        Map(key -> to(value))
    }

    def longFormatter[A](from: Long => A, to: A => Long): Formatter[A] =
      genericFormatter(to(_).toString, _.tryLong.map(from), numberError)

    // FIXME: get user ZoneOffset, more generally better managed dates and timezones!!!
    val instantFormatter: Formatter[Instant] =
      genericFormatter[Instant](_.atZone(ZoneOffset.UTC).toLocalDateTime.toString, s => Try(LocalDateTime.parse(s).toInstant(ZoneOffset.UTC)), datetimeError)

    def stringTryFormatter[A](from: String => Try[A], to: A => String): Formatter[A] =
      genericFormatter(to, from, formatError)

    def stringEitherFormatter[A, E](from: String => Either[E, A], to: A => String)(implicit ev: E <:< Throwable): Formatter[A] =
      genericFormatter(to, from(_).toTry, formatError)

    private def genericFormatter[A](serialize: A => String, parse: String => Try[A], err: String): Formatter[A] = new Formatter[A] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
        data.get(key)
          .map(parse(_).toEither.left.map(t => Seq(FormError(key, err, t.getMessage))))
          .getOrElse(Left(Seq(FormError(key, requiredError))))

      override def unbind(key: String, value: A): Map[String, String] =
        Map(key -> serialize(value))
    }

    def stringMapping[A](from: String => A, to: A => String, cs: ((A => String) => Constraint[A])*): Mapping[A] =
      of(stringFormatter(from, to)).verifying(cs.map(_ (to)): _*)

    def stringTryMapping[A](from: String => Try[A], to: A => String, cs: ((A => String) => Constraint[A])*): Mapping[A] =
      of(stringTryFormatter(from, to)).verifying(cs.map(_ (to)): _*)

    def stringEitherMapping[A, E](from: String => Either[E, A], to: A => String, cs: ((A => String) => Constraint[A])*)(implicit ev: E <:< Throwable): Mapping[A] =
      of(stringEitherFormatter(from, to)).verifying(cs.map(_ (to)): _*)

    def longMapping[A](from: Long => A, to: A => Long, cs: ((A => Long) => Constraint[A])*): Mapping[A] =
      of(longFormatter(from, to)).verifying(cs.map(_ (to)): _*)
  }

}
