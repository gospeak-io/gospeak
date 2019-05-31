package fr.gospeak.web.pages.published.speakers

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.storage.PublicUserRepo
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.speakers.SpeakerCtrl._
import fr.gospeak.web.pages.published.HomeCtrl
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  userRepo: PublicUserRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      speakers <- userRepo.listPublic(params)
      b = listBreadcrumb()
    } yield Ok(html.list(speakers)(b))).unsafeToFuture()
  }

  def detail(user: User.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      speakerElt <- OptionT(userRepo.findPublic(user))
      b = breadcrumb(speakerElt)
    } yield Ok(html.detail(speakerElt)(b))).value.map(_.getOrElse(publicUserNotFound(user))).unsafeToFuture()
  }
}

object SpeakerCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Speakers" -> routes.SpeakerCtrl.list())

  def breadcrumb(speaker: User): Breadcrumb =
    listBreadcrumb().add(speaker.name.value -> routes.SpeakerCtrl.detail(speaker.slug))
}
