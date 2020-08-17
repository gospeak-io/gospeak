package gospeak.web.api.ui

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.ExternalCfp
import gospeak.core.domain.messages.Message
import gospeak.core.services.cloudinary.UploadSrv
import gospeak.core.services.slack.SlackSrv
import gospeak.core.services.slack.domain.SlackToken
import gospeak.core.services.storage.PublicExternalCfpRepo
import gospeak.infra.services.{EmbedSrv, ScraperSrv}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Html, LiquidMarkdown, Markdown, Url}
import gospeak.web.AppConf
import gospeak.web.api.domain.ApiExternalCfp
import gospeak.web.api.domain.utils.ApiResult
import gospeak.web.api.ui.helpers.JsonFormats._
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.services.MessageSrv
import gospeak.web.utils.ApiCtrl
import play.api.libs.json._
import play.api.mvc._
import play.twirl.api.HtmlFormat

import scala.util.control.NonFatal

case class ValidationResult(valid: Boolean, message: String)

case class TemplateDataResponse(data: JsValue)

case class TemplateRequest(template: LiquidMarkdown[Any], ref: Option[Message.Ref], markdown: Boolean)

case class TemplateResponse(result: Option[Html], error: Option[String])

class UtilsCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                externalCfpRepo: PublicExternalCfpRepo,
                uploadSrv: UploadSrv,
                slackSrv: SlackSrv,
                embedSrv: EmbedSrv,
                scraperSrv: ScraperSrv,
                ms: MessageSrv) extends ApiCtrl(cc, silhouette, conf) {
  def cloudinarySignature(): Action[AnyContent] = UserAction[String] { implicit req =>
    val queryParams = req.queryString.flatMap { case (key, values) => values.headOption.map(value => (key, value)) }
    IO.pure(uploadSrv.signRequest(queryParams) match {
      case Right(signature) => ApiResult.of(signature)
      case Left(error) => ApiResult.badRequest(error)
    })
  }

  def validateSlackToken(token: String): Action[AnyContent] = UserAction[ValidationResult] { implicit req =>
    SlackToken.from(token, conf.app.aesKey).toIO.flatMap(slackSrv.getInfos(_, conf.app.aesKey))
      .map(infos => ValidationResult(valid = true, s"Token for ${infos.teamName} team, created by ${infos.userName}"))
      .recover { case NonFatal(e) => ValidationResult(valid = false, s"Invalid token: ${e.getMessage}") }
      .map(ApiResult.of(_))
  }

  def duplicatesExtCfp(params: ExternalCfp.DuplicateParams): Action[AnyContent] = UserAction[Seq[ApiExternalCfp.Published]] { implicit req =>
    externalCfpRepo.listDuplicatesFull(params).map(cfps => ApiResult.of(cfps.map(ApiExternalCfp.published)))
  }

  def embed(url: Url): Action[AnyContent] = UserAwareAction { implicit req =>
    embedSrv.embedCode(url).map(_.value).map(ApiResult.of(_))
  }

  def fetchMetas(url: Url): Action[AnyContent] = UserAwareAction { implicit req =>
    scraperSrv.fetchMetas(url).map(ApiResult.of(_))
  }

  def markdownToHtml(): Action[JsValue] = UserAwareActionJson[String, String] { implicit req =>
    val html = Markdown(req.body).toHtml
    IO.pure(ApiResult.of(html.value))
  }

  def templateData(ref: Message.Ref): Action[AnyContent] = UserAction[TemplateDataResponse] { implicit req =>
    val data = circeToPlay(ms.sample(Some(ref)))
    IO.pure(ApiResult.of(TemplateDataResponse(data)))
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

  def renderTemplate(): Action[JsValue] = UserActionJson[TemplateRequest, TemplateResponse] { implicit req =>
    val data = ms.sample(req.body.ref)(req.withBody(AnyContent()))
    val tmpl = req.body.template.render(data)
    val res = tmpl match {
      case Left(err) => TemplateResponse(None, Some(err.message))
      case Right(tmpl) if req.body.markdown => TemplateResponse(Some(tmpl.toHtml), None)
      case Right(tmpl) => TemplateResponse(Some(Html(s"<pre>${HtmlFormat.escape(tmpl.value)}</pre>")), None)
    }
    IO.pure(ApiResult.of(res))
  }
}
