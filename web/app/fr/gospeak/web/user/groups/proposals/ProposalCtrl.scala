package fr.gospeak.web.user.groups.proposals

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain._
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.proposals.ProposalCtrl._
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   db: GospeakDb) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(db.group.find(req.identity.user.id, group))
      cfpOpt <- OptionT.liftF(db.cfp.find(groupElt.id))
      proposals <- cfpOpt.map(cfpElt => OptionT.liftF(db.proposal.list(cfpElt.id, params))).getOrElse(OptionT.pure[IO](Page.empty[Proposal](params)))
      speakers <- OptionT.liftF(db.user.list(proposals.items.flatMap(_.speakers.toList)))
      events <- OptionT.liftF(db.event.list(proposals.items.flatMap(_.event)))
      h = listHeader(group)
      b = listBreadcrumb(req.identity.user.name, group -> groupElt.name)
    } yield Ok(html.list(groupElt, cfpOpt, proposals, speakers, events)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def detail(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(db.group.find(req.identity.user.id, group))
      proposalElt <- OptionT(db.proposal.find(proposal))
      speakers <- OptionT.liftF(db.user.list(proposalElt.speakers.toList))
      events <- OptionT.liftF(db.event.list(proposalElt.event.toSeq))
      h = header(group)
      b = breadcrumb(req.identity.user.name, group -> groupElt.name, proposal -> proposalElt.title)
    } yield Ok(html.detail(groupElt, proposalElt, speakers, events, GenericForm.embed)(h, b))).value.map(_.getOrElse(proposalNotFound(group, proposal))).unsafeToFuture()
  }

  def doAddSlides(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => db.proposal.updateSlides(proposal)(slides, now, req.identity.user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(video) => db.proposal.updateVideo(proposal)(video, now, req.identity.user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listHeader(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.groups.routes.GroupCtrl.detail(group)))
      .activeFor(routes.ProposalCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: (Group.Slug, Group.Name)): Breadcrumb =
    GroupCtrl.breadcrumb(user, group).add("Proposals" -> routes.ProposalCtrl.list(group._1))

  def header(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name), proposal: (Proposal.Id, Talk.Title)): Breadcrumb =
    (group, proposal) match {
      case ((groupSlug, _), (proposalId, proposalTitle)) =>
        listBreadcrumb(user, group).add(proposalTitle.value -> routes.ProposalCtrl.detail(groupSlug, proposalId))
    }
}
