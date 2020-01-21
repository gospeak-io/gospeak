package fr.gospeak.web.utils

import fr.gospeak.libs.scalautils.domain.CustomException
import play.api.libs.json._

object JsonUtils {

  implicit class JsonFormatExtension[A](val in: Format[A]) extends AnyVal {
    /**
     * Create a `Format[B]` mapping values from a `Format[A]`
     */
    def imap[B](f: A => B)(g: B => A): Format[B] =
      Format(in.map(f), in.contramap(g))

    /**
     * Create a `Format[B]` mapping values from a `Format[A]` but allowing the read part to fail
     */
    def validate[B](f: A => Either[(JsPath, Seq[JsonValidationError]), B])(g: B => A): Format[B] =
      Format(js => in.reads(js).flatMap(a => f(a).fold(e => JsError(Seq(e)), b => JsSuccess(b))), in.contramap(g))

    def validate2[B](f: A => Either[CustomException, B])(g: B => A): Format[B] =
      validate(a => f(a).left.map(toJson))(g)

    private def toJson(ex: CustomException): (JsPath, Seq[JsonValidationError]) =
      (JsPath, JsonValidationError(ex.message) +: ex.errors.map(e => JsonValidationError(e.value)))
  }

  def diff(json1: JsValue, json2: JsValue, path: JsPath = JsPath()): List[(JsPath, Option[JsValue], Option[JsValue])] = {
    ((json1, json2) match {
      case (JsNull, JsNull) => List()
      case (JsBoolean(b1), JsBoolean(b2)) => if (b1 == b2) List() else List((path, Some(JsBoolean(b1)), Some(JsBoolean(b2))))
      case (JsNumber(n1), JsNumber(n2)) => if (n1 == n2) List() else List((path, Some(JsNumber(n1)), Some(JsNumber(n2))))
      case (JsString(s1), JsString(s2)) => if (s1 == s2) List() else List((path, Some(JsString(s1)), Some(JsString(s2))))
      case (JsArray(a1), JsArray(a2)) =>
        a1.zip(a2).zipWithIndex.flatMap { case ((e1, e2), i) => diff(e1, e2, path \ i) }.toList ++
          a1.drop(a2.length).zipWithIndex.map { case (e1, i) => (path \ (a2.length + i), Some(e1), None) }.toList ++
          a2.drop(a1.length).zipWithIndex.map { case (e2, i) => (path \ (a1.length + i), None, Some(e2)) }.toList
      case (JsObject(o1), JsObject(o2)) =>
        o1.flatMap { case (k, v1) => o2.get(k).map(v2 => diff(v1, v2, path \ k)).getOrElse(Seq((path \ k, Some(v1), None))) }.toList ++
          o2.filterKeys(!o1.contains(_)).map { case (k, v2) => (path \ k, None, Some(v2)) }.toList
      case (_, _) => List((path, Some(json1), Some(json2)))
    }).sortBy(_._1.toJsonString)
  }

}
