package fr.gospeak.web.user.groups.cfps

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Cfp, Group, User}
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.cfps.CfpCtrl._
import fr.gospeak.web.user.groups.routes.{GroupCtrl => GroupRoutes}
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              userRepo: UserRepo,
              groupRepo: GroupRepo,
              cfpRepo: CfpRepo,
              eventRepo: EventRepo,
              proposalRepo: ProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfps <- OptionT.liftF(cfpRepo.list(groupElt.id, params)) // TODO listWithProposalCount
      h = listHeader(group)
      b = listBreadcrumb(req.identity.user.name, group -> groupElt.name)
    } yield Ok(html.list(groupElt, cfps)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def create(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(group, CfpForms.create).unsafeToFuture()
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => createForm(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
        // TODO check if slug not already exist
        _ <- OptionT.liftF(cfpRepo.create(groupElt.id, data, req.identity.user.id, now))
      } yield Redirect(routes.CfpCtrl.detail(group, data.slug))).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, form: Form[Cfp.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      h = header(group)
      b = listBreadcrumb(req.identity.user.name, group -> groupElt.name).add("New" -> routes.CfpCtrl.create(group))
    } yield Ok(html.create(groupElt, form)(h, b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposals <- OptionT.liftF(proposalRepo.list(cfpElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.speakers.toList).distinct))
      h = header(group)
      b = breadcrumb(req.identity.user.name, group -> groupElt.name, cfp -> cfpElt.name)
    } yield Ok(html.detail(groupElt, cfpElt, proposals, speakers)(h, b))).value.map(_.getOrElse(cfpNotFound(group, cfp))).unsafeToFuture()
  }

  def edit(group: Group.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, cfp, CfpForms.create).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => editForm(group, cfp, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
        cfpOpt <- OptionT.liftF(cfpRepo.find(groupElt.id, data.slug))
        res <- OptionT.liftF(cfpOpt match {
          case Some(duplicate) if data.slug != cfp =>
            editForm(group, cfp, CfpForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by cfp: ${duplicate.name.value}"))
          case _ =>
            cfpRepo.update(groupElt.id, cfp)(data, req.identity.user.id, now).map { _ => Redirect(routes.CfpCtrl.detail(group, data.slug)) }
        })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, cfp: Cfp.Slug, form: Form[Cfp.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      h = header(group)
      b = breadcrumb(req.identity.user.name, group -> groupElt.name, cfp -> cfpElt.name).add("Edit" -> routes.CfpCtrl.edit(group, cfp))
      filledForm = if (form.hasErrors) form else form.fill(cfpElt.data)
    } yield Ok(html.edit(groupElt, cfpElt, filledForm)(h, b))).value.map(_.getOrElse(cfpNotFound(group, cfp)))
  }
}

object CfpCtrl {
  def listHeader(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", GroupRoutes.detail(group)))
      .activeFor(routes.CfpCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: (Group.Slug, Group.Name)): Breadcrumb =
    group match {
      case (groupSlug, _) => GroupCtrl.breadcrumb(user, group).add("CFPs" -> routes.CfpCtrl.list(groupSlug))
    }

  def header(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name), cfp: (Cfp.Slug, Cfp.Name)): Breadcrumb =
    (group, cfp) match {
      case ((groupSlug, _), (cfpSlug, cfpName)) =>
        listBreadcrumb(user, group).add(cfpName.value -> routes.CfpCtrl.detail(groupSlug, cfpSlug))
    }
}
