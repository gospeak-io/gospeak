package fr.gospeak.web.pages.partials.form

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import fr.gospeak.web.utils.Mappings
import play.api.data.{Field, FormError}
import play.api.i18n.Messages
import play.twirl.api.Html

import scala.util.matching.Regex

object Utils {
  type Args = Seq[(String, String)]
  type Constraint = (String, Seq[Any])

  case class ArgsWrapper(args: Args) extends AnyVal {
    def replace(key: String, value: String): ArgsWrapper =
      ArgsWrapper(args.filter(_._1 != key) ++ Seq(key -> value))

    def remove(key: String): ArgsWrapper =
      ArgsWrapper(args.filter(_._1 != key))

    def extend(key: String, value: String): ArgsWrapper =
      if (args.exists(_._1 == key)) ArgsWrapper(args.map { case (k, v) => if (k == key) k -> (v + value) else k -> v })
      else ArgsWrapper(args ++ Seq(key -> value))

    def addIfNotExists(key: String, value: String): ArgsWrapper =
      if (args.exists(_._1 == key)) ArgsWrapper(args)
      else ArgsWrapper(args ++ Seq(key -> value))

    def toArgs: Args = args
  }

  val timeUnits: Seq[TimeUnit] = Seq(TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS)

  def typeAttr(value: String): Html =
    attr("type", value)

  def typeAttr(args: Args, default: => String): Html =
    typeAttr(getArg(args, "type", default))

  def idAttr(field: Field, args: Args): Html =
    attr("id", id(field, args))

  def nameAttr(field: Field, args: Args, multi: Boolean = false): Html =
    attr("name", getArg(args, "name", field.name) + (if (multi) "[]" else ""))

  def valueAttr(field: Field, args: Args, default: => String = ""): Html =
    attr("value", value(field, args, default))

  def classAttr(field: Field, args: Args, classes: String): Html =
    attr("class", s"""$classes ${getArg(args, "class", "")}${if (hasErrors(field)) " is-invalid" else ""}""")

  def classAttr(args: Args, classes: String): Html =
    attr("class", s"""$classes ${getArg(args, "class", "")}""")

  def placeholderAttr(args: Args): Html =
    getArg(args, "placeholder").map(attr("placeholder", _)).getOrElse(Html(""))

  def hasErrors(field: Field): Boolean =
    getErrors(field).nonEmpty

  def getErrors(field: Field): Seq[FormError] =
    field.errors ++ field.indexes.flatMap(i => field(s"[$i]").errors)

  def requiredAttr(field: Field, args: Args): Html =
    field.constraints
      .find { case (k, _) => k == Mappings.requiredConstraint }
      .filter(_ => !args.exists(_._1 == "optional"))
      .map(_ => attr("required", "required"))
      .getOrElse(Html(""))

  def patternAttr(field: Field): Html =
    pattern(field).map(attr("pattern", _)).getOrElse(Html(""))

  def autofocusAttr(args: Args): Html =
    getArg(args, "autofocus").map(_ => attr("autofocus", "")).getOrElse(Html(""))

  def helpAttr(args: Args): Html =
    getArg(args, "help").map(v => attr("aria-describedby", v)).getOrElse(Html(""))

  def multipleAttr(): Html =
    Html(attr("multiple", "multiple").body + " " + attr("size", "1").body)

  def otherAttrs(args: Args): Seq[Html] = {
    val ignored = Set("type", "class", "id", "name", "value", "placeholder", "required", "optional", "pattern", "autofocus", "help", "emptyOption")
    args.collect { case (key, value) if !ignored.contains(key) => Html(s""" $key="$value"""") }
  }

  def attr(name: String, value: String): Html = Html(s"""$name="$value"""")

  def id(field: Field, args: Args): String =
    getArg(args, "id", field.id)

  def value(field: Field, args: Args, default: => String = ""): String =
    getArg(args, "value", field.value.getOrElse(default))

  def label(field: Field, args: Args): String =
    getArg(args, "label", field.name)

  def getArg(args: Args, key: String): Option[String] =
    args.find { case (k, _) => k == key }.map { case (_, v) => v }

  def getArg(args: Args, key: String, default: => String): String =
    getArg(args, key).getOrElse(default)

  def helpId(args: Args, field: Field): String =
    "help-" + id(field, args)

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
      case (message, args) => messages(message, args.map {
        case r: Regex => r.toString()
        case f: (() => Regex) => f().toString()
        case arg => arg.toString
      }: _*)
    }
}
