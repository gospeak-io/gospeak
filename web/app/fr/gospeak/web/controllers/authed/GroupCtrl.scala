package fr.gospeak.web.controllers.authed


import fr.gospeak.web.controllers.authed.routes.{GroupCtrl, HomeCtrl}
import fr.gospeak.web.controllers.routes.{HomeCtrl => PublicHomeCtrl}
import fr.gospeak.web.views.domain._
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val group = "ht-paris"
  private val h = HeaderInfo(
    brand = NavLink("Gospeak", GroupCtrl.group(group)),
    links = Seq(
      NavDropdown("Public", Seq(
        NavLink("Cfps", PublicHomeCtrl.cfps()),
        NavLink("Groups", PublicHomeCtrl.groups()),
        NavLink("Speakers", PublicHomeCtrl.speakers()))),
      NavDropdown("User", Seq(
        NavLink("Groups", HomeCtrl.groups()),
        NavLink("Talks", HomeCtrl.talks()),
        NavLink("Profile", HomeCtrl.profile()))),
      NavLink("Events", GroupCtrl.events(group)),
      NavLink("Proposals", GroupCtrl.proposals(group))),
    rightLinks = Seq(
      NavLink("logout", PublicHomeCtrl.login())))
  private val b = Breadcrumb(Seq(
    BreadcrumbLink("Public", PublicHomeCtrl.index()),
    BreadcrumbLink("Loïc", HomeCtrl.index()),
    BreadcrumbLink("Groups", HomeCtrl.groups()),
    BreadcrumbLink("HumanTalks Paris", GroupCtrl.group(group))))

  def group(group: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.group()(h.copy(brand = NavLink("Gospeak", HomeCtrl.index())).activeFor(HomeCtrl.groups()), b))
  }

  def events(group: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.events()(h.activeFor(GroupCtrl.events(group)), b.add("Events" -> GroupCtrl.events(group))))
  }

  def createEvent(group: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.eventCreate()(h.activeFor(GroupCtrl.events(group)), b.add("Events" -> GroupCtrl.events(group), "new" -> GroupCtrl.createEvent(group))))
  }

  def event(group: String, event: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.event()(h.activeFor(GroupCtrl.events(group)), b.add("Events" -> GroupCtrl.events(group), "HumanTalks Paris Mars 2019" -> GroupCtrl.event(group, event))))
  }

  def proposals(group: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.proposals()(h.activeFor(GroupCtrl.proposals(group)), b.add("Proposals" -> GroupCtrl.proposals(group))))
  }

  def proposal(group: String, proposal: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(fr.gospeak.web.views.html.authed.proposalGroup()(h.activeFor(GroupCtrl.proposals(group)), b.add("Proposals" -> GroupCtrl.proposals(group), "Why FP" -> GroupCtrl.proposal(group, proposal))))
  }
}
