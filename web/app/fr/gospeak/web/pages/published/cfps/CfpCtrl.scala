package fr.gospeak.web.pages.published.cfps

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import fr.gospeak.core.domain.{Cfp, Talk}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.exceptions.{AccountValidationRequiredException, DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.domain.{Breadcrumb, GospeakMessageBus}
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.published.HomeCtrl
import fr.gospeak.web.pages.published.cfps.CfpCtrl._
import fr.gospeak.web.pages.speaker.talks.proposals.routes.ProposalCtrl
import fr.gospeak.web.utils.{UICtrl, UserAwareReq}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              groupRepo: PublicGroupRepo,
              cfpRepo: PublicCfpRepo,
              talkRepo: SpeakerTalkRepo,
              proposalRepo: SpeakerProposalRepo,
              userRequestRepo: AuthUserRequestRepo,
              authSrv: AuthSrv,
              emailSrv: EmailSrv,
              mb: GospeakMessageBus) extends UICtrl(cc, silhouette) {
  def gettingStarted(): Action[AnyContent] = UserAwareActionIO { implicit req =>
    val b = gettingStartedBreadcrumb()
    IO.pure(Ok(html.gettingStarted()(b)))
  }

  def list(params: Page.Params): Action[AnyContent] = UserAwareActionIO { implicit req =>
    val customParams = params.defaultOrderBy(cfpRepo.fields.close, cfpRepo.fields.name)
    for {
      cfps <- cfpRepo.listOpen(req.now, customParams)
      b = listBreadcrumb()
    } yield Ok(html.list(cfps)(b))
  }

  def detail(cfp: Cfp.Slug): Action[AnyContent] = UserAwareActionIO { implicit req =>
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
      b = breadcrumb(cfpElt)
    } yield Ok(html.detail(cfpElt)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  def propose(cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = UserAwareActionIO { implicit req =>
    proposeForm(cfp, CfpForms.create, params)
  }

  def doPropose(cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = UserAwareActionIO { implicit req =>
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => proposeForm(cfp, formWithErrors, params),
      data => req.secured.map { secured =>
        (for {
          cfpElt <- OptionT(cfpRepo.find(cfp))
          talkElt <- OptionT.liftF(talkRepo.create(data.toTalkData, secured.user.id, req.now))
          proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.toProposalData, talkElt.speakers, secured.user.id, req.now))
          groupElt <- OptionT(groupRepo.find(cfpElt.group))
          _ <- OptionT.liftF(mb.publishProposalCreated(groupElt, cfpElt, proposalElt)(secured))
          b = listBreadcrumb() // replace
          msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
        } yield Redirect(ProposalCtrl.detail(talkElt.slug, cfp)).flashing("success" -> msg)).value.map(_.getOrElse(publicCfpNotFound(cfp)))
      }.getOrElse {
        talkRepo.exists(data.talk.slug).flatMap {
          case true => proposeForm(cfp, CfpForms.create.fill(data).withError("talk.slug", "This slug already exists, please choose an other one"), params)
          case false => proposeConnectForm(cfp, CfpForms.signup.bindFromRequest.discardingErrors, CfpForms.login.bindFromRequest.discardingErrors)
        }
      }
    )
  }

  private def proposeForm(cfp: Cfp.Slug, form: Form[CfpForms.Create], params: Page.Params)(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
    (for {
      cfpElt <- OptionT(cfpRepo.findOpen(cfp, req.now))
      talks <- OptionT.liftF(req.user.map(_.id).map(talkRepo.listActive(_, cfpElt.id, params)).getOrElse(IO.pure(Page.empty[Talk])))
      b = proposeTalkBreadcrumb(cfpElt)
    } yield Ok(html.propose(cfpElt, talks, form)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  def doProposeSignup(cfp: Cfp.Slug): Action[AnyContent] = UserAwareActionIO { implicit req =>
    import cats.implicits._
    CfpForms.signup.bindFromRequest.fold(
      formWithErrors => proposeConnectForm(cfp, formWithErrors, CfpForms.login.bindFromRequest),
      data => (for {
        cfpElt <- OptionT(cfpRepo.find(cfp))
        identity <- OptionT.liftF(authSrv.createIdentity(data.user))
        emailValidation <- OptionT.liftF(userRequestRepo.createAccountValidationRequest(identity.user.email, identity.user.id, req.now))
        (auth, result) <- OptionT.liftF(authSrv.login(identity, data.user.rememberMe, Redirect(ProposalCtrl.detail(data.talk.slug, cfp))))
        secured = req.secured(identity, auth)
        _ <- OptionT.liftF(emailSrv.send(Emails.signup(emailValidation)(secured)))
        talkElt <- OptionT.liftF(talkRepo.create(data.toTalkData, identity.user.id, req.now))
        proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.toProposalData, talkElt.speakers, identity.user.id, req.now))
        groupElt <- OptionT(groupRepo.find(cfpElt.group))
        _ <- OptionT.liftF(mb.publishProposalCreated(groupElt, cfpElt, proposalElt)(secured))
        msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
      } yield result.flashing("success" -> msg)).value.map(_.getOrElse(publicCfpNotFound(cfp))).recoverWith {
        case _: AccountValidationRequiredException => proposeConnectForm(cfp, signupData = Some(data), error = "Account activated, you need to validate it by clicking on the email validation link before submitting a talk")
        case _: DuplicateIdentityException => proposeConnectForm(cfp, signupData = Some(data), error = "User already exists")
        case e: DuplicateSlugException => proposeConnectForm(cfp, signupData = Some(data), error = s"Username ${e.slug.value} is already taken")
        case NonFatal(e) => proposeConnectForm(cfp, signupData = Some(data), error = s"${e.getClass.getSimpleName}: ${e.getMessage}")
      }
    )
  }

  def doProposeLogin(cfp: Cfp.Slug): Action[AnyContent] = UserAwareActionIO { implicit req =>
    import cats.implicits._
    CfpForms.login.bindFromRequest.fold(
      formWithErrors => proposeConnectForm(cfp, CfpForms.signup.bindFromRequest, formWithErrors),
      data => (for {
        cfpElt <- OptionT(cfpRepo.find(cfp))
        identity <- OptionT.liftF(authSrv.getIdentity(data.user))
        (auth, result) <- OptionT.liftF(authSrv.login(identity, data.user.rememberMe, Redirect(ProposalCtrl.detail(data.talk.slug, cfp))))
        secured = req.secured(identity, auth)
        talkElt <- OptionT.liftF(talkRepo.create(data.toTalkData, identity.user.id, req.now))
        proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.toProposalData, talkElt.speakers, identity.user.id, req.now))
        groupElt <- OptionT(groupRepo.find(cfpElt.group))
        _ <- OptionT.liftF(mb.publishProposalCreated(groupElt, cfpElt, proposalElt)(secured))
        msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
      } yield result.flashing("success" -> msg)).value.map(_.getOrElse(publicCfpNotFound(cfp))).recoverWith {
        case _: AccountValidationRequiredException => proposeConnectForm(cfp, loginData = Some(data), error = "You need to validate your account by clicking on the email validation link")
        case _: IdentityNotFoundException => proposeConnectForm(cfp, loginData = Some(data), error = "Wrong login or password")
        case _: InvalidPasswordException => proposeConnectForm(cfp, loginData = Some(data), error = "Wrong login or password")
        case NonFatal(e) => proposeConnectForm(cfp, loginData = Some(data), error = s"${e.getClass.getSimpleName}: ${e.getMessage}")
      }
    )
  }

  private def proposeConnectForm(cfp: Cfp.Slug, signupForm: Form[CfpForms.ProposalSignupData], loginForm: Form[CfpForms.ProposalLoginData])(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
    (for {
      cfpElt <- OptionT(cfpRepo.findOpen(cfp, req.now))
      b = proposeTalkBreadcrumb(cfpElt)
    } yield Ok(html.proposeConnect(cfpElt, signupForm, loginForm)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  private def proposeConnectForm(cfp: Cfp.Slug, error: String, signupData: Option[CfpForms.ProposalSignupData] = None, loginData: Option[CfpForms.ProposalLoginData] = None)(implicit req: UserAwareReq[AnyContent]): IO[Result] =
    proposeConnectForm(
      cfp,
      signupData.map(CfpForms.signup.fill(_).withGlobalError(error)).getOrElse(CfpForms.signup.bindFromRequest.discardingErrors),
      loginData.map(CfpForms.login.fill(_).withGlobalError(error)).getOrElse(CfpForms.login.bindFromRequest.discardingErrors))
}

object CfpCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Cfps" -> routes.CfpCtrl.list())

  def breadcrumb(cfp: Cfp): Breadcrumb =
    listBreadcrumb().add(cfp.name.value -> routes.CfpCtrl.detail(cfp.slug))

  def gettingStartedBreadcrumb(): Breadcrumb =
    listBreadcrumb().add("Getting Started" -> routes.CfpCtrl.gettingStarted())

  def proposeTalkBreadcrumb(cfp: Cfp): Breadcrumb =
    breadcrumb(cfp).add("Proposing a talk" -> routes.CfpCtrl.propose(cfp.slug))
}
