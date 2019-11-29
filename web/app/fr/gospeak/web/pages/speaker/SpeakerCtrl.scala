package fr.gospeak.web.pages.speaker

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.storage.{UserGroupRepo, UserProposalRepo, UserTalkRepo, UserUserRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.speakers.routes.{SpeakerCtrl => PublishedSpeakerRoutes}
import fr.gospeak.web.pages.speaker.SpeakerCtrl._
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.{UICtrl, UserReq}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  env: ApplicationConf.Env,
                  groupRepo: UserGroupRepo,
                  proposalRepo: UserProposalRepo,
                  talkRepo: UserTalkRepo,
                  userRepo: UserUserRepo) extends UICtrl(cc, silhouette, env) with UICtrl.UserAction {
  def detail(params: Page.Params): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    for {
      proposals <- proposalRepo.listFull(params)
      groups <- groupRepo.list
    } yield Ok(html.detail(proposals, groups)(breadcrumb))
  })

  def edit(): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    editView(ProfileForms.create)
  })

  def doEdit(): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    ProfileForms.create.bindFromRequest.fold(
      formWithErrors => editView(formWithErrors),
      data => userRepo.edit(data)
        .map(_ => Redirect(routes.SpeakerCtrl.detail()).flashing("success" -> "Profile updated"))
    )
  })

  private def editView(form: Form[User.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    val filledForm = if (form.hasErrors) form else form.fill(req.user.data)
    IO(Ok(html.edit(filledForm)(editBreadcrumb)))
  }

  def doEditStatus(status: User.Status): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    val next = redirectToPreviousPageOr(routes.SpeakerCtrl.detail())
    val msg = status match {
      case User.Status.Undefined =>
        "Still unsure about what to do? Your profile is <b>Private</b> by default."
      case User.Status.Private =>
        "Great decision, one step at a time, keep things private and make them public later eventually."
      case User.Status.Public =>
        s"""Nice! You are now officially a public speaker on Gospeak. Here is your <a href="${PublishedSpeakerRoutes.detail(req.user.slug)}" target="_blank">public page</a>."""
    }
    userRepo.editStatus(status).map(_ => next.flashing("success" -> msg))
  })
}

object SpeakerCtrl {
  def breadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    UserCtrl.breadcrumb.add("Profile" -> routes.SpeakerCtrl.detail())

  def editBreadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    breadcrumb.add("Edit" -> routes.SpeakerCtrl.edit())
}
