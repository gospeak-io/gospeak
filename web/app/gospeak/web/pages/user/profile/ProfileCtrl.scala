package gospeak.web.pages.user.profile

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.User
import gospeak.core.services.storage.{UserGroupRepo, UserProposalRepo, UserTalkRepo, UserUserRepo}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.published.speakers.routes.{SpeakerCtrl => PublishedSpeakerRoutes}
import gospeak.web.pages.user.profile.ProfileCtrl._
import gospeak.web.pages.user.UserCtrl
import gospeak.web.utils.{GsForms, UICtrl, UserReq}
import gospeak.libs.scala.domain.Page
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class ProfileCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  groupRepo: UserGroupRepo,
                  proposalRepo: UserProposalRepo,
                  talkRepo: UserTalkRepo,
                  userRepo: UserUserRepo) extends UICtrl(cc, silhouette, conf) {
  def detail(params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    for {
      proposals <- proposalRepo.listFull(params)
      groups <- groupRepo.list
    } yield Ok(html.detail(proposals, groups)(breadcrumb))
  }

  def edit(): Action[AnyContent] = UserAction { implicit req =>
    editView(GsForms.user)
  }

  def doEdit(): Action[AnyContent] = UserAction { implicit req =>
    GsForms.user.bindFromRequest.fold(
      formWithErrors => editView(formWithErrors),
      data => userRepo.edit(data)
        .map(_ => Redirect(routes.ProfileCtrl.detail()).flashing("success" -> "Profile updated"))
    )
  }

  private def editView(form: Form[User.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    val filledForm = if (form.hasErrors) form else form.fill(req.user.data)
    IO(Ok(html.edit(filledForm)(editBreadcrumb)))
  }
}

object ProfileCtrl {
  def breadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    UserCtrl.breadcrumb.add("Profile" -> routes.ProfileCtrl.detail())

  def editBreadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    breadcrumb.add("Edit" -> routes.ProfileCtrl.edit())
}
