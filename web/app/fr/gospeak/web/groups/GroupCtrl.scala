package fr.gospeak.web.groups

import fr.gospeak.core.domain.User
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.AuthService
import fr.gospeak.web.domain.HeaderInfo
import fr.gospeak.web.groups.GroupCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents, db: GospeakDb, auth: AuthService) extends UICtrl(cc) {
  def list(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    implicit val user: Option[User] = auth.userAware()
    Ok(html.list()(header))
  }
}

object GroupCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.GroupCtrl.list())
}
