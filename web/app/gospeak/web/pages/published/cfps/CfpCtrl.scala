package gospeak.web.pages.published.cfps

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import gospeak.core.domain.messages.Message
import gospeak.core.domain.{Cfp, ExternalCfp, ExternalEvent, Talk}
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.storage._
import gospeak.libs.scala.MessageBus
import gospeak.libs.scala.domain.{CustomException, Page}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.auth.exceptions.{AccountValidationRequiredException, DuplicateIdentityException, DuplicateSlugException}
import gospeak.web.auth.services.AuthSrv
import gospeak.web.domain.Breadcrumb
import gospeak.web.emails.Emails
import gospeak.web.pages.published.HomeCtrl
import gospeak.web.pages.published.cfps.CfpCtrl._
import gospeak.web.pages.user.talks.proposals.routes.ProposalCtrl
import gospeak.web.services.MessageSrv
import gospeak.web.utils.{GsForms, UICtrl, UserAwareReq, UserReq}
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
              externalEventRepo: PublicExternalEventRepo,
              externalCfpRepo: PublicExternalCfpRepo,
              authSrv: AuthSrv,
              emailSrv: EmailSrv,
              ms: MessageSrv,
              bus: MessageBus[Message]) extends UICtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    externalCfpRepo.listIncoming(params).map(cfps => Ok(html.list(cfps)(listBreadcrumb())))
  }

  def gettingStarted(): Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.gettingStarted()(listBreadcrumb().add("Getting Started" -> routes.CfpCtrl.gettingStarted))))
  }

  def findExternalEvent(params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    externalEventRepo.list(params).map(events => Ok(html.findExternalEvent(events)(extEventsBreadcrumb())))
  }

  def createExternalEvent(): Action[AnyContent] = UserAction { implicit req =>
    createExternalEventView(GsForms.externalEvent)
  }

  def doCreateExternalEvent(): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalEvent.bindFromRequest.fold(
      formWithErrors => createExternalEventView(formWithErrors),
      data => for {
        e <- externalEventRepo.create(data)
        _ <- ms.externalEventCreated(e).map(bus.publish)
      } yield Redirect(routes.CfpCtrl.createExternalCfp(e.id))
    )
  }

  private def createExternalEventView(form: Form[ExternalEvent.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    IO.pure(Ok(html.createExternalEvent(form)(extEventsBreadcrumb().add("Add event" -> routes.CfpCtrl.createExternalEvent()))))
  }

  def createExternalCfp(event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    createExternalCfpView(event, GsForms.externalCfp)
  }

  def doCreateExternalCfp(event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalCfp.bindFromRequest.fold(
      formWithErrors => createExternalCfpView(event, formWithErrors),
      data => (for {
        eventElt <- OptionT(externalEventRepo.find(event))
        cfpElt <- OptionT.liftF(externalCfpRepo.create(eventElt.id, data))
        _ <- OptionT.liftF(ms.externalCfpCreated(eventElt, cfpElt).map(bus.publish))
      } yield Redirect(routes.CfpCtrl.detailExt(cfpElt.id))).value.map(_.getOrElse(extEventNotFound(event)))
    )
  }

  private def createExternalCfpView(event: ExternalEvent.Id, form: Form[ExternalCfp.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      eventElt <- OptionT(externalEventRepo.find(event))
      res = Ok(html.createExternalCfp(eventElt, form)(extEventsBreadcrumb().add("Add CFP" -> routes.CfpCtrl.createExternalCfp(event))))
    } yield res).value.map(_.getOrElse(extEventNotFound(event)))
  }

  def detailExt(cfp: ExternalCfp.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      cfpElt <- OptionT(externalCfpRepo.findFull(cfp))
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
    editView(cfp, GsForms.externalCfpAndEvent)
  }

  def doEdit(cfp: ExternalCfp.Id): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalCfpAndEvent.bindFromRequest.fold(
      formWithErrors => editView(cfp, formWithErrors),
      data => (for {
        cfpElt <- OptionT(externalCfpRepo.findFull(cfp))
        _ <- OptionT.liftF(externalEventRepo.edit(cfpElt.event.id)(data.event))
        _ <- OptionT.liftF(externalCfpRepo.edit(cfp)(data.cfp))
        _ <- OptionT.liftF(ms.externalEventUpdated(cfpElt.event).map(bus.publish))
        _ <- OptionT.liftF(ms.externalCfpUpdated(cfpElt.event, cfpElt.cfp).map(bus.publish))
        res = Redirect(routes.CfpCtrl.detailExt(cfp)).flashing("success" -> "CFP updated")
      } yield res).value.map(_.getOrElse(extCfpNotFound(cfp)))
    )
  }

  private def editView(cfp: ExternalCfp.Id, form: Form[GsForms.ExternalCfpAndEvent])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      cfpElt <- OptionT(externalCfpRepo.findFull(cfp))
      b = breadcrumb(cfpElt).add("Edit" -> routes.CfpCtrl.edit(cfp))
      filledForm = if (form.hasErrors) form else form.fill(GsForms.ExternalCfpAndEvent(cfpElt.cfp.data, cfpElt.event.data))
    } yield Ok(html.edit(cfpElt, filledForm)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  def propose(cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    proposeForm(cfp, GsForms.talkLogged, params)
  }

  def doPropose(cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    GsForms.talkLogged.bindFromRequest.fold(
      formWithErrors => proposeForm(cfp, formWithErrors, params),
      data => req.secured.map { secured =>
        (for {
          cfpElt <- OptionT(cfpRepo.findRead(cfp))
          _ <- if (cfpElt.isActive(req.nowLDT)) OptionT.liftF(IO.pure(())) else OptionT.liftF(IO.raiseError(CustomException("Can't propose a talk, CFP is not open")))
          talkElt <- OptionT.liftF(talkRepo.create(data.talk)(secured))
          proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.proposal, talkElt.speakers)(secured))
          _ <- OptionT(ms.proposalCreated(cfpElt, proposalElt)(secured)).map(bus.publish)
          msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
        } yield Redirect(ProposalCtrl.detail(talkElt.slug, cfp)).flashing("success" -> msg)).value.map(_.getOrElse(publicCfpNotFound(cfp)))
      }.getOrElse {
        talkRepo.exists(data.talk.slug).flatMap {
          case true => proposeForm(cfp, GsForms.talkLogged.fill(data).withError("talk.slug", "This slug already exists, please choose an other one"), params)
          case false => proposeConnectForm(cfp, GsForms.talkSignup.bindFromRequest.discardingErrors, GsForms.talkLogin.bindFromRequest.discardingErrors)
        }
      }
    )
  }

  private def proposeForm(cfp: Cfp.Slug, form: Form[GsForms.TalkLogged], params: Page.Params)(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
    (for {
      cfpElt <- OptionT(cfpRepo.findIncoming(cfp))
      groupElt <- OptionT(groupRepo.find(cfpElt.group))
      talks <- OptionT.liftF(req.secured.map(talkRepo.listCurrent(cfpElt.id, params)(_)).getOrElse(IO.pure(Page.empty[Talk])))
      b = proposeTalkBreadcrumb(cfpElt)
    } yield Ok(html.propose(groupElt, cfpElt, talks, form)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  def doProposeSignup(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    ??? // break signup to avoid spam
    import cats.implicits._
    GsForms.talkSignup.bindFromRequest.fold(
      formWithErrors => proposeConnectForm(cfp, formWithErrors, GsForms.talkLogin.bindFromRequest),
      data => (for {
        cfpElt <- OptionT(cfpRepo.findRead(cfp))
        _ <- if (cfpElt.isActive(req.nowLDT)) OptionT.liftF(IO.pure(())) else OptionT.liftF(IO.raiseError(CustomException("Can't propose a talk, CFP is not open")))
        identity <- OptionT.liftF(authSrv.createIdentity(data.user))
        emailValidation <- OptionT.liftF(userRequestRepo.createAccountValidationRequest(identity.user.email, identity.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.signup(emailValidation, identity.user)))
        (auth, result) <- OptionT.liftF(authSrv.login(identity, data.user.rememberMe, _ => IO.pure(Redirect(ProposalCtrl.detail(data.talk.slug, cfp)))))
        secured = req.secured(identity, auth)
        talkElt <- OptionT.liftF(talkRepo.create(data.talk)(secured))
        proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.proposal, talkElt.speakers)(secured))
        _ <- OptionT(ms.proposalCreated(cfpElt, proposalElt)(secured)).map(bus.publish)
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
    GsForms.talkLogin.bindFromRequest.fold(
      formWithErrors => proposeConnectForm(cfp, GsForms.talkSignup.bindFromRequest, formWithErrors),
      data => (for {
        cfpElt <- OptionT(cfpRepo.findRead(cfp))
        _ <- if (cfpElt.isActive(req.nowLDT)) OptionT.liftF(IO.pure(())) else OptionT.liftF(IO.raiseError(CustomException("Can't propose a talk, CFP is not open")))
        identity <- OptionT.liftF(authSrv.getIdentity(data.user))
        (auth, result) <- OptionT.liftF(authSrv.login(identity, data.user.rememberMe, _ => IO.pure(Redirect(ProposalCtrl.detail(data.talk.slug, cfp)))))
        secured = req.secured(identity, auth)
        talkElt <- OptionT.liftF(talkRepo.create(data.talk)(secured))
        proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data.proposal, talkElt.speakers)(secured))
        _ <- OptionT(ms.proposalCreated(cfpElt, proposalElt)(secured)).map(bus.publish)
        msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
      } yield result.flashing("success" -> msg)).value.map(_.getOrElse(publicCfpNotFound(cfp))).recoverWith {
        case _: AccountValidationRequiredException => proposeConnectForm(cfp, loginData = Some(data), error = "You need to validate your account by clicking on the email validation link")
        case _: IdentityNotFoundException => proposeConnectForm(cfp, loginData = Some(data), error = "Wrong login or password")
        case _: InvalidPasswordException => proposeConnectForm(cfp, loginData = Some(data), error = "Wrong login or password")
        case NonFatal(e) => proposeConnectForm(cfp, loginData = Some(data), error = s"${e.getClass.getSimpleName}: ${e.getMessage}")
      }
    )
  }

  private def proposeConnectForm(cfp: Cfp.Slug, signupForm: Form[GsForms.TalkSignup], loginForm: Form[GsForms.TalkLogin])(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
    (for {
      cfpElt <- OptionT(cfpRepo.findIncoming(cfp))
      groupElt <- OptionT(groupRepo.find(cfpElt.group))
      b = proposeTalkBreadcrumb(cfpElt)
    } yield Ok(html.proposeConnect(groupElt, cfpElt, signupForm, loginForm)(b))).value.map(_.getOrElse(publicCfpNotFound(cfp)))
  }

  private def proposeConnectForm(cfp: Cfp.Slug, error: String, signupData: Option[GsForms.TalkSignup] = None, loginData: Option[GsForms.TalkLogin] = None)(implicit req: UserAwareReq[AnyContent]): IO[Result] =
    proposeConnectForm(
      cfp,
      signupData.map(GsForms.talkSignup.fill(_).withGlobalError(error)).getOrElse(GsForms.talkSignup.bindFromRequest.discardingErrors),
      loginData.map(GsForms.talkLogin.fill(_).withGlobalError(error)).getOrElse(GsForms.talkLogin.bindFromRequest.discardingErrors))
}

object CfpCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("CFPs" -> routes.CfpCtrl.list())

  def breadcrumb(cfp: Cfp): Breadcrumb =
    listBreadcrumb().add(cfp.name.value -> routes.CfpCtrl.detail(cfp.slug))

  def breadcrumb(cfp: ExternalCfp.Full): Breadcrumb =
    listBreadcrumb().add(cfp.event.name.value -> routes.CfpCtrl.detailExt(cfp.id))

  def proposeTalkBreadcrumb(cfp: Cfp): Breadcrumb =
    breadcrumb(cfp).add("Proposing a talk" -> routes.CfpCtrl.propose(cfp.slug))

  def extEventsBreadcrumb(): Breadcrumb =
    listBreadcrumb().add("Find event" -> routes.CfpCtrl.findExternalEvent())
}
