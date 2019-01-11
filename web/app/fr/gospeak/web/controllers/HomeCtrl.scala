package fr.gospeak.web.controllers

import play.api.mvc._

class HomeCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def index(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.index())
  }
}
