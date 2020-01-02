package fr.gospeak.web.api.published

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Cfp
import fr.gospeak.core.services.storage.{PublicCfpRepo, PublicGroupRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.ApiCfp
import fr.gospeak.web.api.domain.utils.ApiResult
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              conf: AppConf,
              groupRepo: PublicGroupRepo,
              cfpRepo: PublicCfpRepo) extends ApiCtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction[Seq[ApiCfp.Published]] { implicit req =>
    for {
      cfps <- cfpRepo.listIncoming(req.now, params)
      groups <- groupRepo.list(cfps.items.map(_.group).distinct)
    } yield ApiResult.of(cfps, (c: Cfp) => ApiCfp.published(c, groups.find(_.id == c.group)))
  }

  def detail(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction[ApiCfp.Published] { implicit req =>
    (for {
      cfpElt <- OptionT(cfpRepo.findRead(cfp))
      groupElt <- OptionT(groupRepo.find(cfpElt.group))
      res = ApiResult.of(ApiCfp.published(cfpElt, Some(groupElt)))
    } yield res).value.map(_.getOrElse(cfpNotFound(cfp)))
  }
}
