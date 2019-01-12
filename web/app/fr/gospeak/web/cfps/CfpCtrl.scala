package fr.gospeak.web.cfps

import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.cfps.CfpCtrl._
import fr.gospeak.web.views.domain.HeaderInfo
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def list(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.list()(header))
  }
}

object CfpCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.CfpCtrl.list())
}
