package gospeak.web.api.published

import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.services.storage.PublicExternalEventRepo
import gospeak.libs.scala.domain.Page
import gospeak.web.AppConf
import gospeak.web.api.domain.ApiEvent
import gospeak.web.api.domain.utils.ApiResult
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class EventCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                externalEventRepo: PublicExternalEventRepo) extends ApiCtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction[Seq[ApiEvent.Common]] { implicit req =>
    externalEventRepo.listCommon(params).map(ApiResult.of(_, ApiEvent.common))
  }
}
