package fr.gospeak.web.partials.form

import fr.gospeak.web.utils.Mappings
import play.api.data.Field
import play.api.i18n.Messages
import play.twirl.api.Html

import scala.util.matching.Regex

object Utils {
  type Args = Seq[(String, String)]
  type Constraint = (String, Seq[Any])

  def getArg(args: Args, key: String): Option[String] =
    args.find { case (k, _) => k == key }.map { case (_, v) => v }

  def getArg(args: Args, key: String, default: => String): String =
    getArg(args, key).getOrElse(default)

  def id(args: Args, field: Field): String =
    getArg(args, "id", field.id)

  def helpId(args: Args, field: Field): String =
    "help-" + id(args, field)

  def attributes(args: Args, ignoring: Seq[String]): Seq[Html] =
    args.collect { case (key, value) if !ignoring.contains(key) => Html(s""" $key="$value"""") }

  def isRequired(field: Field): Option[Constraint] =
    field.constraints.find { case (k, _) => k == Mappings.requiredConstraint }

  def hasPattern(field: Field): Option[Constraint] =
    field.constraints.find { case (k, _) => k == Mappings.patternConstraint }

  def pattern(field: Field): Option[String] =
    hasPattern(field).map(_._2.head).flatMap {
      case r: Regex => Some(r.toString())
      case f: (() => Regex) => Some(f().toString())
      case _ => None
    }

  def format(c: Constraint)(implicit messages: Messages): String =
    c match {
      case (message, args) => messages(message, args: _*)
    }
}
