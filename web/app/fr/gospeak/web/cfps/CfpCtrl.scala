package fr.gospeak.web.cfps

import fr.gospeak.core.domain.User
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.services.AuthRepo
import fr.gospeak.web.cfps.CfpCtrl._
import fr.gospeak.web.domain.HeaderInfo
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents, db: GospeakDb, auth: AuthRepo) extends UICtrl(cc) {
  def list(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    implicit val user: Option[User] = auth.userAware()
    Ok(html.list()(header))
  }
}

object CfpCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.CfpCtrl.list())
}
