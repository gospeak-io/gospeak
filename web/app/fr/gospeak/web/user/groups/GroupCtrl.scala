package fr.gospeak.web.user.groups

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.AuthService
import fr.gospeak.web.domain._
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.groups.GroupCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents, db: GospeakDb, auth: AuthService) extends UICtrl(cc) {
  def list(params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      groups <- db.getGroups(user.id, params)
      h = listHeader
      b = listBreadcrumb(user.name)
    } yield Ok(html.list(groups)(h, b))).unsafeToFuture()
  }

  def create(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    createForm(GroupForms.create).unsafeToFuture()
  }

  def doCreate(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    GroupForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => for {
        // TODO check if slug not already exist
        _ <- db.createGroup(data.slug, data.name, data.description, user.id)
      } yield Redirect(routes.GroupCtrl.detail(data.slug))
    ).unsafeToFuture()
  }

  private def createForm(form: Form[GroupForms.Create])(implicit req: Request[AnyContent], user: User): IO[Result] = {
    val h = listHeader
    val b = listBreadcrumb(user.name).add("New" -> routes.GroupCtrl.create())
    IO.pure(Ok(html.create(form)(h, b)))
  }

  def detail(group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      events <- OptionT.liftF(db.getIncomingEvents(groupElt.id, Page.Params.defaults.orderBy("start")))
      h = header(group)
      b = breadcrumb(user.name, group -> groupElt.name)
    } yield Ok(html.detail(groupElt, events)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }
}

object GroupCtrl {
  def groupNav(group: Group.Slug): Seq[NavLink] = Seq(
    NavLink("Events", events.routes.EventCtrl.list(group)),
    NavLink("Proposals", proposals.routes.ProposalCtrl.list(group)),
    NavLink("Settings", settings.routes.SettingsCtrl.list(group)))

  def listHeader: HeaderInfo =
    UserCtrl.header.activeFor(routes.GroupCtrl.list())

  def listBreadcrumb(user: User.Name): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> routes.GroupCtrl.list())

  def header(group: Group.Slug): HeaderInfo = HeaderInfo(
    brand = NavLink("Gospeak", fr.gospeak.web.user.routes.UserCtrl.index()),
    links = NavDropdown("Public", HomeCtrl.publicNav) +: NavDropdown("User", UserCtrl.userNav) +: groupNav(group),
    rightLinks = UserCtrl.rightNav)
    .activeFor(routes.GroupCtrl.list())

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name)): Breadcrumb =
    listBreadcrumb(user).add(group._2.value -> routes.GroupCtrl.detail(group._1))
}
