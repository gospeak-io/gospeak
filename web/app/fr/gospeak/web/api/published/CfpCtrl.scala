package fr.gospeak.web.api.published

import java.time.Instant

import cats.data.OptionT
import fr.gospeak.core.domain.Cfp
import fr.gospeak.core.services.storage.{PublicCfpRepo, PublicGroupRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.api.domain.PublicApiCfp
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class CfpCtrl(cc: ControllerComponents,
              groupRepo: PublicGroupRepo,
              cfpRepo: PublicCfpRepo) extends ApiCtrl(cc) {
  def list(params: Page.Params): Action[AnyContent] = Action.async { implicit req =>
    responsePage {
      for {
        cfps <- cfpRepo.listOpen(Instant.now(), params)
        groups <- groupRepo.list(cfps.items.map(_.group).distinct)
      } yield cfps.map(c => PublicApiCfp(c, groups.find(_.id == c.group)))
    }
  }

  def detail(cfp: Cfp.Slug): Action[AnyContent] = Action.async { implicit req =>
    responseT {
      for {
        cfpElt <- OptionT(cfpRepo.find(cfp))
        groupElt <- OptionT(groupRepo.find(cfpElt.group))
      } yield PublicApiCfp(cfpElt, Some(groupElt))
    }
  }
}
