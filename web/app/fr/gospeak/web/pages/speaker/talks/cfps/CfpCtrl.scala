package fr.gospeak.web.pages.speaker.talks.cfps

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Cfp, Proposal, Talk, User}
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.talks.cfps.CfpCtrl._
import fr.gospeak.web.pages.speaker.talks.proposals.routes.ProposalCtrl
import fr.gospeak.web.pages.speaker.talks.TalkCtrl
import fr.gospeak.web.pages.speaker.talks.proposals.ProposalForms
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              cfpRepo: SpeakerCfpRepo,
              talkRepo: SpeakerTalkRepo,
              proposalRepo: SpeakerProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(req.identity.user.id, talk))
      cfps <- OptionT.liftF(cfpRepo.availableFor(talkElt.id, params))
      b = listBreadcrumb(req.identity.user, talkElt)
    } yield Ok(html.list(talkElt, cfps)(b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
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
          talkElt <- OptionT(talkRepo.find(req.identity.user.id, talk))
          cfpElt <- OptionT(cfpRepo.find(cfp))
          proposal <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data, talkElt.speakers, req.identity.user.id, now))
        } yield Redirect(ProposalCtrl.detail(talk, proposal.id))).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
      }
    ).unsafeToFuture()
  }

  private def createForm(form: Form[Proposal.Data], talk: Talk.Slug, cfp: Cfp.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(req.identity.user.id, talk))
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalOpt <- OptionT.liftF(proposalRepo.find(talkElt.id, cfpElt.id))
      filledForm = if (form.hasErrors) form else form.fill(Proposal.Data(talkElt))
      b = breadcrumb(req.identity.user, talkElt, cfpElt)
    } yield proposalOpt
      .map(proposal => Redirect(ProposalCtrl.detail(talk, proposal.id)))
      .getOrElse(Ok(html.create(filledForm, talkElt, cfpElt)(b)))).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
  }
}

object CfpCtrl {
  def listBreadcrumb(user: User, talk: Talk): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposing" -> routes.CfpCtrl.list(talk.slug))

  def breadcrumb(user: User, talk: Talk, cfp: Cfp): Breadcrumb =
    listBreadcrumb(user, talk).add(cfp.name.value -> routes.CfpCtrl.create(talk.slug, cfp.slug))
}
