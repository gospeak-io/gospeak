package fr.gospeak.web.api.ui

import java.time.Instant

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.ExternalCfp
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackToken
import fr.gospeak.core.services.storage.PublicExternalCfpRepo
import fr.gospeak.core.services.{MarkdownSrv, TemplateSrv}
import fr.gospeak.infra.services.{EmbedSrv, TemplateSrvImpl}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain.{Html, Markdown, Url}
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.PublicApiExternalCfp
import fr.gospeak.web.api.domain.utils.{PublicApiError, PublicApiResponse}
import fr.gospeak.web.api.utils.JsonFormats._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.{ApiCtrl, UserReq}
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
                slackSrv: SlackSrv,
                templateSrv: TemplateSrv,
                markdownSrv: MarkdownSrv) extends ApiCtrl(cc, conf) {
  private def SecuredActionIO(block: UserReq[AnyContent] => IO[Result]): Action[AnyContent] = SecuredActionIO(parse.anyContent)(block)

  private def SecuredActionIO[A](bodyParser: BodyParser[A])(block: UserReq[A] => IO[Result]): Action[A] = silhouette.SecuredAction(bodyParser).async { r =>
    block(new UserReq[A](r.request, messagesApi.preferred(r.request), Instant.now(), conf, r, r.identity.user, r.identity.groups))
      .recover { case NonFatal(e) => InternalServerError(Json.toJson(PublicApiError(e.getMessage))) }.unsafeToFuture()
  }


  def validateSlackToken(token: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    SlackToken.from(token, conf.application.aesKey).toIO.flatMap(slackSrv.getInfos(_, conf.application.aesKey))
      .map(infos => ValidationResult(valid = true, s"Token for ${infos.teamName} team, created by ${infos.userName}"))
      .recover { case NonFatal(e) => ValidationResult(valid = false, s"Invalid token: ${e.getMessage}") }
      .map(res => Ok(Json.toJson(res)))
  }

  def duplicatesExtCfp(params: ExternalCfp.DuplicateParams): Action[AnyContent] = SecuredActionIO { implicit req =>
    externalCfpRepo.listDuplicates(params).map(cfps => Ok(Json.toJson(PublicApiResponse(cfps.map(PublicApiExternalCfp(_)), req.now))))
  }

  def embed(url: Url): Action[AnyContent] = ActionIO { implicit req =>
    EmbedSrv.embedCode(url)
      .map { code => Ok(code.value) }
  }

  def markdownToHtml(): Action[String] = ActionIO(parse.text) { implicit req =>
    val md = Markdown(req.body)
    val html = markdownSrv.render(md)
    IO.pure(Ok(html.value))
  }

  def templateData(ref: TemplateData.Ref): Action[AnyContent] = SecuredActionIO { implicit req =>
    val data = TemplateData.Sample
      .fromRef(ref)
      .map(TemplateSrvImpl.asData)
      .map(circeToPlay)
      .getOrElse(Json.obj())
    IO.pure(Ok(Json.toJson(TemplateDataResponse(data))))
  }

  def renderTemplate(): Action[JsValue] = SecuredActionIO(parse.json) { implicit req =>
    req.body.validate[TemplateRequest].fold(
      errors => IO.pure(BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))),
      data => {
        val template = data.ref
          .flatMap(TemplateData.Sample.fromRef)
          .map(templateSrv.render(data.template, _))
          .getOrElse(templateSrv.render(data.template))
        val res = template match {
          case Left(err) => TemplateResponse(None, Some(err))
          case Right(tmpl) if data.markdown => TemplateResponse(Some(markdownSrv.render(tmpl)), None)
          case Right(tmpl) => TemplateResponse(Some(Html(s"<pre>${HtmlFormat.escape(tmpl.value)}</pre>")), None)
        }
        IO.pure(Ok(Json.toJson(res)))
      }
    )
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
