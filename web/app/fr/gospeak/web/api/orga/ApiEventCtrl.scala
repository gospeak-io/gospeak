package fr.gospeak.web.api.orga

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Event, Group}
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.services.storage.{OrgaEventRepo, OrgaGroupRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.EventOrgaApi
import fr.gospeak.web.api.domain.utils.ApiResponse
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class ApiEventCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   conf: AppConf,
                   val groupRepo: OrgaGroupRepo,
                   eventRepo: OrgaEventRepo) extends ApiCtrl(cc, silhouette, conf) with ApiCtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit ctx: OrgaCtx =>
    eventRepo.list(params).map(ApiResponse.from(_, (i: Event) => EventOrgaApi(i)))
  }
}
