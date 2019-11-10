package fr.gospeak.web.pages.speaker

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.{Group, Proposal, User}
import fr.gospeak.core.services.storage.{UserGroupRepo, UserProposalRepo, UserTalkRepo, UserUserRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.speakers.routes.{SpeakerCtrl => PublishedSpeakerRoutes}
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.{HttpUtils, SecuredReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  env: ApplicationConf.Env,
                  groupRepo: UserGroupRepo,
                  proposalRepo: UserProposalRepo,
                  talkRepo: UserTalkRepo,
                  userRepo: UserUserRepo) extends UICtrl(cc, silhouette, env) {
  def profile(params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    for {
      proposals <- proposalRepo.listFull(req.user.id, params)
      groups <- groupRepo.list(req.user.id)
      b = SpeakerCtrl.breadcrumb(req.user)
    } yield Ok(html.details(proposals, groups)(b))
  }

  def getProfile(params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    for {
      proposals <- proposalRepo.listFull(req.user.id, params)
      groups <- groupRepo.list(req.user.id)
      b = SpeakerCtrl.editBreadcrumb(req.user)
      form = ProfileForms.create
      filledForm = if (form.hasErrors) form else form.fill(req.user.editable)
    } yield Ok(html.profile(filledForm, proposals, groups)(b))
  }

  def changeStatus(status: User.Profile.Status): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.SpeakerCtrl.profile().toString))
    val msg = status match {
      case User.Profile.Status.Undefined =>
        "Still unsure about what to do? Your profile is <b>Private</b> by default."
      case User.Profile.Status.Private =>
        "Great decision, one step at a time, keep things private and make them public later eventually."
      case User.Profile.Status.Public =>
        s"""Nice! You are now officially a public speaker on Gospeak. Here is your <a href="${PublishedSpeakerRoutes.detail(req.user.slug)}" target="_blank">public page</a>."""
    }
    userRepo.editStatus(req.user.id)(status)
      .map(_ => next.flashing("success" -> msg))
  }

  def editProfile(): Action[AnyContent] = SecuredActionIO { implicit req =>
    ProfileForms.create.bindFromRequest.fold(
      formWithErrors => doEditOrCreate(formWithErrors),
      data => userRepo.edit(req.user, data, req.now).map { _ => Redirect(routes.SpeakerCtrl.profile()).flashing("success" -> "Profile updated") }
    )
  }

  private def doEditOrCreate(form: Form[User.EditableFields])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    val b = SpeakerCtrl.breadcrumb(req.user)
    val filledForm = if (form.hasErrors) form else form.fill(req.user.editable)
    IO(Ok(html.profile(filledForm, Page.empty[Proposal.Full], Seq.empty[Group])(b)))
  }
}

object SpeakerCtrl {
  def breadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Profile" -> routes.SpeakerCtrl.profile())

  def editBreadcrumb(user: User): Breadcrumb =
    breadcrumb(user).add("Edit" -> routes.SpeakerCtrl.editProfile())
}
