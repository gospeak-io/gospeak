package fr.gospeak.web.controllers.authed

import fr.gospeak.web.controllers.authed.routes.HomeCtrl
import fr.gospeak.web.controllers.routes.{HomeCtrl => PublicHomeCtrl}
import fr.gospeak.web.views.domain._
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val h = HeaderInfo(
    brand = NavLink("Gospeak", HomeCtrl.index()),
    links = Seq(
      NavDropdown("Public", Seq(
        NavLink("Cfps", PublicHomeCtrl.cfps()),
        NavLink("Groups", PublicHomeCtrl.groups()),
        NavLink("Speakers", PublicHomeCtrl.speakers()))),
      NavLink("Groups", HomeCtrl.groups()),
      NavLink("Talks", HomeCtrl.talks()),
      NavLink("Profile", HomeCtrl.profile())),
    rightLinks = Seq(
      NavLink("logout", PublicHomeCtrl.login())))
  private val b = Breadcrumb(Seq(
    BreadcrumbLink("Public", PublicHomeCtrl.index()),
    BreadcrumbLink("Loïc", HomeCtrl.index())))

  def index(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.index()(h.copy(brand = NavLink("Gospeak", PublicHomeCtrl.index())), b))
  }

  def groups(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.groups()(h.activeFor(HomeCtrl.groups()), b.add("Groups" -> HomeCtrl.groups())))
  }

  def talks(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.talks()(h.activeFor(HomeCtrl.talks()), b.add("Talks" -> HomeCtrl.talks())))
  }

  def talk(talk: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.talk()(h.activeFor(HomeCtrl.talks()), b.add("Talks" -> HomeCtrl.talks(), "Why FP" -> HomeCtrl.talk(talk))))
  }

  def proposal(talk: String, proposal: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.proposalUser()(h.activeFor(HomeCtrl.talks()), b.add("Talks" -> HomeCtrl.talks(), "Why FP" -> HomeCtrl.talk(talk), "HumanTalks Paris" -> HomeCtrl.proposal(talk, proposal))))
  }

  def profile(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.profile()(h.activeFor(HomeCtrl.profile()), b.add("Profile" -> HomeCtrl.profile())))
  }
}
