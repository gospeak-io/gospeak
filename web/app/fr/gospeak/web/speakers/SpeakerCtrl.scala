package fr.gospeak.web.speakers

import fr.gospeak.core.domain.User
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.domain.HeaderInfo
import fr.gospeak.web.speakers.SpeakerCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class SpeakerCtrl(cc: ControllerComponents, db: GospeakDb) extends UICtrl(cc) {
  def list(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    implicit val user: Option[User] = db.userAware() // logged user
    Ok(html.list()(header))
  }
}

object SpeakerCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.SpeakerCtrl.list())
}
