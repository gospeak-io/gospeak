package fr.gospeak.web.pages.published.speakers

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.storage.PublicUserRepo
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  userRepo: PublicUserRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      speakers <- userRepo.listPublic(params)
    } yield Ok(html.list(speakers))).unsafeToFuture()
  }

  def detail(user: User.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      speakerElt <- OptionT(userRepo.findPublic(user))
    } yield Ok(html.detail(speakerElt))).value.map(_.getOrElse(publicUserNotFound(user))).unsafeToFuture()
  }
}
