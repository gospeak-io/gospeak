package fr.gospeak.web.api

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.services.email.EmailSrv
import fr.gospeak.core.services.storage.AuthUserRequestRepo
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.ApiUser
import fr.gospeak.web.api.domain.utils.ApiResult
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.utils.ApiCtrl
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class AuthCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               conf: AppConf,
               userRequestRepo: AuthUserRequestRepo,
               authSrv: AuthSrv,
               emailSrv: EmailSrv) extends ApiCtrl(cc, silhouette, conf) {
  /*def signup(): Action[JsValue] = UserAwareActionJson[ApiUser.SignupPayload, Option[String]] { implicit req =>
    val data = req.body.asData
    for {
      user <- authSrv.createIdentity(data)
      emailValidation <- userRequestRepo.createAccountValidationRequest(user.user.email, user.user.id, req.now)
      _ <- emailSrv.send(Emails.signup(emailValidation, user.user))
      (_, result) <- authSrv.login(user, data.rememberMe, _ => IO.pure(Ok("")))
    } yield ApiResult.of(None) // TODO: error messages
  }

  def login(): Action[JsValue] = UserAwareActionJson[ApiUser.LoginPayload, Option[String]] { implicit req =>
    val data = req.body.asData
    for {
      user <- authSrv.getIdentity(data)
      (_, result) <- authSrv.login(user, data.rememberMe, _ => IO.pure(Ok("")))
    } yield ApiResult.of(None) // TODO: error messages
  }

  def logout(): Action[AnyContent] = UserAction[Option[String]] { implicit req =>
    authSrv.logout(IO.pure(Ok(""))).map(_ => ApiResult.of(None))
  }*/
}
