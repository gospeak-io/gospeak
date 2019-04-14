package fr.gospeak.web.pages.user

import java.time.Instant

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.{UserGroupRepo, UserTalkRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain._
import fr.gospeak.web.pages.orga.GroupForms
import fr.gospeak.web.pages.orga.routes.{GroupCtrl => GroupRoutes}
import fr.gospeak.web.pages.user.UserCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class UserCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               groupRepo: UserGroupRepo,
               talkRepo: UserTalkRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def index(): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groups <- groupRepo.list(req.identity.user.id, Page.Params.defaults)
      talks <- talkRepo.list(req.identity.user.id, Page.Params.defaults)
      b = breadcrumb(req.identity.user)
    } yield Ok(html.index(groups, talks)(b))).unsafeToFuture()
  }

  def listGroup(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groups <- groupRepo.list(req.identity.user.id, params)
      b = groupBreadcrumb(req.identity.user)
    } yield Ok(html.listGroup(groups)(b))).unsafeToFuture()
  }

  def createGroup(): Action[AnyContent] = SecuredAction.async { implicit req =>
    createGroupForm(GroupForms.create).unsafeToFuture()
  }

  def doCreateGroup(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    GroupForms.create.bindFromRequest.fold(
      formWithErrors => createGroupForm(formWithErrors),
      data => for {
        // TODO check if slug not already exist
        _ <- groupRepo.create(data, req.identity.user.id, now)
      } yield Redirect(GroupRoutes.detail(data.slug))
    ).unsafeToFuture()
  }

  private def createGroupForm(form: Form[Group.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    val b = groupBreadcrumb(req.identity.user).add("New" -> routes.UserCtrl.createGroup())
    IO.pure(Ok(html.createGroup(form)(b)))
  }
}

object UserCtrl {
  def breadcrumb(user: User): Breadcrumb =
    Breadcrumb(user.name.value -> routes.UserCtrl.index())

  def groupBreadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> routes.UserCtrl.listGroup())
}
