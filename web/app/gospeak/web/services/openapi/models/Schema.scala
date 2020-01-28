package gospeak.web.services.openapi.models

import gospeak.web.services.openapi.OpenApiUtils
import gospeak.web.services.openapi.error.OpenApiError
import gospeak.web.services.openapi.models.utils.{HasValidation, Js, Markdown}

// FIXME miss a lot a features here :(
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#schema-object"
 * @see "https://spec.openapis.org/oas/v3.0.2#data-types"
 */
sealed trait Schema extends HasValidation with Product with Serializable {
  val hint: String

  def flatten: List[Schema]
}

object Schema {
  val hintAttr = "type"

  final case class StringVal(format: Option[String], // ex: date, date-time, password, byte, binary
                             enum: Option[List[String]],
                             example: Option[String],
                             default: Option[String],
                             description: Option[Markdown]) extends Schema {
    override val hint: String = StringVal.hint

    override def flatten: List[Schema] = List(this)

    override def getErrors(s: Schemas): List[OpenApiError] = List()
  }

  object StringVal {
    val hint = "string"
  }

  final case class IntegerVal(format: Option[String], // ex: int32, int64
                              enum: Option[List[Long]],
                              example: Option[Long],
                              default: Option[Long],
                              description: Option[Markdown],
                              minimum: Option[Int]) extends Schema {
    override val hint: String = IntegerVal.hint

    override def flatten: List[Schema] = List(this)

    override def getErrors(s: Schemas): List[OpenApiError] = List()
  }

  object IntegerVal {
    val hint = "integer"
  }

  final case class NumberVal(format: Option[String], // ex: float, double
                             enum: Option[List[Double]],
                             example: Option[Double],
                             default: Option[Double],
                             description: Option[Markdown]) extends Schema {
    override val hint: String = NumberVal.hint

    override def flatten: List[Schema] = List(this)

    override def getErrors(s: Schemas): List[OpenApiError] = List()
  }

  object NumberVal {
    val hint = "number"
  }

  final case class BooleanVal(example: Option[Boolean],
                              default: Option[Boolean],
                              description: Option[Markdown]) extends Schema {
    override val hint: String = BooleanVal.hint

    override def flatten: List[Schema] = List(this)

    override def getErrors(s: Schemas): List[OpenApiError] = List()
  }

  object BooleanVal {
    val hint = "boolean"
  }

  final case class ArrayVal(items: Schema,
                            example: Option[List[Js]],
                            description: Option[Markdown]) extends Schema {
    override val hint: String = ArrayVal.hint

    override def flatten: List[Schema] = this :: items.flatten

    override def getErrors(s: Schemas): List[OpenApiError] = {
      s.resolve(items) match {
        case Left(errs) => errs.map(_.atPath(".items")).toList
        case Right(Some(res)) =>
          example.getOrElse(List()).zipWithIndex
            .filterNot(_._1.matchSchema(s, res))
            .map { case (js, i) => OpenApiError.badExampleFormat(js.value, items.hint).atPath(".example", s"[$i]") }
        case Right(None) => List()
      }
    }
  }

  object ArrayVal {
    val hint = "array"
  }

  final case class ObjectVal(properties: Map[String, Schema],
                             description: Option[Markdown],
                             required: Option[List[String]]) extends Schema {
    override val hint: String = ObjectVal.hint

    override def flatten: List[Schema] = this :: properties.values.toList.flatMap(_.flatten)

    override def getErrors(s: Schemas): List[OpenApiError] = {
      val invalidSchemas = OpenApiUtils.validate("properties", properties, s)
      val missingProperties = required.getOrElse(List()).zipWithIndex
        .filterNot { case (name, _) => properties.contains(name) }
        .map { case (name, i) => OpenApiError.missingProperty(name).atPath(".required", s"[$i]") }
      val duplicateRequired = OpenApiUtils.noDuplicates[String]("required", required.getOrElse(List()), identity)
      invalidSchemas ++ missingProperties ++ duplicateRequired
    }
  }

  object ObjectVal {
    val hint = "object"
  }

  final case class ReferenceVal($ref: Reference) extends Schema {
    override val hint: String = ReferenceVal.hint

    override def flatten: List[Schema] = List(this)

    override def getErrors(s: Schemas): List[OpenApiError] = {
      $ref.localRef match {
        case Some(("schemas", name)) => s.get(name) match {
          case Some(res) => s.resolve(res).swap.toOption.map(_.toList).getOrElse(List())
          case None => List(OpenApiError.missingReference($ref.value))
        }
        case Some((component, _)) => List(OpenApiError.badReference($ref.value, component, "schemas"))
        case None => List()
      }
    }
  }

  object ReferenceVal {
    val hint = "reference"
    val hintAttr = "$ref"
  }

  final case class CombinationVal(oneOf: Option[List[Schema]]) extends Schema {
    override val hint: String = CombinationVal.hint

    override def flatten: List[Schema] = this :: oneOf.getOrElse(List())

    override def getErrors(s: Schemas): List[OpenApiError] = {
      val oneOfErrors = OpenApiUtils.validate("oneOf", oneOf.getOrElse(List()), s)
      oneOfErrors
    }
  }

  object CombinationVal {
    val hint = "combination"
  }

}
