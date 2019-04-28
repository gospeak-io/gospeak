package fr.gospeak.web.api.ui

import fr.gospeak.core.services._
import fr.gospeak.web.utils.ApiCtrl
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

case class SuggestedItem(id: String, text: String)

object SuggestedItem {
  implicit val gitStatusWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
}

class SuggestCtrl(cc: ControllerComponents,
                  groupRepo: SuggestGroupRepo,
                  cfpRepo: SuggestCfpRepo,
                  eventRepo: SuggestEventRepo,
                  talkRepo: SuggestTalkRepo,
                  proposalRepo: SuggestProposalRepo) extends ApiCtrl(cc) {

  def tags(): Action[AnyContent] = Action.async { implicit req =>
    (for {
      gTags <- groupRepo.listTags()
      cTags <- cfpRepo.listTags()
      eTags <- eventRepo.listTags()
      tTags <- talkRepo.listTags()
      pTags <- proposalRepo.listTags()
    } yield (gTags ++ cTags ++ eTags ++ tTags ++ pTags).distinct.sortBy(_.value)).map { tags =>
      Ok(Json.toJson(tags.map(tag => SuggestedItem(tag.value, tag.value))))
    }.unsafeToFuture()
  }

}
