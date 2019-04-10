package fr.gospeak.web.pages.orga

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain._
import fr.gospeak.web.pages.orga.GroupCtrl._
import fr.gospeak.web.pages.published.HomeCtrl
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                userRepo: OrgaUserRepo,
                groupRepo: OrgaGroupRepo,
                eventRepo: OrgaEventRepo,
                proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groups <- groupRepo.list(req.identity.user.id, params)
      h = listHeader()
      b = listBreadcrumb(req.identity.user.name)
    } yield Ok(html.list(groups)(h, b))).unsafeToFuture()
  }

  def create(): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(GroupForms.create).unsafeToFuture()
  }

  def doCreate(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    GroupForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => for {
        // TODO check if slug not already exist
        _ <- groupRepo.create(data, req.identity.user.id, now)
      } yield Redirect(routes.GroupCtrl.detail(data.slug))
    ).unsafeToFuture()
  }

  private def createForm(form: Form[Group.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    val h = listHeader()
    val b = listBreadcrumb(req.identity.user.name).add("New" -> routes.GroupCtrl.create())
    IO.pure(Ok(html.create(form)(h, b)))
  }

  def detail(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      events <- OptionT.liftF(eventRepo.listAfter(groupElt.id, now, Page.Params.defaults.orderBy("start")))
      proposals <- OptionT.liftF(proposalRepo.list(events.items.flatMap(_.talks)))
      speakers <- OptionT.liftF(userRepo.list(proposals.flatMap(_.speakers.toList)))
      h = header(group)
      b = breadcrumb(req.identity.user.name, groupElt)
    } yield Ok(html.detail(groupElt, events, proposals, speakers)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }
}

object GroupCtrl {
  def groupNav(group: Group.Slug): Seq[NavLink] = Seq(
    NavLink("Events", events.routes.EventCtrl.list(group)),
    NavLink("CFPs", cfps.routes.CfpCtrl.list(group)),
    NavLink("Proposals", proposals.routes.ProposalCtrl.list(group)),
    NavLink("Speakers", speakers.routes.SpeakerCtrl.list(group)),
    NavLink("Settings", settings.routes.SettingsCtrl.list(group)))

  def listHeader()(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    UserCtrl.header().activeFor(routes.GroupCtrl.list())

  def listBreadcrumb(user: User.Name): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> routes.GroupCtrl.list())

  def header(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo = HeaderInfo(
    brand = NavLink("Gospeak", fr.gospeak.web.pages.user.routes.UserCtrl.index()),
    links = NavDropdown("Public", HomeCtrl.publicNav) +: NavDropdown("User", UserCtrl.userNav) +: groupNav(group),
    rightLinks = UserCtrl.rightNav())
    .activeFor(routes.GroupCtrl.list())

  def breadcrumb(user: User.Name, group: Group): Breadcrumb =
    listBreadcrumb(user).add(group.name.value -> routes.GroupCtrl.detail(group.slug))
}
