package fr.gospeak.web.pages.user

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.storage.{UserGroupRepo, UserTalkRepo, UserUserRepo, UserUserRequestRepo}
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
               userRepo: UserUserRepo,
               groupRepo: UserGroupRepo,
               userRequestRepo: UserUserRequestRepo,
               talkRepo: UserTalkRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def index(): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groups <- groupRepo.list(user, Page.Params.defaults)
      talks <- talkRepo.list(user, Page.Params.defaults)
      b = breadcrumb(req.identity.user)
    } yield Ok(html.index(groups, talks)(b))).unsafeToFuture()
  }

  def listGroup(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groups <- groupRepo.list(user, params)
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
        _ <- groupRepo.create(data, by, now)
      } yield Redirect(GroupRoutes.detail(data.slug))
    ).unsafeToFuture()
  }

  private def createGroupForm(form: Form[Group.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    val b = groupBreadcrumb(req.identity.user).add("New" -> routes.UserCtrl.createGroup())
    IO.pure(Ok(html.createGroup(form)(b)))
  }

  def joinGroup(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groups <- groupRepo.listJoinable(user, params)
      pendingRequests <- userRequestRepo.findPendingUserToJoinAGroupRequests(user)
      owners <- userRepo.list(groups.items.flatMap(_.owners.toList).distinct)
      b = groupBreadcrumb(req.identity.user).add("Join" -> routes.UserCtrl.joinGroup())
    } yield Ok(html.joinGroup(groups, owners, pendingRequests)(b))).unsafeToFuture()
  }

  def doJoinGroup(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.findPublic(group))
      _ <- OptionT.liftF(userRequestRepo.createUserAskToJoinAGroup(user, groupElt.id, now))
    } yield Redirect(routes.UserCtrl.index()).flashing("success" -> s"Join request sent to <b>${groupElt.name.value}</b> group"))
      .value.map(_.getOrElse(Redirect(routes.UserCtrl.joinGroup(params)).flashing("error" -> s"Unable to send join request to <b>$group</b>"))).unsafeToFuture()
  }
}

object UserCtrl {
  def breadcrumb(user: User): Breadcrumb =
    Breadcrumb(user.name.value -> routes.UserCtrl.index())

  def groupBreadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> routes.UserCtrl.listGroup())
}
