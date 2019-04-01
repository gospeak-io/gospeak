package fr.gospeak.web.user.talks.cfps

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
import fr.gospeak.web.user.talks.TalkCtrl
import fr.gospeak.web.user.talks.cfps.CfpCtrl._
import fr.gospeak.web.user.talks.proposals.routes.ProposalCtrl
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              cfpRepo: CfpRepo,
              talkRepo: TalkRepo,
              proposalRepo: ProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(req.identity.user.id, talk))
      cfps <- OptionT.liftF(cfpRepo.listAvailables(talkElt.id, params))
      h = TalkCtrl.header(talkElt.slug)
      b = listBreadcrumb(req.identity.user.name, talk -> talkElt.title)
    } yield Ok(html.list(talkElt, cfps)(h, b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def create(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(CfpForms.create, talk, cfp).unsafeToFuture()
  }

  def doCreate(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    CfpForms.create.bindFromRequest.fold(
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
      h = TalkCtrl.header(talkElt.slug)
      b = breadcrumb(req.identity.user.name, talk -> talkElt.title, cfp -> cfpElt.name)
    } yield proposalOpt
      .map(proposal => Redirect(ProposalCtrl.detail(talk, proposal.id)))
      .getOrElse(Ok(html.create(filledForm, talkElt, cfpElt)(h, b)))).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
  }
}

object CfpCtrl {
  def listBreadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title)): Breadcrumb =
    talk match {
      case (talkSlug, _) => TalkCtrl.breadcrumb(user, talk).add("Proposing" -> routes.CfpCtrl.list(talkSlug))
    }

  def breadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title), cfp: (Cfp.Slug, Cfp.Name)): Breadcrumb =
    (talk, cfp) match {
      case ((talkSlug, _), (cfpSlug, cfpName)) =>
        listBreadcrumb(user, talk).add(cfpName.value -> routes.CfpCtrl.create(talkSlug, cfpSlug))
    }
}
