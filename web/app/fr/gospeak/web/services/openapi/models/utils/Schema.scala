package fr.gospeak.web.services.openapi.models.utils

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

// FIXME miss a lot a features here :(
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#schema-object"
 * @see "https://spec.openapis.org/oas/v3.0.2#data-types"
 */
sealed trait Schema extends Product with Serializable {
  val hint: String

  def flatten: List[Schema]
}

object Schema {
  val hintAttr = "type"

  final case class StringVal(format: Option[String], // ex: date, date-time, password, byte, binary
                             example: Option[String],
                             default: Option[String],
                             description: Option[Markdown]) extends Schema {
    override val hint: String = StringVal.hint

    override def flatten: List[Schema] = List(this)
  }

  object StringVal {
    val hint = "string"
  }

  final case class IntegerVal(format: Option[String], // ex: int32, int64
                              example: Option[Long],
                              default: Option[Long],
                              description: Option[Markdown],
                              minimum: Option[Int]) extends Schema {
    override val hint: String = IntegerVal.hint

    override def flatten: List[Schema] = List(this)
  }

  object IntegerVal {
    val hint = "integer"
  }

  final case class NumberVal(format: Option[String], // ex: float, double
                             example: Option[Double],
                             default: Option[Double],
                             description: Option[Markdown]) extends Schema {
    override val hint: String = NumberVal.hint

    override def flatten: List[Schema] = List(this)
  }

  object NumberVal {
    val hint = "number"
  }

  final case class BooleanVal(example: Option[Boolean],
                              default: Option[Boolean],
                              description: Option[Markdown]) extends Schema {
    override val hint: String = BooleanVal.hint

    override def flatten: List[Schema] = List(this)
  }

  object BooleanVal {
    val hint = "boolean"
  }

  final case class ArrayVal(items: Schema,
                            example: Option[List[Js]],
                            description: Option[Markdown]) extends Schema {
    override val hint: String = ArrayVal.hint

    override def flatten: List[Schema] = this :: items.flatten

    def hasErrors: Option[NonEmptyList[ErrorMessage]] = {
      val badTypeExamples = (items match {
        case _: StringVal => example.getOrElse(List()).filterNot(_.isString)
        case _: IntegerVal => example.getOrElse(List()).filterNot(_.isNumber)
        case _: NumberVal => example.getOrElse(List()).filterNot(_.isNumber)
        case _: BooleanVal => example.getOrElse(List()).filterNot(_.isBoolean)
        case _: ArrayVal => example.getOrElse(List()).filterNot(_.isArray)
        case _: ObjectVal => example.getOrElse(List()).filterNot(_.isObject)
        case _: ReferenceVal => List() // will be done at Components level
      }).map(e => ErrorMessage.badExampleFormat(e.value, items.hint, description.map(_.value).getOrElse("")))

      NonEmptyList.fromList(badTypeExamples)
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

    def hasErrors: Option[NonEmptyList[ErrorMessage]] = {
      val missingProperties = required.getOrElse(List())
        .filterNot(properties.contains)
        .map(ErrorMessage.missingProperty)
      val duplicateRequired = required.getOrElse(List())
        .groupBy(identity)
        .filter(_._2.length > 1)
        .keys.toList
        .map(ErrorMessage.duplicateValue(_, "required"))

      NonEmptyList.fromList(missingProperties ++ duplicateRequired)
    }
  }

  object ObjectVal {
    val hint = "object"
  }

  final case class ReferenceVal($ref: Reference) extends Schema {
    override val hint: String = ReferenceVal.hint

    override def flatten: List[Schema] = List(this)
  }

  object ReferenceVal {
    val hint = "reference"
  }

}
