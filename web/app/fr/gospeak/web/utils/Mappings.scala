package fr.gospeak.web.utils

import fr.gospeak.core.domain.{Event, Talk}
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.data.{FormError, Mapping}

import scala.util.matching.Regex

object Mappings {
  val requiredConstraint = "constraint.required"
  val patternConstraint = "constraint.pattern"

  private def required[T](stringify: T => String): Constraint[T] = Constraint[T](requiredConstraint) { o =>
    if (o == null) Invalid(ValidationError("error.required"))
    else if (stringify(o).trim.isEmpty) Invalid(ValidationError("error.required"))
    else Valid
  }

  private def pattern[T](regex: Regex)(stringify: T => String): Constraint[T] = Constraint[T](patternConstraint, regex) { o =>
    if (o == null) Invalid(ValidationError("error.pattern", regex))
    else regex.unapplySeq(stringify(o)).map(_ => Valid).getOrElse(Invalid(ValidationError("error.pattern", regex)))
  }

  private def stringMapping[Out](from: String => Out, to: Out => String, cs: ((Out => String) => Constraint[Out])*): Mapping[Out] = {
    val formatter = new Formatter[Out] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Out] =
        data.get(key).map(from).toRight(Seq(FormError(key, "error.required", Nil)))

      override def unbind(key: String, value: Out): Map[String, String] =
        Map(key -> to(value))
    }
    of(formatter).verifying(cs.map(_ (to)): _*)
  }

  val talkSlug: Mapping[Talk.Slug] = stringMapping(Talk.Slug, _.value, required, pattern("[a-z0-9-]+".r))
  val talkTitle: Mapping[Talk.Title] = stringMapping(Talk.Title, _.value, required)
  val eventSlug: Mapping[Event.Slug] = stringMapping(Event.Slug, _.value, required, pattern("[a-z0-9-]+".r))
  val eventName: Mapping[Event.Name] = stringMapping(Event.Name, _.value, required)
}
