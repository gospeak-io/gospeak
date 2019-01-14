package fr.gospeak.web.speakers

import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.speakers.SpeakerCtrl._
import fr.gospeak.web.domain.HeaderInfo
import play.api.mvc._

class SpeakerCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def list(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.list()(header))
  }
}

object SpeakerCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.SpeakerCtrl.list())
}
