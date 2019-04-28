package fr.gospeak.web.api.ui

import fr.gospeak.core.services.SuggestCfpRepo
import fr.gospeak.web.utils.ApiCtrl
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

case class SuggestedItem(id: String, text: String)

object SuggestedItem {
  implicit val gitStatusWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
}

class SuggestCtrl(cc: ControllerComponents,
                  cfpRepo: SuggestCfpRepo) extends ApiCtrl(cc) {

  def cfpTags(): Action[AnyContent] = Action.async { implicit req =>
    cfpRepo.listTags().map { tags =>
      Ok(Json.toJson(tags.map(tag => SuggestedItem(tag.value, tag.value))))
    }.unsafeToFuture()
  }

}
