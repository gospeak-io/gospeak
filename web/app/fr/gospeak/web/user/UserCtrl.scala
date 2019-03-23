package fr.gospeak.web.user

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.auth.services.AuthRepo
import fr.gospeak.web.domain._
import fr.gospeak.web.user.UserCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

import scala.concurrent.Future

class UserCtrl(cc: ControllerComponents, db: GospeakDb, silhouette: Silhouette[CookieEnv], auth: AuthRepo) extends UICtrl(cc) {

  import silhouette._

  def unsecure: Action[AnyContent] = UnsecuredAction.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok("UnsecuredAction"))
  }

  def userAware: Action[AnyContent] = UserAwareAction.async { implicit request: UserAwareRequest[CookieEnv, AnyContent] =>
    Future.successful(Ok("UserAwareAction: " + request.identity))
  }

  def secure: Action[AnyContent] = SecuredAction.async { implicit request: SecuredRequest[CookieEnv, AnyContent] =>
    Future.successful(Ok("SecuredAction: " + request.identity))
  }

  def index(): Action[AnyContent] = SecuredAction.async { implicit req: SecuredRequest[CookieEnv, AnyContent] =>
    implicit val user: User = req.identity.user
    (for {
      groups <- db.getGroups(user.id, Page.Params.defaults)
      talks <- db.getTalks(user.id, Page.Params.defaults)
    } yield Ok(html.index(groups, talks)(indexHeader, breadcrumb(user.name)))).unsafeToFuture()
  }

  def profile(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val h = header.activeFor(routes.UserCtrl.profile())
    val b = breadcrumb(user.name).add("Profile" -> routes.UserCtrl.profile())
    Ok(html.profile()(h, b))
  }
}

object UserCtrl {
  val userNav: Seq[NavLink] = Seq(
    NavLink("Groups", groups.routes.GroupCtrl.list()),
    NavLink("Talks", talks.routes.TalkCtrl.list()))

  private val leftNav = NavDropdown("Public", HomeCtrl.publicNav) +: userNav
  val rightNav: Seq[NavMenu] = Seq(NavDropdown("<i class=\"fas fa-user-circle\"></i>", Seq(
    NavLink("Profile", routes.UserCtrl.profile()),
    NavLink("logout", fr.gospeak.web.auth.routes.AuthCtrl.doLogout()))))

  val indexHeader: HeaderInfo = HeaderInfo(
    brand = NavLink("Gospeak", fr.gospeak.web.routes.HomeCtrl.index()),
    links = leftNav,
    rightLinks = rightNav)

  val header: HeaderInfo =
    indexHeader.copy(brand = NavLink("Gospeak", routes.UserCtrl.index()))

  def breadcrumb(user: User.Name) = Breadcrumb(Seq(
    BreadcrumbLink("Public", fr.gospeak.web.routes.HomeCtrl.index()),
    BreadcrumbLink(user.value, routes.UserCtrl.index())))
}
