package fr.gospeak.web.api.ui

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

case class SuggestedItem(id: String, text: String)

object SuggestedItem {
  implicit val gitStatusWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
}

class SuggestCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  groupRepo: SuggestGroupRepo,
                  cfpRepo: SuggestCfpRepo,
                  eventRepo: SuggestEventRepo,
                  talkRepo: SuggestTalkRepo,
                  proposalRepo: SuggestProposalRepo,
                  partnerRepo: SuggestPartnerRepo) extends ApiCtrl(cc) {

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

  def partners(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      partners <- OptionT.liftF(partnerRepo.list(groupElt.id))
      suggestItems = partners.map(p => SuggestedItem(p.id.value, p.name.value))
    } yield Ok(Json.toJson(suggestItems.sortBy(_.text)))).value.map(_.getOrElse(NotFound(Json.toJson(Seq.empty[SuggestedItem])))).unsafeToFuture()
  }

}
