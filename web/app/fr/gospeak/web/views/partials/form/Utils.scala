package fr.gospeak.web.views.partials.form

import fr.gospeak.web.utils.Mappings
import play.api.data.Field
import play.api.i18n.Messages

object Utils {
  type Constraint = (String, Seq[Any])

  def getArg(args: Seq[(String, String)], key: String): Option[String] =
    args.find(_._1 == key).map(_._2)

  def getArg(args: Seq[(String, String)], key: String, default: => String): String =
    getArg(args, key).getOrElse(default)

  def id(args: Seq[(String, String)], field: Field): String =
    getArg(args, "id", field.id)

  def helpId(args: Seq[(String, String)], field: Field): String =
    "help-" + id(args, field)

  def isRequired(field: Field): Option[Constraint] =
    field.constraints.find(_._1 == Mappings.requiredConstraint)

  def hasPattern(field: Field): Option[Constraint] =
    field.constraints.find(_._1 == Mappings.patternConstraint)

  def format(c: Constraint)(implicit messages: Messages): String =
    messages(c._1, c._2: _*)
}
