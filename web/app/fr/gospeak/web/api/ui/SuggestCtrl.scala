package fr.gospeak.web.api.ui

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackToken
import fr.gospeak.core.services.storage._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.JsonFormats._
import fr.gospeak.web.utils.{ApiCtrl, Formats}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.util.control.NonFatal

case class SuggestedItem(id: String, text: String)

case class ValidationResult(valid: Boolean, message: String)

class SuggestCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  groupRepo: SuggestGroupRepo,
                  cfpRepo: SuggestCfpRepo,
                  eventRepo: SuggestEventRepo,
                  talkRepo: SuggestTalkRepo,
                  proposalRepo: SuggestProposalRepo,
                  partnerRepo: SuggestPartnerRepo,
                  venueRepo: SuggestVenueRepo,
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

  def validateSlackToken(token: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    import cats.implicits._
    slackSrv.getInfos(SlackToken(token)).map {
      case Right(infos) => ValidationResult(infos.ok, s"Token for ${infos.team} team, created by ${infos.user}")
      case Left(err) => ValidationResult(err.ok, s"Invalid token: ${err.error}")
    }.recover { case NonFatal(e) => ValidationResult(valid = false, s"Invalid token: ${e.getMessage}") }
      .map { res => Ok(Json.toJson(res)) }.unsafeToFuture()
  }

}
