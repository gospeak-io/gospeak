package fr.gospeak.web.user

import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.domain._
import fr.gospeak.web.user.UserCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class UserCtrl(cc: ControllerComponents, db: GospeakDb) extends UICtrl(cc) {
  def index(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    (for {
      groups <- db.getGroups(user.id, Page.Params.defaults)
      talks <- db.getTalks(user.id, Page.Params.defaults)
    } yield Ok(html.index(groups, talks)(indexHeader, breadcrumb(user.name)))).unsafeToFuture()
  }

  def profile(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
  val h = header.activeFor(routes.UserCtrl.profile())
    val b = breadcrumb(user.name).add("Profile" -> routes.UserCtrl.profile())
    Ok(html.profile()(h, b))
  }
}

object UserCtrl {
  val userNav: Seq[NavLink] = Seq(
    NavLink("Groups", groups.routes.GroupCtrl.list()),
    NavLink("Talks", talks.routes.TalkCtrl.list()),
    NavLink("Profile", routes.UserCtrl.profile()))

  val indexHeader: HeaderInfo = HeaderInfo(
    brand = NavLink("Gospeak", fr.gospeak.web.routes.HomeCtrl.index()),
    links = NavDropdown("Public", HomeCtrl.publicNav) +: userNav,
    rightLinks = Seq(NavLink("logout", fr.gospeak.web.auth.routes.AuthCtrl.logout())))

  val header: HeaderInfo =
    indexHeader.copy(brand = NavLink("Gospeak", routes.UserCtrl.index()))

  def breadcrumb(user: User.Name) = Breadcrumb(Seq(
    BreadcrumbLink("Public", fr.gospeak.web.routes.HomeCtrl.index()),
    BreadcrumbLink(user.value, routes.UserCtrl.index())))
}
