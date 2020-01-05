package fr.gospeak.web.services.openapi.models.utils

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
}

object Js {
  def apply(value: String): Js = new Js("\"" + value + "\"", "JsString")

  def apply(value: Int): Js = new Js(value.toString, "JsNumber")

  def apply(value: Long): Js = new Js(value.toString, "JsNumber")

  def apply(value: Double): Js = new Js(value.toString, "JsNumber")

  def apply(value: Boolean): Js = if (value) new Js(value.toString, "JsTrue$") else new Js(value.toString, "JsFalse$")
}
