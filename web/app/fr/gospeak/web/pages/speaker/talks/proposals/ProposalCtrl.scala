package fr.gospeak.web.pages.speaker.talks.proposals

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.GospeakMessage.ProposalMessage.ProposalCreated
import fr.gospeak.core.domain._
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.MessageBus
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.talks.TalkCtrl
import fr.gospeak.web.pages.speaker.talks.cfps.CfpCtrl
import fr.gospeak.web.pages.speaker.talks.cfps.routes.{CfpCtrl => CfpRoutes}
import fr.gospeak.web.pages.speaker.talks.proposals.ProposalCtrl._
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.data.Form
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   userRepo: SpeakerUserRepo,
                   cfpRepo: SpeakerCfpRepo,
                   eventRepo: SpeakerEventRepo,
                   talkRepo: SpeakerTalkRepo,
                   proposalRepo: SpeakerProposalRepo,
                   mb: MessageBus[GospeakMessage]) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      proposals <- OptionT.liftF(proposalRepo.list(talkElt.id, params))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_._2.event)))
      b = listBreadcrumb(req.identity.user, talkElt)
    } yield Ok(html.list(talkElt, proposals, events)(b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def create(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(ProposalForms.create, talk, cfp).unsafeToFuture()
  }

  def doCreate(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors, talk, cfp),
      data => {
        (for {
          talkElt <- OptionT(talkRepo.find(user, talk))
          cfpElt <- OptionT(cfpRepo.find(cfp))
          proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data, talkElt.speakers, by, now))
          _ <- OptionT.liftF(mb.publish(ProposalCreated(proposalElt)))
          msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
        } yield Redirect(routes.ProposalCtrl.detail(talk, cfp)).flashing("success" -> msg)).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
      }
    ).unsafeToFuture()
  }

  private def createForm(form: Form[Proposal.Data], talk: Talk.Slug, cfp: Cfp.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalOpt <- OptionT.liftF(proposalRepo.find(user, talk, cfp))
      filledForm = if (form.hasErrors) form else form.fill(Proposal.Data(talkElt))
      b = CfpCtrl.listBreadcrumb(req.identity.user, talkElt).add(cfpElt.name.value -> CfpRoutes.list(talkElt.slug))
    } yield proposalOpt
      .map(_ => Redirect(routes.ProposalCtrl.detail(talk, cfp)))
      .getOrElse(Ok(html.create(filledForm, talkElt, cfpElt)(b)))).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
  }

  def detail(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalElt <- OptionT(proposalRepo.find(user, talk, cfp))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.users))
      events <- OptionT.liftF(eventRepo.list(proposalElt.event.toSeq))
      b = breadcrumb(req.identity.user, talkElt, cfpElt)
    } yield Ok(html.detail(talkElt, cfpElt, proposalElt, speakers, events, GenericForm.embed)(b))).value.map(_.getOrElse(proposalNotFound(talk, cfp))).unsafeToFuture()
  }

  def edit(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(talk, cfp, ProposalForms.create).unsafeToFuture()
  }

  def doEdit(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => editForm(talk, cfp, formWithErrors),
      data => proposalRepo.edit(talk, cfp)(data, by, now).map { _ => Redirect(routes.ProposalCtrl.detail(talk, cfp)) }
    ).unsafeToFuture()
  }

  private def editForm(talk: Talk.Slug, cfp: Cfp.Slug, form: Form[Proposal.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalElt <- OptionT(proposalRepo.find(user, talk, cfp))
      b = breadcrumb(req.identity.user, talkElt, cfpElt).add("Edit" -> routes.ProposalCtrl.edit(talk, cfp))
      filledForm = if (form.hasErrors) form else form.fill(proposalElt.data)
    } yield Ok(html.edit(filledForm, talkElt, cfpElt, proposalElt)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def doAddSlides(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => proposalRepo.editSlides(talk, cfp)(slides, by, now).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(video) => proposalRepo.editVideo(talk, cfp)(video, by, now).map(_ => next)
      }
    ).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listBreadcrumb(user: User, talk: Talk): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposals" -> routes.ProposalCtrl.list(talk.slug))

  def breadcrumb(user: User, talk: Talk, cfp: Cfp): Breadcrumb =
    listBreadcrumb(user, talk).add(cfp.name.value -> CfpRoutes.list(talk.slug))
}
