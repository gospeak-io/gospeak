package fr.gospeak.web.user.talks.proposing

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Group, Talk, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.user.talks.TalkCtrl
import fr.gospeak.web.user.talks.proposing.ProposingCtrl._
import fr.gospeak.web.user.talks.proposals.routes.ProposalCtrl
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._

class ProposingCtrl(cc: ControllerComponents, db: GospeakDb) extends AbstractController(cc) with I18nSupport {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    (for {
      talkId <- OptionT(db.getTalkId(user.id, talk))
      talkElt <- OptionT(db.getTalk(talkId, user.id))
      groups <- OptionT.liftF(db.getGroupsWithCfp(params)) // TODO exclude already proposed groups
      h = TalkCtrl.header(talkElt.slug)
      b = listBreadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(html.list(talkElt, groups)(h, b))).value.map(_.getOrElse(NotFound)).unsafeToFuture()
  }

  def detail(talk: Talk.Slug, group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    (for {
      talkId <- OptionT(db.getTalkId(user.id, talk))
      groupId <- OptionT(db.getGroupId(group))
      talkElt <- OptionT(db.getTalk(talkId, user.id))
      groupElt <- OptionT(db.getGroupWithCfp(groupId))
      h = TalkCtrl.header(talkElt.slug)
      b = breadcrumb(user.name, talk -> talkElt.title, group -> groupElt.name)
    } yield Ok(html.detail(talkElt, groupElt)(h, b))).value.map(_.getOrElse(NotFound)).unsafeToFuture()
  }

  def create(talk: Talk.Slug, group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    createForm(ProposingForms.create, talk, group).unsafeToFuture()
  }

  def doCreate(talk: Talk.Slug, group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    ProposingForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors, talk, group),
      data => {
        (for {
          talkId <- OptionT(db.getTalkId(user.id, talk))
          groupId <- OptionT(db.getGroupId(group))
          proposal <- OptionT.liftF(db.createProposal(talkId, groupId, data.title, data.description, user.id))
        } yield Redirect(ProposalCtrl.detail(talk, proposal.id))).value.map(_.getOrElse(NotFound))
      }
    ).unsafeToFuture()
  }

  private def createForm(form: Form[ProposingForms.Create], talk: Talk.Slug, group: Group.Slug)(implicit req: Request[AnyContent], user: User): IO[Result] = {
    (for {
      talkId <- OptionT(db.getTalkId(user.id, talk))
      groupId <- OptionT(db.getGroupId(group))
      talkElt <- OptionT(db.getTalk(talkId, user.id))
      groupElt <- OptionT(db.getGroupWithCfp(groupId))
      filledForm = if(form.hasErrors) form else form.fill(ProposingForms.Create(talkElt.title, talkElt.description))
      h = TalkCtrl.header(talkElt.slug)
      b = breadcrumb(user.name, talk -> talkElt.title, group -> groupElt.name).add("New" -> routes.ProposingCtrl.create(talk, group))
    } yield Ok(html.create(filledForm, talkElt, groupElt)(h, b))).value.map(_.getOrElse(NotFound))
  }
}

object ProposingCtrl {
  def listBreadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title)): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposing" -> routes.ProposingCtrl.list(talk._1))

  def breadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title), group: (Group.Slug, Group.Name)): Breadcrumb =
    listBreadcrumb(user, talk).add(group._2.value -> routes.ProposingCtrl.detail(talk._1, group._1))
}
