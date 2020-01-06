package fr.gospeak.web.services.openapi

import cats.data.NonEmptyList
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.services.openapi.error.OpenApiError.{ErrorMessage, ValidationError}
import fr.gospeak.web.services.openapi.error.OpenApiErrors
import fr.gospeak.web.services.openapi.models.utils._
import fr.gospeak.web.services.openapi.models._
import fr.gospeak.web.utils.JsonUtils._
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
    // `lazy` is useful for recursive types: the sub-type needs the super-type but it's not defined yet. If not lazy, you got a NullPointerException
    private val fString: Format[String] = Format((json: JsValue) => json.validate[String], (o: String) => JsString(o))
    implicit lazy val fTODO: Format[TODO] = Format((_: JsValue) => JsSuccess(TODO()), (_: TODO) => JsNull)
    implicit lazy val fMarkdown: Format[Markdown] = fString.imap(Markdown)(_.value)
    implicit lazy val fUrl: Format[Url] = fString.validate(Url.from)(_.value)
    implicit lazy val fEmail: Format[Email] = fString.validate(Email.from)(_.value)
    implicit lazy val fVersion: Format[Version] = fString.validate(Version.from)(_.format)
    implicit lazy val fJs: Format[Js] = Format(js => JsSuccess(Js(js.toString, js.getClass.getSimpleName)), a => Json.parse(a.value))

    implicit lazy val fReference: Format[Reference] = fString.validate(Reference.from)(_.value)
    implicit lazy val fInfoContact: Format[Info.Contact] = Json.format[Info.Contact]
    implicit lazy val fInfoLicense: Format[Info.License] = Json.format[Info.License]
    implicit lazy val fInfo: Format[Info] = Json.format[Info]
    implicit lazy val fExternalDoc: Format[ExternalDoc] = Json.format[ExternalDoc]
    implicit lazy val fServerVariable: Format[Server.Variable] = Json.format[Server.Variable]
    implicit lazy val fServer: Format[Server] = Json.format[Server].verify(_.hasErrors)
    implicit lazy val fTag: Format[Tag] = Json.format[Tag]
    implicit lazy val fSchemaString: Format[Schema.StringVal] = Json.format[Schema.StringVal].hint(Schema.hintAttr, Schema.StringVal.hint)
    implicit lazy val fSchemaInteger: Format[Schema.IntegerVal] = Json.format[Schema.IntegerVal].hint(Schema.hintAttr, Schema.IntegerVal.hint)
    implicit lazy val fSchemaNumber: Format[Schema.NumberVal] = Json.format[Schema.NumberVal].hint(Schema.hintAttr, Schema.NumberVal.hint)
    implicit lazy val fSchemaBoolean: Format[Schema.BooleanVal] = Json.format[Schema.BooleanVal].hint(Schema.hintAttr, Schema.BooleanVal.hint)
    implicit lazy val fSchemaArray: Format[Schema.ArrayVal] = Json.format[Schema.ArrayVal].hint(Schema.hintAttr, Schema.ArrayVal.hint).verify(_.hasErrors)
    implicit lazy val fSchemaObject: Format[Schema.ObjectVal] = Json.format[Schema.ObjectVal].hint(Schema.hintAttr, Schema.ObjectVal.hint).verify(_.hasErrors)
    implicit lazy val fSchemaReference: Format[Schema.ReferenceVal] = Json.format[Schema.ReferenceVal].verify(_.hasErrors)
    implicit lazy val fSchema: Format[Schema] = Format[Schema](
      (json: JsValue) => (json \ Schema.hintAttr).asOpt[String] match {
        case Some(Schema.StringVal.hint) => fSchemaString.reads(json)
        case Some(Schema.IntegerVal.hint) => fSchemaInteger.reads(json)
        case Some(Schema.NumberVal.hint) => fSchemaNumber.reads(json)
        case Some(Schema.BooleanVal.hint) => fSchemaBoolean.reads(json)
        case Some(Schema.ArrayVal.hint) => fSchemaArray.reads(json)
        case Some(Schema.ObjectVal.hint) => fSchemaObject.reads(json)
        case Some(value) => JsError(ErrorMessage.unknownHint(value, Schema.hintAttr).toJson)
        case None => fSchemaReference.reads(json)
      },
      {
        case s: Schema.StringVal => fSchemaString.writes(s)
        case s: Schema.IntegerVal => fSchemaInteger.writes(s)
        case s: Schema.NumberVal => fSchemaNumber.writes(s)
        case s: Schema.BooleanVal => fSchemaBoolean.writes(s)
        case s: Schema.ArrayVal => fSchemaArray.writes(s)
        case s: Schema.ObjectVal => fSchemaObject.writes(s)
        case s: Schema.ReferenceVal => fSchemaReference.writes(s)
      })
    implicit lazy val fParameterLocation: Format[Parameter.Location] = fString.validate(Parameter.Location.from)(_.value)
    implicit lazy val fParameter: Format[Parameter] = Json.format[Parameter]
    implicit lazy val fComponents: Format[Components] = Json.format[Components].verify(_.hasErrors)
    implicit lazy val fHeader: Format[Header] = Json.format[Header]
    implicit lazy val fMediaType: Format[MediaType] = Json.format[MediaType]
    implicit lazy val fLink: Format[Link] = Json.format[Link]
    implicit lazy val fResponse: Format[Response] = Json.format[Response]
    implicit lazy val fRequestBody: Format[RequestBody] = Json.format[RequestBody]
    implicit lazy val fPathItemOperation: Format[PathItem.Operation] = Json.format[PathItem.Operation]
    implicit lazy val fPathItem: Format[PathItem] = Json.format[PathItem]
    implicit lazy val fPaths: Format[Map[Path, PathItem]] = implicitly[Format[Map[String, PathItem]]].validate(
      _.map { case (k, v) => Path.from(k).map(p => (p, v)) }.sequence.map(_.toMap))(
      _.map { case (k, v) => (k.value, v) })
    implicit lazy val fOpenApi: Format[OpenApi] = Json.format[OpenApi].verify(_.hasErrors)
  }

  private def formatError(errs: (JsPath, Seq[JsonValidationError])): ValidationError =
    ValidationError(
      path = errs._1.path.map(_.toJsonString),
      errors = NonEmptyList.fromList(errs._2.map(err => ErrorMessage(err.message, err.args.map(_.toString).toList)).toList)
        .getOrElse(NonEmptyList.of(ErrorMessage.noMessage())))

}
