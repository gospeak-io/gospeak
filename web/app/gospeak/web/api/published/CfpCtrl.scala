package gospeak.web.api.published

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.{Cfp, CommonCfp, ExternalCfp}
import gospeak.core.services.storage.{PublicExternalCfpRepo, PublicGroupRepo}
import gospeak.web.AppConf
import gospeak.web.api.domain.ApiCfp
import gospeak.web.api.domain.utils.ApiResult
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.utils.ApiCtrl
import gospeak.libs.scala.domain.Page
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              conf: AppConf,
              groupRepo: PublicGroupRepo,
              externalCfpRepo: PublicExternalCfpRepo) extends ApiCtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction[Seq[ApiCfp.Published]] { implicit req =>
    for {
      cfps <- externalCfpRepo.listIncoming(params)
      groups <- groupRepo.list(cfps.items.flatMap(_.group.map(_._1)))
    } yield ApiResult.of(cfps, (cfp: CommonCfp) => ApiCfp.published(cfp, groups))
  }

  def detail(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction[ApiCfp.Published] { implicit req =>
    (for {
      cfpElt <- OptionT(externalCfpRepo.findCommon(cfp))
      groups <- OptionT.liftF(groupRepo.list(cfpElt.group.map(_._1).toList))
      res = ApiResult.of(ApiCfp.published(cfpElt, groups))
    } yield res).value.map(_.getOrElse(cfpNotFound(cfp)))
  }

  def detailExt(cfp: ExternalCfp.Id): Action[AnyContent] = UserAwareAction[ApiCfp.Published] { implicit req =>
    (for {
      cfpElt <- OptionT(externalCfpRepo.findCommon(cfp))
      groups <- OptionT.liftF(groupRepo.list(cfpElt.group.map(_._1).toList))
      res = ApiResult.of(ApiCfp.published(cfpElt, groups))
    } yield res).value.map(_.getOrElse(cfpNotFound(cfp)))
  }
}
