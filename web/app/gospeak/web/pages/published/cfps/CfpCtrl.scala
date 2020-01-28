package gospeak.web.pages.published.cfps

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import gospeak.core.domain.{Cfp, ExternalCfp, Talk}
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.storage._
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.auth.exceptions.{AccountValidationRequiredException, DuplicateIdentityException, DuplicateSlugException}
import gospeak.web.auth.services.AuthSrv
import gospeak.web.domain.{Breadcrumb, GospeakMessageBus}
import gospeak.web.emails.Emails
import gospeak.web.pages.published.HomeCtrl
import gospeak.web.pages.published.cfps.CfpCtrl._
import gospeak.web.pages.user.talks.proposals.routes.ProposalCtrl
import gospeak.web.utils.{UICtrl, UserAwareReq, UserReq}
import gospeak.libs.scala.domain.{CustomException, Page}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              conf: AppConf,
              groupRepo: PublicGroupRepo,
              cfpRepo: PublicCfpRepo,
              talkRepo: SpeakerTalkRepo,
              proposalRepo: SpeakerProposalRepo,
              userRequestRepo: AuthUserRequestRepo,
              externalCfpRepo: PublicExternalCfpRepo,
              authSrv: AuthSrv,
              emailSrv: EmailSrv,
              mb: GospeakMessageBus) extends UICtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    externalCfpRepo.listIncoming(req.now, params).map(cfps => Ok(html.list(cfps)(listBreadcrumb())))
  }

  def gettingStarted(): Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.gettingStarted()(listBreadcrumb().add("Getting Started" -> routes.CfpCtrl.gettingStarted))))
  }

  def add(): Action[AnyContent] = UserAction { implicit req =>
    IO.pure(Ok(html.create(CfpForms.external)(listBreadcrumb().add("Add" -> routes.CfpCtrl.add))))
  }

  def doAdd(): Action[AnyContent] = UserAction { implicit req =>
    CfpForms.external.bindFromRequest.fold(
      formWithErrors => IO.pure(Ok(html.create(formWithErrors)(listBreadcrumb().add("Add" -> routes.CfpCtrl.add)))),
      data => externalCfpRepo.create(data, req.user.id, req.now).map(cfp => Redirect(routes.CfpCtrl.detailExt(cfp.id)))
    )
  }

  def detailExt(cfp: ExternalCfp.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      cfpElt <- OptionT(externalCfpRepo.find(cfp))
      b = breadcrumb(cfpElt)
    } yield Ok(html.detailExt(cfpElt)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  def detail(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      cfpElt <- OptionT(cfpRepo.findRead(cfp))
      groupElt <- OptionT(groupRepo.find(cfpElt.group))
      b = breadcrumb(cfpElt)
    } yield Ok(html.detail(groupElt, cfpElt)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  def edit(cfp: ExternalCfp.Id): Action[AnyContent] = UserAction { implicit req =>
    editView(cfp, CfpForms.external)
  }

  def doEdit(cfp: ExternalCfp.Id): Action[AnyContent] = UserAction { implicit req =>
    CfpForms.external.bindFromRequest.fold(
      formWithErrors => editView(cfp, formWithErrors),
      data => externalCfpRepo.edit(cfp)(data, req.user.id, req.now)
        .map(_ => Redirect(routes.CfpCtrl.detailExt(cfp)).flashing("success" -> "CFP updated"))
    )
  }

  private def editView(cfp: ExternalCfp.Id, form: Form[ExternalCfp.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      cfpElt <- OptionT(externalCfpRepo.find(cfp))
      b = breadcrumb(cfpElt).add("Edit" -> routes.CfpCtrl.edit(cfp))
      filledForm = if (form.hasErrors) form else form.fill(cfpElt.data)
    } yield Ok(html.edit(cfpElt, filledForm)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  def propose(cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    proposeForm(cfp, CfpForms.create, params)
  }

  def doPropose(cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    CfpForms.create.bindFromRequest.fold(
      formWithErrors => proposeForm(cfp, formWithErrors, params),
      data => req.secured.map { secured =>
        (for {
          cfpElt <- OptionT(cfpRepo.findRead(cfp))
          _ <- if(cfpElt.isActive(req.nowLDT)) OptionT.liftF(IO.pure(())) else OptionT.liftF(IO.raiseError(CustomException("Can't propose a talk, CFP is not open")))
          talkElt <- OptionT.liftF(talkRepo.create(data.toTalkData)(secured))
          proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.toProposalData, talkElt.speakers)(secured))
          groupElt <- OptionT(groupRepo.find(cfpElt.group))
          _ <- OptionT.liftF(mb.publishProposalCreated(groupElt, cfpElt, proposalElt)(secured))
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
      cfpElt <- OptionT(cfpRepo.findIncoming(cfp, req.now))
      groupElt <- OptionT(groupRepo.find(cfpElt.group))
      talks <- OptionT.liftF(req.user.map(_.id).map(talkRepo.listActive(_, cfpElt.id, params)).getOrElse(IO.pure(Page.empty[Talk])))
      b = proposeTalkBreadcrumb(cfpElt)
    } yield Ok(html.propose(groupElt, cfpElt, talks, form)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  def doProposeSignup(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    import cats.implicits._
    CfpForms.signup.bindFromRequest.fold(
      formWithErrors => proposeConnectForm(cfp, formWithErrors, CfpForms.login.bindFromRequest),
      data => (for {
        cfpElt <- OptionT(cfpRepo.findRead(cfp))
        _ <- if(cfpElt.isActive(req.nowLDT)) OptionT.liftF(IO.pure(())) else OptionT.liftF(IO.raiseError(CustomException("Can't propose a talk, CFP is not open")))
        identity <- OptionT.liftF(authSrv.createIdentity(data.user))
        emailValidation <- OptionT.liftF(userRequestRepo.createAccountValidationRequest(identity.user.email, identity.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.signup(emailValidation, identity.user)))
        (auth, result) <- OptionT.liftF(authSrv.login(identity, data.user.rememberMe, _ => IO.pure(Redirect(ProposalCtrl.detail(data.talk.slug, cfp)))))
        secured = req.secured(identity, auth)
        talkElt <- OptionT.liftF(talkRepo.create(data.toTalkData)(secured))
        proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.toProposalData, talkElt.speakers)(secured))
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

  def doProposeLogin(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    import cats.implicits._
    CfpForms.login.bindFromRequest.fold(
      formWithErrors => proposeConnectForm(cfp, CfpForms.signup.bindFromRequest, formWithErrors),
      data => (for {
        cfpElt <- OptionT(cfpRepo.findRead(cfp))
        _ <- if(cfpElt.isActive(req.nowLDT)) OptionT.liftF(IO.pure(())) else OptionT.liftF(IO.raiseError(CustomException("Can't propose a talk, CFP is not open")))
        identity <- OptionT.liftF(authSrv.getIdentity(data.user))
        (auth, result) <- OptionT.liftF(authSrv.login(identity, data.user.rememberMe, _ => IO.pure(Redirect(ProposalCtrl.detail(data.talk.slug, cfp)))))
        secured = req.secured(identity, auth)
        talkElt <- OptionT.liftF(talkRepo.create(data.toTalkData)(secured))
        proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.toProposalData, talkElt.speakers)(secured))
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
      cfpElt <- OptionT(cfpRepo.findIncoming(cfp, req.now))
      groupElt <- OptionT(groupRepo.find(cfpElt.group))
      b = proposeTalkBreadcrumb(cfpElt)
    } yield Ok(html.proposeConnect(groupElt, cfpElt, signupForm, loginForm)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  private def proposeConnectForm(cfp: Cfp.Slug, error: String, signupData: Option[CfpForms.ProposalSignupData] = None, loginData: Option[CfpForms.ProposalLoginData] = None)(implicit req: UserAwareReq[AnyContent]): IO[Result] =
    proposeConnectForm(
      cfp,
      signupData.map(CfpForms.signup.fill(_).withGlobalError(error)).getOrElse(CfpForms.signup.bindFromRequest.discardingErrors),
      loginData.map(CfpForms.login.fill(_).withGlobalError(error)).getOrElse(CfpForms.login.bindFromRequest.discardingErrors))
}

object CfpCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("CFPs" -> routes.CfpCtrl.list())

  def breadcrumb(cfp: Cfp): Breadcrumb =
    listBreadcrumb().add(cfp.name.value -> routes.CfpCtrl.detail(cfp.slug))

  def breadcrumb(cfp: ExternalCfp): Breadcrumb =
    listBreadcrumb().add(cfp.name.value -> routes.CfpCtrl.detailExt(cfp.id))

  def proposeTalkBreadcrumb(cfp: Cfp): Breadcrumb =
    breadcrumb(cfp).add("Proposing a talk" -> routes.CfpCtrl.propose(cfp.slug))
}
