package fr.gospeak.web.pages.orga.cfps.proposals

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Cfp, Group, Proposal, User}
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.pages.orga.cfps.CfpCtrl
import fr.gospeak.web.pages.orga.cfps.proposals.ProposalCtrl._
import fr.gospeak.web.pages.orga.cfps.routes.{CfpCtrl => CfpRoutes}
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   userRepo: UserRepo,
                   groupRepo: GroupRepo,
                   cfpRepo: CfpRepo,
                   eventRepo: EventRepo,
                   proposalRepo: ProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposals <- OptionT.liftF(proposalRepo.list(cfpElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.speakers.toList).distinct))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_.event.toList).distinct))
      h = listHeader(group, cfp)
      b = listBreadcrumb(req.identity.user.name, groupElt, cfpElt)
    } yield Ok(html.list(groupElt, cfpElt, proposals, speakers, events)(h, b))).value.map(_.getOrElse(cfpNotFound(group, cfp))).unsafeToFuture()
  }

  def detail(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(proposal))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.speakers.toList))
      events <- OptionT.liftF(eventRepo.list(proposalElt.event.toList))
      h = header(group, cfp)
      b = breadcrumb(req.identity.user.name, groupElt, cfpElt, proposalElt)
    } yield Ok(html.detail(groupElt, cfpElt, proposalElt, speakers, events, GenericForm.embed)(h, b))).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal))).unsafeToFuture()
  }

  def doAddSlides(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => proposalRepo.editSlides(proposal)(slides, now, req.identity.user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(video) => proposalRepo.editVideo(proposal)(video, now, req.identity.user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listHeader(group: Group.Slug, cfp: Cfp.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    CfpCtrl.header(group)
      .copy(brand = NavLink("Gospeak", CfpRoutes.detail(group, cfp)))
      .activeFor(routes.ProposalCtrl.list(group, cfp))

  def listBreadcrumb(user: User.Name, group: Group, cfp: Cfp): Breadcrumb =
    CfpCtrl.breadcrumb(user, group, cfp).add("Proposals" -> routes.ProposalCtrl.list(group.slug, cfp.slug))

  def header(group: Group.Slug, cfp: Cfp.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    listHeader(group, cfp)

  def breadcrumb(user: User.Name, group: Group, cfp: Cfp, proposal: Proposal): Breadcrumb =
    listBreadcrumb(user, group, cfp).add(proposal.title.value -> routes.ProposalCtrl.detail(group.slug, cfp.slug, proposal.id))
}
