package fr.gospeak.web.utils

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
    if (o == null) Invalid(ValidationError(requiredError))
    else {
      stringify(o) match {
        case null => Invalid(ValidationError(requiredError))
        case str if str.trim.isEmpty => Invalid(ValidationError(requiredError))
        case _ => Valid
      }
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

  private[utils] def longFormatter[A](from: Long => A, to: A => Long): Formatter[A] = new Formatter[A] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
      data.get(key)
        .map(s => Try(s.toLong).toEither.left.map(t => Seq(FormError(key, numberError, t.getMessage))))
        .map(_.right.map(from))
        .getOrElse(Left(Seq(FormError(key, requiredError))))

    override def unbind(key: String, value: A): Map[String, String] =
      Map(key -> to(value).toString)
  }

  val formatError = "error.format"

  private[utils] def stringTryFormatter[A](from: String => Try[A], to: A => String): Formatter[A] = new Formatter[A] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
      data.get(key)
        .map(from(_).toEither.left.map(t => Seq(FormError(key, formatError, t.getMessage))))
        .getOrElse(Left(Seq(FormError(key, requiredError))))

    override def unbind(key: String, value: A): Map[String, String] =
      Map(key -> to(value))
  }

  private def stringMapping[A](from: String => A, to: A => String, cs: ((A => String) => Constraint[A])*): Mapping[A] =
    of(stringFormatter(from, to)).verifying(cs.map(_ (to)): _*)

  private def stringTryMapping[A](from: String => Try[A], to: A => String, cs: ((A => String) => Constraint[A])*): Mapping[A] =
    of(stringTryFormatter(from, to)).verifying(cs.map(_ (to)): _*)

  private def longMapping[A](from: Long => A, to: A => Long, cs: ((A => Long) => Constraint[A])*): Mapping[A] =
    of(longFormatter(from, to)).verifying(cs.map(_ (to)): _*)

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
