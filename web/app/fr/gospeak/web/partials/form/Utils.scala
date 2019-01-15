package fr.gospeak.web.partials.form

import fr.gospeak.web.utils.Mappings
import play.api.data.Field
import play.api.i18n.Messages
import play.twirl.api.Html

object Utils {
  type Args = Seq[(String, String)]
  type Constraint = (String, Seq[Any])

  def getArg(args: Args, key: String): Option[String] =
    args.find(_._1 == key).map(_._2)

  def getArg(args: Args, key: String, default: => String): String =
    getArg(args, key).getOrElse(default)

  def id(args: Args, field: Field): String =
    getArg(args, "id", field.id)

  def helpId(args: Args, field: Field): String =
    "help-" + id(args, field)

  def attributes(args: Args, ignoring: Seq[String]): Seq[Html] =
    args.collect { case (key, value) if !ignoring.contains(key) => Html(s""" $key="$value"""") }

  def isRequired(field: Field): Option[Constraint] =
    field.constraints.find(_._1 == Mappings.requiredConstraint)

  def hasPattern(field: Field): Option[Constraint] =
    field.constraints.find(_._1 == Mappings.patternConstraint)

  def format(c: Constraint)(implicit messages: Messages): String =
    messages(c._1, c._2: _*)
}
