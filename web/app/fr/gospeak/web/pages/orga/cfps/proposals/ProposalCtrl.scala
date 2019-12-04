package fr.gospeak.web.pages.orga.cfps.proposals

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.services.email.EmailSrv
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.orga.cfps.CfpCtrl
import fr.gospeak.web.pages.orga.cfps.proposals.ProposalCtrl._
import fr.gospeak.web.pages.speaker.talks.proposals.{ProposalForms => SpeakerProposalForms}
import fr.gospeak.web.utils.{GenericForm, OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

import scala.util.control.NonFatal

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   env: ApplicationConf.Env,
                   userRepo: OrgaUserRepo,
                   userRequestRepo: OrgaUserRequestRepo,
                   val groupRepo: OrgaGroupRepo,
                   cfpRepo: OrgaCfpRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo,
                   commentRepo: OrgaCommentRepo,
                   emailSrv: EmailSrv) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def list(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposals <- OptionT.liftF(proposalRepo.listFull(cfpElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users).distinct))
      userRatings <- OptionT.liftF(proposalRepo.listRatings(cfp))
      b = listBreadcrumb(cfpElt)
    } yield Ok(html.list(cfpElt, proposals, speakers, userRatings)(b))).value.map(_.getOrElse(cfpNotFound(group, cfp)))
  })

  def detail(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    proposalView(group, cfp, proposal, GenericForm.comment, GenericForm.comment)
  })

  def doRate(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, grade: Proposal.Rating.Grade): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.rate(cfp, proposalElt.id, grade))
      next = redirectToPreviousPageOr(routes.ProposalCtrl.detail(group, cfp, proposal))
    } yield next.flashing("success" -> s"$grade saved on <b>${proposalElt.title.value}</b>")).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  })

  def doSendComment(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, orga: Boolean): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    GenericForm.comment.bindFromRequest.fold(
      formWithErrors => proposalView(group, cfp, proposal, if (orga) GenericForm.comment else formWithErrors, if (orga) formWithErrors else GenericForm.comment),
      data => (for {
        proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
        _ <- OptionT.liftF(if (orga) commentRepo.addOrgaComment(proposalElt.id, data) else commentRepo.addComment(proposalElt.id, data))
      } yield Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
    )
  })

  private def proposalView(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, commentForm: Form[Comment.Data], orgaCommentForm: Form[Comment.Data])(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    (for {
      proposalElt <- OptionT(proposalRepo.findFull(cfp, proposal))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.users))
      ratings <- OptionT.liftF(proposalRepo.listRatings(proposalElt.id))
      comments <- OptionT.liftF(commentRepo.getComments(proposalElt.id))
      orgaComments <- OptionT.liftF(commentRepo.getOrgaComments(proposalElt.id))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(proposal))
      events <- OptionT.liftF(eventRepo.list(proposalElt.event.map(_.id).toList))
      b = breadcrumb(proposalElt.cfp, proposalElt.proposal)
      res = Ok(html.detail(proposalElt, speakers, ratings, comments, orgaComments, invites, events, SpeakerProposalForms.addSpeaker, GenericForm.embed, commentForm, orgaCommentForm)(b))
    } yield res).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def edit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    editView(group, cfp, proposal, ProposalForms.create)
  })

  def doEdit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => editView(group, cfp, proposal, formWithErrors),
      data => proposalRepo.edit(cfp, proposal, data).map { _ => Redirect(routes.ProposalCtrl.detail(group, cfp, proposal)) }
    )
  })

  private def editView(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, form: Form[Proposal.DataOrga])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      b = breadcrumb(cfpElt, proposalElt).add("Edit" -> routes.ProposalCtrl.edit(group, cfp, proposal))
      filledForm = if (form.hasErrors) form else form.fill(proposalElt.dataOrga)
    } yield Ok(html.edit(cfpElt, proposalElt, filledForm)(b))).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def inviteSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    SpeakerProposalForms.addSpeaker.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      email => (for {
        proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
        invite <- OptionT.liftF(userRequestRepo.invite(proposalElt.id, email))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposal(invite, proposalElt)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
    )
  })

  def cancelInviteSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, request: UserRequest.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      invite <- OptionT.liftF(userRequestRepo.cancelProposalInvite(request))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalCanceled(invite, proposalElt)))
      next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  })

  def removeSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, speaker: User.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    (for {
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        proposalRepo.removeSpeaker(cfp, proposal, speakerElt.id).flatMap { _ =>
          if (speakerElt.id == req.user.id) IO.pure(next.flashing("success" -> s"You removed yourself from this proposal"))
          else emailSrv.send(Emails.speakerRemovedFromProposal(proposalElt, speakerElt))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  })

  def doAddSlides(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => proposalRepo.editSlides(cfp, proposal, slides).map(_ => next)
      }
    )
  })

  def doAddVideo(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(video) => proposalRepo.editVideo(cfp, proposal, video).map(_ => next)
      }
    )
  })

  def reject(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.reject(cfp, proposal))
      next = redirectToPreviousPageOr(routes.ProposalCtrl.detail(group, cfp, proposal))
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  })

  def cancelRejection(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.cancelReject(cfp, proposal))
      next = redirectToPreviousPageOr(routes.ProposalCtrl.detail(group, cfp, proposal))
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  })
}

object ProposalCtrl {
  def listBreadcrumb(cfp: Cfp)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    CfpCtrl.breadcrumb(cfp).add("Proposals" -> routes.ProposalCtrl.list(req.group.slug, cfp.slug))

  def breadcrumb(cfp: Cfp, proposal: Proposal)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb(cfp).add(proposal.title.value -> routes.ProposalCtrl.detail(req.group.slug, cfp.slug, proposal.id))
}
