package fr.gospeak.web.api.ui

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.domain._
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackToken
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.TemplateSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain.{Html, Page}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl
import fr.gospeak.web.pages.orga.events.routes.EventCtrl
import fr.gospeak.web.pages.orga.partners.routes.PartnerCtrl
import fr.gospeak.web.pages.orga.speakers.routes.SpeakerCtrl
import fr.gospeak.web.utils.JsonFormats._
import fr.gospeak.web.utils.{ApiCtrl, Formats, MarkdownUtils}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import play.twirl.api.HtmlFormat

import scala.concurrent.Future
import scala.util.control.NonFatal

case class SuggestedItem(id: String, text: String)

case class SearchResultItem(text: String, url: String)

case class ValidationResult(valid: Boolean, message: String)

case class TemplateDataResponse(data: JsValue)

case class TemplateRequest(template: MustacheMarkdownTmpl[TemplateData], ref: Option[TemplateData.Ref], markdown: Boolean)

case class TemplateResponse(result: Option[Html], error: Option[String])

class SuggestCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  appConf: ApplicationConf,
                  groupRepo: SuggestGroupRepo,
                  userRepo: SuggestUserRepo,
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

  def suggestTags(): Action[AnyContent] = Action.async { implicit req =>
    (for {
      gTags <- groupRepo.listTags()
      cTags <- cfpRepo.listTags()
      eTags <- eventRepo.listTags()
      tTags <- talkRepo.listTags()
      pTags <- proposalRepo.listTags()
      suggestItems = (gTags ++ cTags ++ eTags ++ tTags ++ pTags).distinct.map(tag => SuggestedItem(tag.value, tag.value))
    } yield Ok(Json.toJson(suggestItems.sortBy(_.text)))).unsafeToFuture()
  }

  def suggestCfps(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    makeSuggest[Cfp](cfpRepo.list, c => SuggestedItem(c.id.value, c.name.value + " - " + Formats.cfpDates(c)))(group)
  }

  def suggestPartners(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    makeSuggest[Partner](partnerRepo.list, p => SuggestedItem(p.id.value, p.name.value))(group)
  }

  def suggestVenues(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    makeSuggest[Venue.Full](venueRepo.list, v => SuggestedItem(v.venue.id.value, v.partner.name.value + " - " + v.venue.address.value))(group)
  }

  def suggestSponsorPacks(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    makeSuggest[SponsorPack](sponsorPackRepo.listAll, sp => SuggestedItem(sp.id.value, sp.name.value + " (" + sp.price.value + ")"))(group)
  }

  private def makeSuggest[A](list: Group.Id => IO[Seq[A]], format: A => SuggestedItem)
                            (group: Group.Slug)
                            (implicit req: SecuredRequest[CookieEnv, AnyContent]): Future[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      results <- OptionT.liftF(list(groupElt.id))
      res = Ok(Json.toJson(results.map(format).sortBy(_.text)))
    } yield res).value.map(_.getOrElse(NotFound(Json.toJson(Seq.empty[SuggestedItem])))).unsafeToFuture()
  }

  def searchRoot(group: Group.Slug): Action[AnyContent] = SecuredAction { implicit req =>
    BadRequest("this endpoint should not be requested")
  }

  def searchSpeakers(group: Group.Slug, q: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    makeSearch[User](userRepo.speakers, s => SearchResultItem(s.name.value, SpeakerCtrl.detail(group, s.slug).toString))(group, q)
  }

  def searchProposals(group: Group.Slug, q: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    makeSearch[(Proposal, Cfp)](proposalRepo.listWithCfp, { case (p, c) => SearchResultItem(p.title.value, ProposalCtrl.detail(group, c.slug, p.id).toString) })(group, q)
  }

  def searchPartners(group: Group.Slug, q: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    makeSearch[Partner](partnerRepo.list, p => SearchResultItem(p.name.value, PartnerCtrl.detail(group, p.slug).toString))(group, q)
  }

  def searchEvents(group: Group.Slug, q: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    makeSearch[Event](eventRepo.list, e => SearchResultItem(e.name.value, EventCtrl.detail(group, e.slug).toString))(group, q)
  }

  private def makeSearch[A](list: (Group.Id, Page.Params) => IO[Page[A]], format: A => SearchResultItem)
                           (group: Group.Slug, q: String)
                           (implicit req: SecuredRequest[CookieEnv, AnyContent]): Future[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      results <- OptionT.liftF(list(groupElt.id, Page.Params.defaults.search(q)))
      res = Ok(Json.toJson(results.items.map(format)))
    } yield res).value.map(_.getOrElse(NotFound(Json.toJson(Seq.empty[SearchResultItem])))).unsafeToFuture()
  }

  def validateSlackToken(token: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    SlackToken.from(token, appConf.aesKey).toIO.flatMap(slackSrv.getInfos(_, appConf.aesKey))
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
