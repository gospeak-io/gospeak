package fr.gospeak.web.api.ui

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackToken
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.TemplateSrv
import fr.gospeak.libs.scalautils.domain.Html
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.JsonFormats._
import fr.gospeak.web.utils.{ApiCtrl, Formats, MarkdownUtils}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.twirl.api.HtmlFormat

import scala.util.control.NonFatal

case class SuggestedItem(id: String, text: String)

case class ValidationResult(valid: Boolean, message: String)

case class TemplateDataResponse(data: JsValue)

case class TemplateRequest(template: MustacheMarkdownTmpl[TemplateData], ref: Option[TemplateData.Ref], markdown: Boolean)

case class TemplateResponse(result: Option[Html], error: Option[String])

class SuggestCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  groupRepo: SuggestGroupRepo,
                  cfpRepo: SuggestCfpRepo,
                  eventRepo: SuggestEventRepo,
                  talkRepo: SuggestTalkRepo,
                  proposalRepo: SuggestProposalRepo,
                  partnerRepo: SuggestPartnerRepo,
                  venueRepo: SuggestVenueRepo,
                  sponsorPackRepo: SuggestSponsorPackRepo,
                  templateSrv: TemplateSrv,
                  slackSrv: SlackSrv) extends ApiCtrl(cc) {

  import silhouette._

  def tags(): Action[AnyContent] = Action.async { implicit req =>
    (for {
      gTags <- groupRepo.listTags()
      cTags <- cfpRepo.listTags()
      eTags <- eventRepo.listTags()
      tTags <- talkRepo.listTags()
      pTags <- proposalRepo.listTags()
      suggestItems = (gTags ++ cTags ++ eTags ++ tTags ++ pTags).distinct.map(tag => SuggestedItem(tag.value, tag.value))
    } yield Ok(Json.toJson(suggestItems.sortBy(_.text)))).unsafeToFuture()
  }

  def cfps(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfps <- OptionT.liftF(cfpRepo.list(groupElt.id))
      suggestItems = cfps.map(c => SuggestedItem(c.id.value, c.name.value + " - " + Formats.cfpDates(c)))
    } yield Ok(Json.toJson(suggestItems.sortBy(_.text)))).value.map(_.getOrElse(NotFound(Json.toJson(Seq.empty[SuggestedItem])))).unsafeToFuture()
  }

  def partners(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      partners <- OptionT.liftF(partnerRepo.list(groupElt.id))
      suggestItems = partners.map(p => SuggestedItem(p.id.value, p.name.value))
    } yield Ok(Json.toJson(suggestItems.sortBy(_.text)))).value.map(_.getOrElse(NotFound(Json.toJson(Seq.empty[SuggestedItem])))).unsafeToFuture()
  }

  def venues(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      venues <- OptionT.liftF(venueRepo.list(groupElt.id))
      suggestItems = venues.map { case (p, v) => SuggestedItem(v.id.value, p.name.value + " - " + v.address.value) }
    } yield Ok(Json.toJson(suggestItems.sortBy(_.text)))).value.map(_.getOrElse(NotFound(Json.toJson(Seq.empty[SuggestedItem])))).unsafeToFuture()
  }

  def sponsorPacks(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      sponsorPacks <- OptionT.liftF(sponsorPackRepo.listAll(groupElt.id))
      suggestItems = sponsorPacks.sortBy(-_.price.amount).map(sp => SuggestedItem(sp.id.value, sp.name.value + " (" + sp.price.value + ")"))
    } yield Ok(Json.toJson(suggestItems))).value.map(_.getOrElse(NotFound(Json.toJson(Seq.empty[SuggestedItem])))).unsafeToFuture()
  }

  def validateSlackToken(token: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    import cats.implicits._
    slackSrv.getInfos(SlackToken(token))
      .map(infos => ValidationResult(valid = true, s"Token for ${infos.teamName} team, created by ${infos.userName}"))
      .recover { case NonFatal(e) => ValidationResult(valid = false, s"Invalid token: ${e.getMessage}") }
      .map(res => Ok(Json.toJson(res))).unsafeToFuture()
  }

  def templateData(ref: TemplateData.Ref): Action[AnyContent] = SecuredAction.async { implicit req =>
    val data = TemplateData.Sample
      .fromRef(ref)
      .map(templateSrv.asData)
      .map(circeToPlay)
      .getOrElse(Json.obj())
    IO.pure(Ok(Json.toJson(TemplateDataResponse(data)))).unsafeToFuture()
  }

  def renderTemplate(): Action[JsValue] = SecuredAction(parse.json).async { implicit req =>
    req.body.validate[TemplateRequest].fold(
      errors => IO.pure(BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))),
      data => {
        val template = data.ref
          .flatMap(TemplateData.Sample.fromRef)
          .map(templateSrv.render(data.template, _))
          .getOrElse(templateSrv.render(data.template))
        val res = template match {
          case Left(err) => TemplateResponse(None, Some(err))
          case Right(tmpl) if data.markdown => TemplateResponse(Some(MarkdownUtils.render(tmpl)), None)
          case Right(tmpl) => TemplateResponse(Some(Html(s"<pre>${HtmlFormat.escape(tmpl.value)}</pre>")), None)
        }
        IO.pure(Ok(Json.toJson(res)))
      }
    ).unsafeToFuture()
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
