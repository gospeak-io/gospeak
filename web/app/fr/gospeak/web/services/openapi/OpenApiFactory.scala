package fr.gospeak.web.services.openapi

import fr.gospeak.web.services.openapi.error.OpenApiError.{Message, ValidationError}
import fr.gospeak.web.services.openapi.error.OpenApiErrors
import fr.gospeak.web.services.openapi.models.{OpenApi, Version}
import fr.gospeak.web.utils.Extensions._
import play.api.libs.json._

object OpenApiFactory {
  def parseJson(json: JsValue): Either[OpenApiErrors, OpenApi] = {
    import Formats._
    json.validate[OpenApi].fold(
      errors => Left(OpenApiErrors(errors.map(formatError))),
      openApi => Right(openApi)
    )
  }

  def toJson(openApi: OpenApi): JsValue = {
    import Formats._
    Json.toJson(openApi)
  }

  private object Formats {
    private val formatString: Format[String] = Format((json: JsValue) => json.validate[String], (o: String) => JsString(o))
    implicit val formatVersion: Format[Version] = formatString.validate(Version.from)(_.format)
    implicit val formatOpenApi: Format[OpenApi] = Json.format[OpenApi]
  }

  private def formatError(err: (JsPath, Seq[JsonValidationError])): ValidationError =
    ValidationError(
      path = err._1.path.map(_.toJsonString),
      errors = err._2.map(e => Message(e.message, e.args.toList)).toList)

}
