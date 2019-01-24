package fr.gospeak.web.user.talks.cfps

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Cfp, Talk, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.user.talks.TalkCtrl
import fr.gospeak.web.user.talks.cfps.CfpCtrl._
import fr.gospeak.web.user.talks.proposals.routes.ProposalCtrl
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents, db: GospeakDb) extends AbstractController(cc) with I18nSupport {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      cfps <- OptionT.liftF(db.getCfps(params))
      h = TalkCtrl.header(talkElt.slug)
      b = listBreadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(html.list(talkElt, cfps)(h, b))).value.map(_.getOrElse(NotFound)).unsafeToFuture()
  }

  def detail(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      cfpElt <- OptionT(db.getCfp(cfp))
      h = TalkCtrl.header(talkElt.slug)
      b = breadcrumb(user.name, talk -> talkElt.title, cfp -> cfpElt.name)
    } yield Ok(html.detail(talkElt, cfpElt)(h, b))).value.map(_.getOrElse(NotFound)).unsafeToFuture()
  }

  def create(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    createForm(CfpForms.create, talk, cfp).unsafeToFuture()
  }

  def doCreate(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors, talk, cfp),
      data => {
        (for {
          talkElt <- OptionT(db.getTalk(user.id, talk))
          cfpElt <- OptionT(db.getCfp(cfp))
          proposal <- OptionT.liftF(db.createProposal(talkElt.id, cfpElt.id, data.title, data.description, user.id))
        } yield Redirect(ProposalCtrl.detail(talk, proposal.id))).value.map(_.getOrElse(NotFound))
      }
    ).unsafeToFuture()
  }

  private def createForm(form: Form[CfpForms.Create], talk: Talk.Slug, cfp: Cfp.Slug)(implicit req: Request[AnyContent], user: User): IO[Result] = {
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      cfpElt <- OptionT(db.getCfp(cfp))
      filledForm = if (form.hasErrors) form else form.fill(CfpForms.Create(talkElt.title, talkElt.description))
      h = TalkCtrl.header(talkElt.slug)
      b = breadcrumb(user.name, talk -> talkElt.title, cfp -> cfpElt.name).add("New" -> routes.CfpCtrl.create(talk, cfp))
    } yield Ok(html.create(filledForm, talkElt, cfpElt)(h, b))).value.map(_.getOrElse(NotFound))
  }
}

object CfpCtrl {
  def listBreadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title)): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposing" -> routes.CfpCtrl.list(talk._1))

  def breadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title), cfp: (Cfp.Slug, Cfp.Name)): Breadcrumb =
    listBreadcrumb(user, talk).add(cfp._2.value -> routes.CfpCtrl.detail(talk._1, cfp._1))
}
