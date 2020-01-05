package fr.gospeak.web.services.openapi

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.{ErrorMessage, ValidationError}
import fr.gospeak.web.services.openapi.error.OpenApiErrors
import fr.gospeak.web.services.openapi.models.utils.{Email, Markdown, Reference, Schema, TODO, Url, Version}
import fr.gospeak.web.services.openapi.models.{Components, ExternalDoc, Info, OpenApi, Server, Tag}
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

  object Formats {
    private val formatString: Format[String] = Format((json: JsValue) => json.validate[String], (o: String) => JsString(o))
    implicit val formatTODO: Format[TODO] = Format((_: JsValue) => JsSuccess(TODO()), (_: TODO) => JsNull)
    implicit val formatMarkdown: Format[Markdown] = formatString.imap(Markdown)(_.value)
    implicit val formatUrl: Format[Url] = formatString.validate(Url.from)(_.value)
    implicit val formatEmail: Format[Email] = formatString.validate(Email.from)(_.value)
    implicit val formatVersion: Format[Version] = formatString.validate(Version.from)(_.format)
    implicit val formatReference: Format[Reference] = formatString.validate(Reference.from)(_.value)

    implicit val formatInfoContact: Format[Info.Contact] = Json.format[Info.Contact]
    implicit val formatInfoLicense: Format[Info.License] = Json.format[Info.License]
    implicit val formatInfo: Format[Info] = Json.format[Info]
    implicit val formatExternalDoc: Format[ExternalDoc] = Json.format[ExternalDoc]
    implicit val formatServerVariable: Format[Server.Variable] = Json.format[Server.Variable]
    implicit val formatServer: Format[Server] = Json.format[Server].verify(_.hasErrors)
    implicit val formatTag: Format[Tag] = Json.format[Tag]
    implicit val formatComponentsSchema: Format[Schema] = Format[Schema]( // should be declared before sub-types for recursive types
      new Reads[Schema] {
        override def reads(json: JsValue): JsResult[Schema] = (json \ "type").asOpt[String] match {
          case Some("string") => formatComponentsSchemaString.reads(json)
          case Some("integer") => formatComponentsSchemaInteger.reads(json)
          case Some("number") => formatComponentsSchemaNumber.reads(json)
          case Some("boolean") => formatComponentsSchemaBoolean.reads(json)
          case Some("array") => formatComponentsSchemaArray.reads(json)
          case Some("object") => formatComponentsSchemaObject.reads(json)
          case Some(value) => JsError(ErrorMessage.unknownHint(value, "type").toJson)
          case None => formatComponentsSchemaReference.reads(json)
        }
      },
      new Writes[Schema] {
        override def writes(o: Schema): JsValue = o match {
          case s: Schema.StringVal => formatComponentsSchemaString.writes(s)
          case s: Schema.IntegerVal => formatComponentsSchemaInteger.writes(s)
          case s: Schema.NumberVal => formatComponentsSchemaNumber.writes(s)
          case s: Schema.BooleanVal => formatComponentsSchemaBoolean.writes(s)
          case s: Schema.ArrayVal => formatComponentsSchemaArray.writes(s)
          case s: Schema.ObjectVal => formatComponentsSchemaObject.writes(s)
          case s: Schema.ReferenceVal => formatComponentsSchemaReference.writes(s)
        }
      }
    )
    implicit val formatComponentsSchemaString: Format[Schema.StringVal] = Json.format[Schema.StringVal].hint("type", Schema.StringVal.hint)
    implicit val formatComponentsSchemaInteger: Format[Schema.IntegerVal] = Json.format[Schema.IntegerVal].hint("type", Schema.IntegerVal.hint)
    implicit val formatComponentsSchemaNumber: Format[Schema.NumberVal] = Json.format[Schema.NumberVal].hint("type", Schema.NumberVal.hint)
    implicit val formatComponentsSchemaBoolean: Format[Schema.BooleanVal] = Json.format[Schema.BooleanVal].hint("type", Schema.BooleanVal.hint)
    implicit val formatComponentsSchemaArray: Format[Schema.ArrayVal] = Json.format[Schema.ArrayVal].hint("type", Schema.ArrayVal.hint).verify(_.hasErrors)
    implicit val formatComponentsSchemaObject: Format[Schema.ObjectVal] = Json.format[Schema.ObjectVal].hint("type", Schema.ObjectVal.hint).verify(_.hasErrors)
    implicit val formatComponentsSchemaReference: Format[Schema.ReferenceVal] = Json.format[Schema.ReferenceVal]
    implicit val formatComponents: Format[Components] = Json.format[Components].verify(_.hasErrors)
    implicit val formatOpenApi: Format[OpenApi] = Json.format[OpenApi].verify(_.hasErrors)
  }

  private def formatError(errs: (JsPath, Seq[JsonValidationError])): ValidationError =
    ValidationError(
      path = errs._1.path.map(_.toJsonString),
      errors = NonEmptyList.fromList(errs._2.map(err => ErrorMessage(err.message, err.args.map(_.toString).toList)).toList).getOrElse(NonEmptyList.of(ErrorMessage.noMessage())))

}
