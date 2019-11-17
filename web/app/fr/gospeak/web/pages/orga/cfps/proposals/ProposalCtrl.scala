package fr.gospeak.web.pages.orga.cfps.proposals

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.orga.cfps.CfpCtrl
import fr.gospeak.web.pages.orga.cfps.proposals.ProposalCtrl._
import fr.gospeak.web.pages.speaker.talks.proposals.ProposalForms
import fr.gospeak.web.utils.{GenericForm, HttpUtils, SecuredReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

import scala.util.control.NonFatal

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   env: ApplicationConf.Env,
                   userRepo: OrgaUserRepo,
                   userRequestRepo: OrgaUserRequestRepo,
                   groupRepo: OrgaGroupRepo,
                   cfpRepo: OrgaCfpRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo,
                   commentRepo: OrgaCommentRepo,
                   emailSrv: EmailSrv) extends UICtrl(cc, silhouette, env) {
  def list(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposals <- OptionT.liftF(proposalRepo.listFull(cfpElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users).distinct))
      userRatings <- OptionT.liftF(proposalRepo.listRatings(cfp, req.user.id))
      b = listBreadcrumb(groupElt, cfpElt)
    } yield Ok(html.list(groupElt, cfpElt, proposals, speakers, userRatings)(b))).value.map(_.getOrElse(cfpNotFound(group, cfp)))
  }

  def detail(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    proposalView(group, cfp, proposal, GenericForm.comment, GenericForm.comment)
  }

  def doRate(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, grade: Proposal.Rating.Grade): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.ProposalCtrl.detail(group, cfp, proposal).toString))
    (for {
      _ <- OptionT(groupRepo.find(req.user.id, group)) // to ensure user is an orga
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.rate(cfp, proposalElt.id, grade, req.user.id, req.now))
    } yield next.flashing("success" -> s"$grade saved on <b>${proposalElt.title.value}</b>")).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def doSendComment(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, orga: Boolean): Action[AnyContent] = SecuredActionIO { implicit req =>
    GenericForm.comment.bindFromRequest.fold(
      formWithErrors => proposalView(group, cfp, proposal, if (orga) GenericForm.comment else formWithErrors, if (orga) formWithErrors else GenericForm.comment),
      data => (for {
        proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
        _ <- OptionT.liftF(if (orga) commentRepo.addOrgaComment(proposalElt.id, data, req.user.id, req.now) else commentRepo.addComment(proposalElt.id, data, req.user.id, req.now))
      } yield Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
    )
  }

  private def proposalView(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, commentForm: Form[Comment.Data], orgaCommentForm: Form[Comment.Data])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.users))
      ratings <- OptionT.liftF(proposalRepo.listRatings(proposalElt.id))
      comments <- OptionT.liftF(commentRepo.getComments(proposalElt.id))
      orgaComments <- OptionT.liftF(commentRepo.getOrgaComments(proposalElt.id))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(proposal))
      events <- OptionT.liftF(eventRepo.list(proposalElt.event.toList))
      b = breadcrumb(groupElt, cfpElt, proposalElt)
      res = Ok(html.detail(groupElt, cfpElt, proposalElt, speakers, ratings, comments, orgaComments, invites, events, ProposalForms.addSpeaker, GenericForm.embed, commentForm, orgaCommentForm)(b))
    } yield res).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def edit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    editView(group, cfp, proposal, ProposalForms.create)
  }

  def doEdit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => editView(group, cfp, proposal, formWithErrors),
      data => proposalRepo.edit(req.user.id, group, cfp, proposal)(data, req.now).map { _ => Redirect(routes.ProposalCtrl.detail(group, cfp, proposal)) }
    )
  }

  private def editView(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, form: Form[Proposal.Data])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      b = breadcrumb(groupElt, cfpElt, proposalElt).add("Edit" -> routes.ProposalCtrl.edit(group, cfp, proposal))
      filledForm = if (form.hasErrors) form else form.fill(proposalElt.data)
    } yield Ok(html.edit(groupElt, cfpElt, proposalElt, filledForm)(b))).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def inviteSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    ProposalForms.addSpeaker.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      email => (for {
        proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
        invite <- OptionT.liftF(userRequestRepo.invite(proposalElt.id, email, req.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposal(invite, proposalElt)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
    )
  }

  def cancelInviteSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, request: UserRequest.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      invite <- OptionT.liftF(userRequestRepo.cancelProposalInvite(request, req.user.id, req.now))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalCanceled(invite, proposalElt)))
      next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def removeSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, speaker: User.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    (for {
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        proposalRepo.removeSpeaker(cfp, proposal)(speakerElt.id, req.user.id, req.now).flatMap { _ =>
          if (speakerElt.id == req.user.id) IO.pure(next.flashing("success" -> s"You removed yourself from this proposal"))
          else emailSrv.send(Emails.speakerRemovedFromProposal(proposalElt, speakerElt))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def doAddSlides(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => proposalRepo.editSlides(cfp, proposal)(slides, req.user.id, req.now).map(_ => next)
      }
    )
  }

  def doAddVideo(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(video) => proposalRepo.editVideo(cfp, proposal)(video, req.user.id, req.now).map(_ => next)
      }
    )
  }

  def reject(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.ProposalCtrl.detail(group, cfp, proposal).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.reject(cfp, proposal, req.user.id, req.now))
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def cancelRejection(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.ProposalCtrl.detail(group, cfp, proposal).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.cancelReject(cfp, proposal, req.user.id, req.now))
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }
}

object ProposalCtrl {
  def listBreadcrumb(group: Group, cfp: Cfp): Breadcrumb =
    CfpCtrl.breadcrumb(group, cfp).add("Proposals" -> routes.ProposalCtrl.list(group.slug, cfp.slug))

  def breadcrumb(group: Group, cfp: Cfp, proposal: Proposal): Breadcrumb =
    listBreadcrumb(group, cfp).add(proposal.title.value -> routes.ProposalCtrl.detail(group.slug, cfp.slug, proposal.id))
}
