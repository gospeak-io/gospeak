package fr.gospeak.web.user.talks.cfps

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain.{Cfp, Talk, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.AuthService
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.user.talks.TalkCtrl
import fr.gospeak.web.user.talks.cfps.CfpCtrl._
import fr.gospeak.web.user.talks.proposals.routes.ProposalCtrl
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents, db: GospeakDb, auth: AuthService) extends UICtrl(cc) {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      cfps <- OptionT.liftF(db.getCfpAvailables(talkElt.id, params))
      h = TalkCtrl.header(talkElt.slug)
      b = listBreadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(html.list(talkElt, cfps)(h, b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def create(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    createForm(CfpForms.create, talk, cfp).unsafeToFuture()
  }

  def doCreate(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors, talk, cfp),
      data => {
        (for {
          talkElt <- OptionT(db.getTalk(user.id, talk))
          cfpElt <- OptionT(db.getCfp(cfp))
          proposal <- OptionT.liftF(db.createProposal(talkElt.id, cfpElt.id, data.title, data.description, talkElt.speakers, user.id, now))
        } yield Redirect(ProposalCtrl.detail(talk, proposal.id))).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
      }
    ).unsafeToFuture()
  }

  private def createForm(form: Form[CfpForms.Create], talk: Talk.Slug, cfp: Cfp.Slug)(implicit req: Request[AnyContent], user: User): IO[Result] = {
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      cfpElt <- OptionT(db.getCfp(cfp))
      proposalOpt <- OptionT.liftF(db.getProposal(talkElt.id, cfpElt.id))
      filledForm = if (form.hasErrors) form else form.fill(CfpForms.Create(talkElt.title, talkElt.description))
      h = TalkCtrl.header(talkElt.slug)
      b = breadcrumb(user.name, talk -> talkElt.title, cfp -> cfpElt.name)
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
