package fr.gospeak.web.utils

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.utils.OrgaReqCtx
import fr.gospeak.core.services.storage.OrgaGroupRepo
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.pages
import play.api.mvc._

abstract class UICtrlOrga(cc: ControllerComponents,
                          silhouette: Silhouette[CookieEnv],
                          env: ApplicationConf.Env,
                          groupRepo: OrgaGroupRepo) extends UICtrl(cc, silhouette, env) {
  private def withCtx(group: Group.Slug)(block: OrgaReqCtx => IO[Result])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    groupRepo.find(req.user.id, group).flatMap {
      case Some(group) => block(new OrgaReqCtx(req.now, req.user, group))
      case None => IO.pure(Redirect(pages.user.routes.UserCtrl.index()).flashing("warning" -> s"Unable to find group with slug '${group.value}'"))
    }
  }

  protected def OrgaAction(group: Group.Slug)(block: SecuredReq[AnyContent] => OrgaReqCtx => IO[Result]): Action[AnyContent] = {
    SecuredActionIO { implicit req =>
      withCtx(group) { implicit ctx =>
        block(req)(ctx)
      }
    }
  }
}
