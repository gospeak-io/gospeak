package fr.gospeak.web.api.published

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.storage.PublicUserRepo
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.ApiUser
import fr.gospeak.web.api.domain.utils.ApiResponse
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  userRepo: PublicUserRepo) extends ApiCtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction[Seq[ApiUser.Published]] { implicit req =>
    // TODO add proposals, talks, groups member and owners
    userRepo.listPublic(params).map(ApiResponse.from(_, ApiUser.published))
  }

  def detail(speaker: User.Slug): Action[AnyContent] = UserAwareAction[ApiUser.Published] { implicit req =>
    // TODO add proposals, talks, groups member and owners
    userRepo.findPublic(speaker).map(_.map(g => ApiResponse.from(ApiUser.published(g))).getOrElse(userNotFound(speaker)))
  }
}
