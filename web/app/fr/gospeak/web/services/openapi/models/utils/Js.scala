package fr.gospeak.web.services.openapi.models.utils

import fr.gospeak.web.services.openapi.models.{Schema, Schemas}

/**
 * A Json value, useful to store any value which can't be properly typed
 *
 * @param value the Json value
 * @param clazz the class of the Json
 */
final case class Js(value: String, clazz: String) {
  def isString: Boolean = clazz == "JsString"

  def isNumber: Boolean = clazz == "JsNumber"

  def isBoolean: Boolean = clazz == "JsTrue$" || clazz == "JsFalse$"

  def isArray: Boolean = clazz == "JsArray"

  def isObject: Boolean = clazz == "JsObject"

  def matchSchema(schemas: Schemas, res: Schema): Boolean = {
    res match {
      case _: Schema.StringVal => isString
      case _: Schema.IntegerVal => isNumber
      case _: Schema.NumberVal => isNumber
      case _: Schema.BooleanVal => isBoolean
      case _: Schema.ArrayVal => isArray
      case _: Schema.ObjectVal => isObject
      case _: Schema.ReferenceVal => true // should not happen as it's resolved
      case s: Schema.CombinationVal => s.oneOf.forall(_.exists(matchSchema(schemas, _)))
    }
  }
}

object Js {
  def apply(value: String): Js = new Js("\"" + value + "\"", "JsString")

  def apply(value: Int): Js = new Js(value.toString, "JsNumber")

  def apply(value: Long): Js = new Js(value.toString, "JsNumber")

  def apply(value: Double): Js = new Js(value.toString, "JsNumber")

  def apply(value: Boolean): Js = if (value) new Js(value.toString, "JsTrue$") else new Js(value.toString, "JsFalse$")
}
