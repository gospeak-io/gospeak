package fr.gospeak.web.auth

import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.AuthCtrl._
import fr.gospeak.web.domain.HeaderInfo
import play.api.mvc._

class AuthCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def login(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.login()(header))
  }

  def logout(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Redirect(fr.gospeak.web.routes.HomeCtrl.index())
  }
}

object AuthCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.AuthCtrl.login())
}
