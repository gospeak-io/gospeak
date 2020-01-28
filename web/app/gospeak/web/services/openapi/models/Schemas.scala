package fr.gospeak.web.services.openapi.models

import cats.data.NonEmptyList
import gospeak.libs.scala.Extensions._
import fr.gospeak.web.services.openapi.error.OpenApiError

final case class Schemas(value: Map[String, Schema]) extends AnyVal {
  def get(name: String): Option[Schema] = value.get(name)

  def resolve(schema: Schema): Either[NonEmptyList[OpenApiError], Option[Schema]] = schema match {
    case s: Schema.StringVal => Right(Some(s))
    case s: Schema.IntegerVal => Right(Some(s))
    case s: Schema.NumberVal => Right(Some(s))
    case s: Schema.BooleanVal => Right(Some(s))
    case s: Schema.ArrayVal => resolve(s.items).map(_.map(r => s.copy(items = r))).left.map(_.map(_.atPath(s".items")))
    case s: Schema.ObjectVal =>
      s.properties.map { case (name, value) =>
        resolve(value).map(res => (name, res)).left.map(_.map(_.atPath(".properties", s".$name")))
      }.sequence.map(p => p.map { case (k, v) => v.map(k -> _) }.sequence).map(_.map(p => s.copy(properties = p.toMap)))
    case s: Schema.ReferenceVal => s.$ref.localRef match {
      case Some(("schemas", name)) => get(name).toRight(NonEmptyList.of(OpenApiError.missingReference(s.$ref.value).atPath(".$ref"))).flatMap(resolve)
      case Some((component, name)) => Left(NonEmptyList.of(OpenApiError.badReference(s.$ref.value, component, "schemas")))
      case None => Right(None)
    }
    case s: Schema.CombinationVal => s.oneOf.map(_.zipWithIndex.map { case (s, i) =>
      resolve(s).left.map(_.map(_.atPath(".oneOf", s"[$i]")))
    }.sequence).sequence.map(_.flatMap(_.sequence.map(a => Schema.CombinationVal(Some(a)))))
  }

  def contains(name: String): Boolean = value.contains(name)
}

object Schemas {
  def apply(values: (String, Schema)*): Schemas = new Schemas(values.toMap)
}
