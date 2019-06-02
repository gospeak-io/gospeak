package fr.gospeak.web.pages.speaker

import java.time.Instant

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Proposal, User}
import fr.gospeak.core.services.storage.{UserGroupRepo, UserProposalRepo, UserRepo, UserTalkRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  groupRepo: UserGroupRepo,
                  proposalRepo: UserProposalRepo,
                  talkRepo: UserTalkRepo,
                  userRepo: UserRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def profile(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      proposals <- proposalRepo.list(req.identity.user.id, Proposal.Status.Accepted, params)
      groups <- groupRepo.list(req.identity.user.id, params)
      b = SpeakerCtrl.breadcrumb(req.identity.user)
    } yield Ok(html.details(proposals, groups)(b))).unsafeToFuture()
  }

  def getProfile(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      proposals <- proposalRepo.list(req.identity.user.id, Proposal.Status.Accepted, params)
      groups <- groupRepo.list(req.identity.user.id, params)
      b = SpeakerCtrl.editBreadcrumb(req.identity.user)
      form = ProfileForms.create
      filledForm = if (form.hasErrors) form else form.fill(req.identity.user.editable)
    } yield Ok(html.profile(filledForm, proposals, groups)(b))).unsafeToFuture()
  }

  def editProfile(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    ProfileForms.create.bindFromRequest.fold(
      formWithErrors => doEditOrCreate(formWithErrors),
      data => userRepo.edit(req.identity.user, data, now).map { _ => Redirect(routes.SpeakerCtrl.profile()).flashing("success" -> "Profile updated") }
    ).unsafeToFuture()
  }

  private def doEditOrCreate(form: Form[User.EditableFields])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    val b = SpeakerCtrl.breadcrumb(req.identity.user)
    val filledForm = if (form.hasErrors) form else form.fill(req.identity.user.editable)
    IO(Ok(html.profile(filledForm)(b)))
  }
}

object SpeakerCtrl {
  def breadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Profile" -> routes.SpeakerCtrl.profile())

  def editBreadcrumb(user: User): Breadcrumb =
    breadcrumb(user).add("Edit" -> routes.SpeakerCtrl.editProfile())
}
