package fr.gospeak.web.api.ui

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.ExternalCfp
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.cloudinary.CloudinarySrv
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackToken
import fr.gospeak.core.services.storage.PublicExternalCfpRepo
import fr.gospeak.core.services.{MarkdownSrv, TemplateSrv}
import fr.gospeak.infra.services.{EmbedSrv, TemplateSrvImpl}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain.{Html, Markdown, Url}
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.ApiExternalCfp
import fr.gospeak.web.api.domain.utils.{ApiResponse, PublicApiError, PublicApiResponse}
import fr.gospeak.web.api.ui.helpers.JsonFormats._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.libs.json._
import play.api.mvc._
import play.twirl.api.HtmlFormat

import scala.util.control.NonFatal

case class ValidationResult(valid: Boolean, message: String)

case class TemplateDataResponse(data: JsValue)

case class TemplateRequest(template: MustacheMarkdownTmpl[TemplateData], ref: Option[TemplateData.Ref], markdown: Boolean)

case class TemplateResponse(result: Option[Html], error: Option[String])

class UtilsCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                externalCfpRepo: PublicExternalCfpRepo,
                cloudinarySrv: CloudinarySrv,
                slackSrv: SlackSrv,
                templateSrv: TemplateSrv,
                markdownSrv: MarkdownSrv) extends ApiCtrl(cc, silhouette, conf) {
  def cloudinarySignature(): Action[AnyContent] = CustomUserAction { implicit req =>
    val queryParams = req.queryString.flatMap { case (key, values) => values.headOption.map(value => (key, value)) }
    IO.pure(cloudinarySrv.signRequest(queryParams) match {
      case Right(signature) => Ok(signature)
      case Left(error) => BadRequest(Json.toJson(PublicApiError(error)))
    })
  }

  def validateSlackToken(token: String): Action[AnyContent] = CustomUserAction { implicit req =>
    SlackToken.from(token, conf.application.aesKey).toIO.flatMap(slackSrv.getInfos(_, conf.application.aesKey))
      .map(infos => ValidationResult(valid = true, s"Token for ${infos.teamName} team, created by ${infos.userName}"))
      .recover { case NonFatal(e) => ValidationResult(valid = false, s"Invalid token: ${e.getMessage}") }
      .map(res => Ok(Json.toJson(res)))
  }

  def duplicatesExtCfp(params: ExternalCfp.DuplicateParams): Action[AnyContent] = CustomUserAction { implicit req =>
    externalCfpRepo.listDuplicates(params).map(cfps => Ok(Json.toJson(PublicApiResponse(cfps.map(ApiExternalCfp.published), req.now))))
  }

  def embed(url: Url): Action[AnyContent] = CustomUserAwareAction { implicit req =>
    EmbedSrv.embedCode(url).map(code => Ok(code.value))
  }

  def markdownToHtml(): Action[String] = CustomUserAwareAction(parse.text) { implicit req =>
    val md = Markdown(req.body)
    val html = markdownSrv.render(md)
    IO.pure(Ok(html.value))
  }

  def templateData(ref: TemplateData.Ref): Action[AnyContent] = CustomUserAction { implicit req =>
    val data = TemplateData.Sample
      .fromRef(ref)
      .map(TemplateSrvImpl.asData)
      .map(circeToPlay)
      .getOrElse(Json.obj())
    IO.pure(Ok(Json.toJson(TemplateDataResponse(data))))
  }

  def renderTemplate(): Action[JsValue] = UserActionJson[TemplateRequest, TemplateResponse] { implicit req =>
    val template = req.body.ref
      .flatMap(TemplateData.Sample.fromRef)
      .map(templateSrv.render(req.body.template, _))
      .getOrElse(templateSrv.render(req.body.template))
    val res = template match {
      case Left(err) => TemplateResponse(None, Some(err))
      case Right(tmpl) if req.body.markdown => TemplateResponse(Some(markdownSrv.render(tmpl)), None)
      case Right(tmpl) => TemplateResponse(Some(Html(s"<pre>${HtmlFormat.escape(tmpl.value)}</pre>")), None)
    }
    IO.pure(ApiResponse.from(res))
  }

  private def circeToPlay(json: io.circe.Json): JsValue = {
    json.fold(
      jsonNull = JsNull,
      jsonBoolean = (b: Boolean) => JsBoolean(b),
      jsonNumber = (n: io.circe.JsonNumber) => JsNumber(n.toBigDecimal.getOrElse(BigDecimal(n.toDouble))),
      jsonString = (s: String) => JsString(s),
      jsonArray = (v: Vector[io.circe.Json]) => JsArray(v.map(circeToPlay)),
      jsonObject = (o: io.circe.JsonObject) => JsObject(o.toMap.mapValues(circeToPlay))
    )
  }
}
