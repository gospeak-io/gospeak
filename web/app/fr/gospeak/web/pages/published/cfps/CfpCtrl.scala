package fr.gospeak.web.pages.published.cfps

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.UserAwareRequest
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import fr.gospeak.core.domain.utils.GospeakMessage
import fr.gospeak.core.domain.utils.GospeakMessage.ProposalCreated
import fr.gospeak.core.domain.{Cfp, Talk}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.MessageBus
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.emails.Emails
import fr.gospeak.web.auth.exceptions.{AccountValidationRequiredException, DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.pages.speaker.talks.proposals.routes.ProposalCtrl
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
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
              mb: MessageBus[GospeakMessage]) extends UICtrl(cc, silhouette) {

  import silhouette._

  def gettingStarted(): Action[AnyContent] = UserAwareAction { implicit req =>
    Ok(html.gettingStarted())
  }

  def list(params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    val customParams = params.defaultOrderBy(cfpRepo.fields.end, cfpRepo.fields.name)
    (for {
      cfps <- cfpRepo.listOpen(now, customParams)
    } yield Ok(html.list(cfps, now))).unsafeToFuture()
  }

  def detail(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
    } yield Ok(html.detail(cfpElt))).value.map(_.getOrElse(publicCfpNotFound(cfp))).unsafeToFuture()
  }

  def propose(cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    proposeForm(cfp, CfpForms.create, now, params).unsafeToFuture()
  }

  def doPropose(cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => proposeForm(cfp, formWithErrors, now, params),
      data =>
        req.identity.map { identity =>
          (for {
            cfpElt <- OptionT(cfpRepo.find(cfp))
            talkElt <- OptionT.liftF(talkRepo.create(data.toTalkData, identity.user.id, now))
            proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.toProposalData, talkElt.speakers, identity.user.id, now))
            _ <- OptionT.liftF(mb.publish(ProposalCreated(cfpElt, proposalElt)))
            msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
          } yield Redirect(ProposalCtrl.detail(talkElt.slug, cfp)).flashing("success" -> msg)).value.map(_.getOrElse(publicCfpNotFound(cfp)))
        }.getOrElse {
          talkRepo.exists(data.talk.slug).flatMap {
            case true => proposeForm(cfp, CfpForms.create.fill(data).withError("talk.slug", "This slug already exists, please choose an other one"), now, params)
            case false => proposeConnectForm(cfp, CfpForms.signup.bindFromRequest.discardingErrors, CfpForms.login.bindFromRequest.discardingErrors, now)
          }
        }
    ).unsafeToFuture()
  }

  private def proposeForm(cfp: Cfp.Slug, form: Form[CfpForms.Create], now: Instant, params: Page.Params)(implicit req: UserAwareRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      cfpElt <- OptionT(cfpRepo.findOpen(cfp, now))
      talks <- OptionT.liftF(userOpt.map(talkRepo.listActive(_, cfpElt.id, params)).getOrElse(IO.pure(Page.empty[Talk])))
    } yield Ok(html.propose(cfpElt, talks, form))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  def doProposeSignup(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    import cats.implicits._
    val now = Instant.now()
    CfpForms.signup.bindFromRequest.fold(
      formWithErrors => proposeConnectForm(cfp, formWithErrors, CfpForms.login.bindFromRequest, now).unsafeToFuture(),
      data => (for {
        cfpElt <- OptionT(cfpRepo.find(cfp).unsafeToFuture())
        identity <- OptionT.liftF(authSrv.createIdentity(data.user, now).unsafeToFuture())
        emailValidation <- OptionT.liftF(userRequestRepo.createAccountValidationRequest(identity.user.email, identity.user.id, now).unsafeToFuture())
        _ <- OptionT.liftF(emailSrv.send(Emails.signup(identity, emailValidation)).unsafeToFuture())
        result <- OptionT.liftF(authSrv.login(identity, data.user.rememberMe, Redirect(ProposalCtrl.detail(data.talk.slug, cfp))))
        talkElt <- OptionT.liftF(talkRepo.create(data.toTalkData, identity.user.id, now).unsafeToFuture())
        proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.toProposalData, talkElt.speakers, identity.user.id, now).unsafeToFuture())
        _ <- OptionT.liftF(mb.publish(ProposalCreated(cfpElt, proposalElt)).unsafeToFuture())
        msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
      } yield result.flashing("success" -> msg)).value.map(_.getOrElse(publicCfpNotFound(cfp))).recoverWith {
        case _: AccountValidationRequiredException => proposeConnectForm(cfp, now, signupData = Some(data), error = "Account created, you need to validate it by clicking on the email validation link").unsafeToFuture()
        case _: DuplicateIdentityException => proposeConnectForm(cfp, now, signupData = Some(data), error = "User already exists").unsafeToFuture()
        case e: DuplicateSlugException => proposeConnectForm(cfp, now, signupData = Some(data), error = s"Username ${e.slug.value} is already taken").unsafeToFuture()
        case NonFatal(e) => proposeConnectForm(cfp, now, signupData = Some(data), error = s"${e.getClass.getSimpleName}: ${e.getMessage}").unsafeToFuture()
      }
    )
  }

  def doProposeLogin(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    import cats.implicits._
    val now = Instant.now()
    CfpForms.login.bindFromRequest.fold(
      formWithErrors => proposeConnectForm(cfp, CfpForms.signup.bindFromRequest, formWithErrors, now).unsafeToFuture(),
      data => (for {
        cfpElt <- OptionT(cfpRepo.find(cfp).unsafeToFuture())
        identity <- OptionT.liftF(authSrv.getIdentity(data.user))
        result <- OptionT.liftF(authSrv.login(identity, data.user.rememberMe, Redirect(ProposalCtrl.detail(data.talk.slug, cfp))))
        talkElt <- OptionT.liftF(talkRepo.create(data.toTalkData, identity.user.id, now).unsafeToFuture())
        proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.toProposalData, talkElt.speakers, identity.user.id, now).unsafeToFuture())
        _ <- OptionT.liftF(mb.publish(ProposalCreated(cfpElt, proposalElt)).unsafeToFuture())
        msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
      } yield result.flashing("success" -> msg)).value.map(_.getOrElse(publicCfpNotFound(cfp))).recoverWith {
        case _: AccountValidationRequiredException => proposeConnectForm(cfp, now, loginData = Some(data), error = "You need to validate your account by clicking on the email validation link").unsafeToFuture()
        case _: IdentityNotFoundException => proposeConnectForm(cfp, now, loginData = Some(data), error = "Wrong login or password").unsafeToFuture()
        case _: InvalidPasswordException => proposeConnectForm(cfp, now, loginData = Some(data), error = "Wrong login or password").unsafeToFuture()
        case NonFatal(e) => proposeConnectForm(cfp, now, loginData = Some(data), error = s"${e.getClass.getSimpleName}: ${e.getMessage}").unsafeToFuture()
      }
    )
  }

  private def proposeConnectForm(cfp: Cfp.Slug, signupForm: Form[CfpForms.ProposalSignupData], loginForm: Form[CfpForms.ProposalLoginData], now: Instant)(implicit req: UserAwareRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      cfpElt <- OptionT(cfpRepo.findOpen(cfp, now))
    } yield Ok(html.proposeConnect(cfpElt, signupForm, loginForm))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  private def proposeConnectForm(cfp: Cfp.Slug, now: Instant, error: String, signupData: Option[CfpForms.ProposalSignupData] = None, loginData: Option[CfpForms.ProposalLoginData] = None)(implicit req: UserAwareRequest[CookieEnv, AnyContent]): IO[Result] =
    proposeConnectForm(
      cfp,
      signupData.map(CfpForms.signup.fill(_).withGlobalError(error)).getOrElse(CfpForms.signup.bindFromRequest),
      loginData.map(CfpForms.login.fill(_).withGlobalError(error)).getOrElse(CfpForms.login.bindFromRequest),
      now
    )
}
