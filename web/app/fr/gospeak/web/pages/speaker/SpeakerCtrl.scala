package fr.gospeak.web.pages.speaker

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.{UserGroupRepo, UserTalkRepo}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.SpeakerCtrl._
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.UICtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  groupRepo: UserGroupRepo,
                  talkRepo: UserTalkRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def profile(): Action[AnyContent] = SecuredAction { implicit req =>
    val b = breadcrumb(req.identity.user)
    Ok(html.profile()(b))
  }
}

object SpeakerCtrl {
  def breadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Profile" -> routes.SpeakerCtrl.profile())
}
