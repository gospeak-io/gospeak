package fr.gospeak.web.auth

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import fr.gospeak.core.ApplicationConf
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.CustomException
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.routes.AuthCtrl
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.pages
import fr.gospeak.web.utils.UICtrl
import org.apache.http.auth.AuthenticationException
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

class SocialAuthCtrl(cc: ControllerComponents,
                     silhouette: Silhouette[CookieEnv],
                     val authSrv: AuthSrv,
                     envConf: ApplicationConf.Env)
  extends UICtrl(cc, silhouette) {

  def authenticate(providerID: String): Action[AnyContent] = Action.async { implicit request =>
    (for {
      provider <- authSrv.provider(providerID).toFuture(new AuthenticationException(s"Cannot authenticate with unexpected social provider $providerID"))
      result <- provider.authenticate().flatMap {
        case Left(result) => Future.successful(result)
        case Right(authInfo) =>
          for {
            profile <- provider.retrieveProfile(authInfo)
            socialProfile <- Try(profile.asInstanceOf[CommonSocialProfile]).mapFailure(_ => CustomException("Oops. Something went wrong. Cannot retrieve the social profile.")).toFuture
            authUser <- authSrv.createOrEdit(socialProfile).unsafeToFuture()
            redirect = Redirect(pages.user.routes.UserCtrl.index()).flashing("success" -> s"Hi ${authUser.user.name.value}, welcome to Gospeak!")
            authenticatorResult <- authSrv.login(authUser, rememberMe = true, redirect)
          } yield authenticatorResult
      }
    } yield result).recover {
      case CustomException(msg, _) => Redirect(AuthCtrl.login()).flashing("error" -> msg)
      case NonFatal(e) => Redirect(AuthCtrl.login()).flashing("error" -> e.getMessage)
    }
  }
}


