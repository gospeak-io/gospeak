package fr.gospeak.web.utils

import java.time.{Instant, LocalDateTime, ZoneOffset}

import fr.gospeak.core.domain.{Cfp, Event, Group, Talk}
import fr.gospeak.libs.scalautils.domain.{Email, Markdown, Secret, SlugBuilder}
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.data.{FormError, Mapping}

import scala.concurrent.duration._
import scala.util.Try
import scala.util.matching.Regex

object Mappings {
  val requiredConstraint = "constraint.required"
  val requiredError = "error.required"

  private[utils] def required[A](stringify: A => String): Constraint[A] = Constraint[A](requiredConstraint) { o =>
    val str = stringify(o)
    if (str.trim.isEmpty) {
      Invalid(ValidationError(requiredError))
    } else {
      Valid
    }
  }

  val patternConstraint = "constraint.pattern"
  val patternError = "error.pattern"

  private[utils] def pattern[A](regex: Regex)(stringify: A => String): Constraint[A] = Constraint[A](patternConstraint, regex) { o =>
    if (o == null) Invalid(ValidationError(patternError, regex))
    else regex.unapplySeq(stringify(o)).map(_ => Valid).getOrElse(Invalid(ValidationError(patternError, regex)))
  }

  private[utils] def stringFormatter[A](from: String => A, to: A => String): Formatter[A] = new Formatter[A] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
      data.get(key).map(from).toRight(Seq(FormError(key, requiredError)))

    override def unbind(key: String, value: A): Map[String, String] =
      Map(key -> to(value))
  }

  val numberError = "error.number"

  private[utils] def longFormatter[A](from: Long => A, to: A => Long): Formatter[A] =
    genericFormatter(to(_).toString, s => Try(s.toLong).map(from), numberError)

  val datetimeError = "error.datetime"

  // FIXME: get user ZoneOffset, more generally better managed dates and timezones!!!
  private[utils] val instantFormatter =
    genericFormatter[Instant](_.atZone(ZoneOffset.UTC).toLocalDateTime.toString, s => Try(LocalDateTime.parse(s).toInstant(ZoneOffset.UTC)), datetimeError)

  val formatError = "error.format"

  private[utils] def stringTryFormatter[A](from: String => Try[A], to: A => String): Formatter[A] =
    genericFormatter(to, from, formatError)

  private def genericFormatter[A](serialize: A => String, parse: String => Try[A], err: String): Formatter[A] = new Formatter[A] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
      data.get(key)
        .map(parse(_).toEither.left.map(t => Seq(FormError(key, err, t.getMessage))))
        .getOrElse(Left(Seq(FormError(key, requiredError))))

    override def unbind(key: String, value: A): Map[String, String] =
      Map(key -> serialize(value))
  }

  private def stringMapping[A](from: String => A, to: A => String, cs: ((A => String) => Constraint[A])*): Mapping[A] =
    of(stringFormatter(from, to)).verifying(cs.map(_ (to)): _*)

  private def stringTryMapping[A](from: String => Try[A], to: A => String, cs: ((A => String) => Constraint[A])*): Mapping[A] =
    of(stringTryFormatter(from, to)).verifying(cs.map(_ (to)): _*)

  private def longMapping[A](from: Long => A, to: A => Long, cs: ((A => Long) => Constraint[A])*): Mapping[A] =
    of(longFormatter(from, to)).verifying(cs.map(_ (to)): _*)

  val instant: Mapping[Instant] = of(instantFormatter)
  val duration: Mapping[FiniteDuration] = longMapping(Duration.apply(_, MINUTES), _.toMinutes)

  val markdown: Mapping[Markdown] = stringMapping(Markdown, _.value, required)
  val mail: Mapping[Email] = stringTryMapping(Email.from, _.value, required)
  val secret: Mapping[Secret] = stringMapping(Secret, _.decode, required)

  val groupSlug: Mapping[Group.Slug] = stringTryMapping(Group.Slug.from, _.value, required, pattern(SlugBuilder.pattern))
  val groupName: Mapping[Group.Name] = stringMapping(Group.Name, _.value, required)
  val eventSlug: Mapping[Event.Slug] = stringTryMapping(Event.Slug.from, _.value, required, pattern(SlugBuilder.pattern))
  val eventName: Mapping[Event.Name] = stringMapping(Event.Name, _.value, required)
  val cfpSlug: Mapping[Cfp.Slug] = stringTryMapping(Cfp.Slug.from, _.value, required, pattern(SlugBuilder.pattern))
  val cfpName: Mapping[Cfp.Name] = stringMapping(Cfp.Name, _.value, required)
  val talkSlug: Mapping[Talk.Slug] = stringTryMapping(Talk.Slug.from, _.value, required, pattern(SlugBuilder.pattern))
  val talkTitle: Mapping[Talk.Title] = stringMapping(Talk.Title, _.value, required)
}
