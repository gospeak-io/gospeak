package fr.gospeak.web.api.published

import fr.gospeak.core.domain.User
import fr.gospeak.core.services.storage.PublicUserRepo
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.api.domain.PublicApiSpeaker
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class SpeakerCtrl(cc: ControllerComponents,
                  userRepo: PublicUserRepo) extends ApiCtrl(cc) {
  def list(params: Page.Params): Action[AnyContent] = Action.async { implicit req =>
    responsePage {
      // TODO add proposals, talks, groups member and owners
      userRepo.listPublic(params).map(_.map(PublicApiSpeaker(_)))
    }
  }

  def detail(speaker: User.Slug): Action[AnyContent] = Action.async { implicit req =>
    response {
      // TODO add proposals, talks, groups member and owners
      userRepo.findPublic(speaker).map(_.map(PublicApiSpeaker(_)))
    }
  }
}
