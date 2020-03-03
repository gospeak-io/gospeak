package gospeak.web.pages.orga.cfps.proposals

import cats.data.{NonEmptyList, OptionT}
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.storage._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Done, Page, Slides, Video}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.emails.Emails
import gospeak.web.pages.orga.cfps.CfpCtrl
import gospeak.web.pages.orga.cfps.proposals.ProposalCtrl._
import gospeak.web.utils.Extensions._
import gospeak.web.utils.{GsForms, OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

import scala.util.control.NonFatal

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   conf: AppConf,
                   userRepo: OrgaUserRepo,
                   userRequestRepo: OrgaUserRequestRepo,
                   val groupRepo: OrgaGroupRepo,
                   cfpRepo: OrgaCfpRepo,
                   eventRepo: OrgaEventRepo,
                   talkRepo: SpeakerTalkRepo,
                   proposalRepo: OrgaProposalRepo,
                   commentRepo: OrgaCommentRepo,
                   emailSrv: EmailSrv) extends UICtrl(cc, silhouette, conf) with UICtrl.OrgaAction {
  def list(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposals <- OptionT.liftF(proposalRepo.listFull(cfp, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users).distinct))
      userRatings <- OptionT.liftF(proposalRepo.listRatings(cfp))
      b = listBreadcrumb(cfpElt)
    } yield Ok(html.list(cfpElt, proposals, speakers, userRatings)(b))).value.map(_.getOrElse(cfpNotFound(group, cfp)))
  }

  def detail(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    proposalView(group, cfp, proposal)
  }

  def doRate(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, grade: Proposal.Rating.Grade): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.rate(cfp, proposalElt.id, grade))
      next = redirectToPreviousPageOr(routes.ProposalCtrl.detail(group, cfp, proposal))
    } yield next.flashing("success" -> s"$grade saved on <b>${proposalElt.title.value}</b>")).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def doSendComment(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, orga: Boolean): Action[AnyContent] = OrgaAction(group) { implicit req =>
    val next = redirectToPreviousPageOr(routes.ProposalCtrl.detail(group, cfp, proposal))
    GsForms.comment.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => (for {
        proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
        cfpElt <- OptionT(cfpRepo.find(cfp))
        talkElt <- OptionT(talkRepo.find(proposalElt.talk))
        _ <- OptionT.liftF(if (orga) {
          commentRepo.addOrgaComment(proposalElt.id, data).map(_ => Done)
        } else {
          for {
            speakers <- userRepo.list(proposalElt.speakers.toList)
            comment <- commentRepo.addComment(proposalElt.id, data)
            _ <- NonEmptyList.fromList(speakers.toList).map(s => emailSrv.send(Emails.proposalCommentAddedForSpeaker(cfpElt, talkElt, proposalElt, s, comment))).sequence
          } yield Done
        })
      } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
    )
  }

  private def proposalView(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    (for {
      proposalElt <- OptionT(proposalRepo.findFull(cfp, proposal))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.users))
      ratings <- OptionT.liftF(proposalRepo.listRatings(proposalElt.id))
      comments <- OptionT.liftF(commentRepo.getComments(proposalElt.id))
      orgaComments <- OptionT.liftF(commentRepo.getOrgaComments(proposalElt.id))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(proposal))
      events <- OptionT.liftF(eventRepo.list(proposalElt.event.map(_.id).toList))
      b = breadcrumb(proposalElt.cfp, proposalElt.proposal)
      res = Ok(html.detail(proposalElt, speakers, ratings, comments, orgaComments, invites, events)(b))
    } yield res).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def edit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, redirect: Option[String]): Action[AnyContent] = OrgaAction(group) { implicit req =>
    editView(group, cfp, proposal, GsForms.proposalOrga, redirect)
  }

  def doEdit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, redirect: Option[String]): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.proposalOrga.bindFromRequest.fold(
      formWithErrors => editView(group, cfp, proposal, formWithErrors, redirect),
      data => proposalRepo.edit(cfp, proposal, data).map { _ => redirectOr(redirect, routes.ProposalCtrl.detail(group, cfp, proposal)) }
    )
  }

  private def editView(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, form: Form[Proposal.DataOrga], redirect: Option[String])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      b = breadcrumb(cfpElt, proposalElt).add("Edit" -> routes.ProposalCtrl.edit(group, cfp, proposal))
      filledForm = if (form.hasErrors) form else form.fill(proposalElt.dataOrga)
    } yield Ok(html.edit(cfpElt, proposalElt, filledForm, redirect)(b))).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def inviteSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GsForms.invite.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => (for {
        cfpElt <- OptionT(cfpRepo.find(cfp))
        proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
        eventElt <- proposalElt.event.map(id => OptionT(eventRepo.find(id))).sequence
        invite <- OptionT.liftF(userRequestRepo.invite(proposalElt.id, data.email))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposal(invite, cfpElt, eventElt, proposalElt, data.message)))
      } yield next.flashing("success" -> s"<b>${invite.email.value}</b> is invited as speaker")).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
    )
  }

  def cancelInviteSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, request: UserRequest.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      invite <- OptionT.liftF(userRequestRepo.cancelProposalInvite(request))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalCanceled(invite, proposalElt)))
      next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def removeSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, speaker: User.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
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
  }

  def doAddSlides(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GsForms.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => proposalRepo.editSlides(cfp, proposal, slides).map(_ => next)
      }
    )
  }

  def doAddVideo(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GsForms.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(video) => proposalRepo.editVideo(cfp, proposal, video).map(_ => next)
      }
    )
  }

  def reject(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.reject(cfp, proposal))
      next = redirectToPreviousPageOr(routes.ProposalCtrl.detail(group, cfp, proposal))
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def cancelRejection(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.cancelReject(cfp, proposal))
      next = redirectToPreviousPageOr(routes.ProposalCtrl.detail(group, cfp, proposal))
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def doEditOrgaTags(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, redirect: Option[String]): Action[AnyContent] = OrgaAction(group) { implicit req =>
    val next = redirectToPreviousPageOr(redirect, routes.ProposalCtrl.detail(group, cfp, proposal))
    GsForms.updateTags.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => proposalRepo.editOrgaTags(cfp, proposal, data).map { _ => next }
    )
  }
}

object ProposalCtrl {
  def listBreadcrumb(cfp: Cfp)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    CfpCtrl.breadcrumb(cfp).add("Proposals" -> routes.ProposalCtrl.list(req.group.slug, cfp.slug))

  def breadcrumb(cfp: Cfp, proposal: Proposal)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb(cfp).add(proposal.title.value -> routes.ProposalCtrl.detail(req.group.slug, cfp.slug, proposal.id))
}
