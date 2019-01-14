package fr.gospeak.web.groups

import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.groups.GroupCtrl._
import fr.gospeak.web.domain.HeaderInfo
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def list(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(html.list()(header))
  }
}

object GroupCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.GroupCtrl.list())
}
