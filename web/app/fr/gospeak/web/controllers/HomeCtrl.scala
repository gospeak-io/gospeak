package fr.gospeak.web.controllers

import fr.gospeak.web.controllers.routes.HomeCtrl
import fr.gospeak.web.views.domain.{HeaderInfo, NavLink}
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val h = HeaderInfo(
    brand = NavLink("Gospeak", HomeCtrl.index()),
    links = Seq(
      NavLink("Cfps", HomeCtrl.cfps()),
      NavLink("Groups", HomeCtrl.groups()),
      NavLink("Speakers", HomeCtrl.speakers())),
    rightLinks = Seq(
      NavLink("login", HomeCtrl.login())))

  def index(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.index()(h))
  }

  def cfps(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.cfps()(h.activeFor(HomeCtrl.cfps())))
  }

  def groups(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.groups()(h.activeFor(HomeCtrl.groups())))
  }

  def speakers(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.speakers()(h.activeFor(HomeCtrl.speakers())))
  }

  def login(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.login()(h.activeFor(HomeCtrl.login())))
  }
}
