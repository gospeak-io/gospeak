package fr.gospeak.web.pages.speaker

import java.time.Instant

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.storage.{UserGroupRepo, UserRepo, UserTalkRepo}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  groupRepo: UserGroupRepo,
                  talkRepo: UserTalkRepo,
                  userRepo: UserRepo) extends UICtrl(cc, silhouette) {

  import silhouette._


  def getProfile(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val b = SpeakerCtrl.breadcrumb(req.identity.user)
    val form = ProfileForms.create
    val filledForm = if (form.hasErrors) {
      println(s"form.has Error : ${form.errors}")
      form
    } else form.fill(req.identity.user.editable)
    IO(Ok(html.profile(filledForm)(b))).unsafeToFuture()
  }

  def profile(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    ProfileForms.create.bindFromRequest.fold(
      formWithErrors => doEditOrCreate(formWithErrors),
      data => userRepo.edit(req.identity.user, data, now).map { _ => Redirect(routes.SpeakerCtrl.profile()) }
    ).unsafeToFuture()
  }

  def doEditOrCreate(form: Form[User.EditableFields])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    val b = SpeakerCtrl.breadcrumb(req.identity.user)
    val filledForm = if (form.hasErrors) form else form.fill(req.identity.user.editable)
    IO(Ok(html.profile(filledForm)(b)))
  }

}

object SpeakerCtrl {
  def breadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Profile" -> routes.SpeakerCtrl.profile())
}
