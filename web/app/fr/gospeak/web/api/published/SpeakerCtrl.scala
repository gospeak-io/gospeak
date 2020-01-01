package fr.gospeak.web.api.published

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.storage.PublicUserRepo
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.SpeakerPublicApi
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  userRepo: PublicUserRepo) extends ApiCtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = ApiActionPage { implicit req =>
    // TODO add proposals, talks, groups member and owners
    userRepo.listPublic(params).map(_.map(SpeakerPublicApi(_)))
  }

  def detail(speaker: User.Slug): Action[AnyContent] = ApiActionOpt { implicit req =>
    // TODO add proposals, talks, groups member and owners
    userRepo.findPublic(speaker).map(_.map(SpeakerPublicApi(_)))
  }
}
