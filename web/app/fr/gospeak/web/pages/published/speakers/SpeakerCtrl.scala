package fr.gospeak.web.pages.published.speakers

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.HeaderInfo
import fr.gospeak.web.pages.published.HomeCtrl
import fr.gospeak.web.pages.published.speakers.SpeakerCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv]) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(): Action[AnyContent] = UserAwareAction { implicit req =>
    Ok(html.list()(header))
  }
}

object SpeakerCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.SpeakerCtrl.list())
}
