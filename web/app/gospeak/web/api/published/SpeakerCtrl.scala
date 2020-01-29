package gospeak.web.api.published

import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.User
import gospeak.core.services.storage.PublicUserRepo
import gospeak.web.AppConf
import gospeak.web.api.domain.ApiUser
import gospeak.web.api.domain.utils.ApiResult
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.utils.ApiCtrl
import gospeak.libs.scala.domain.Page
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  userRepo: PublicUserRepo) extends ApiCtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction[Seq[ApiUser.Published]] { implicit req =>
    // TODO add proposals, talks, groups member and owners
    userRepo.listPublic(params).map(ApiResult.of(_, ApiUser.published))
  }

  def detail(speaker: User.Slug): Action[AnyContent] = UserAwareAction[ApiUser.Published] { implicit req =>
    // TODO add proposals, talks, groups member and owners
    userRepo.findPublic(speaker).map(_.map(g => ApiResult.of(ApiUser.published(g))).getOrElse(userNotFound(speaker)))
  }
}
