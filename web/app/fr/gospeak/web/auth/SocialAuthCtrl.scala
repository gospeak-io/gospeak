package fr.gospeak.web.auth

import cats.effect.{ContextShift, IO}
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

import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.control.NonFatal

class SocialAuthCtrl(cc: ControllerComponents,
                     silhouette: Silhouette[CookieEnv],
                     val authSrv: AuthSrv,
                     env: ApplicationConf.Env,
                     envConf: ApplicationConf.Env)
  extends UICtrl(cc, silhouette, env) {

  implicit private val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def authenticate(providerID: String): Action[AnyContent] = UserAwareActionIO { implicit request =>
    (for {
      provider <- authSrv.provider(providerID).toIO(new AuthenticationException(s"Cannot authenticate with unexpected social provider $providerID"))
      res <- IO.fromFuture(IO(provider.authenticate()))
      result <- res match {
        case Left(result) => IO.pure(result)
        case Right(authInfo) =>
          for {
            profile <- IO.fromFuture(IO(provider.retrieveProfile(authInfo)))
            socialProfile <- Try(profile.asInstanceOf[CommonSocialProfile])
              .mapFailure(_ => CustomException("Oops. Something went wrong. Cannot retrieve the social profile.")).toIO
            authUser <- authSrv.createOrEdit(socialProfile)
            redirection = Redirect(pages.user.routes.UserCtrl.index()).flashing("success" -> s"Hi ${authUser.user.name.value}, welcome to Gospeak!")
            (_, authenticatorResult) <- authSrv.login(authUser, rememberMe = true, redirection)
          } yield authenticatorResult
      }
    } yield result).recover {
      case CustomException(msg, _) => Redirect(AuthCtrl.login()).flashing("error" -> msg)
      case NonFatal(e) => Redirect(AuthCtrl.login()).flashing("error" -> e.getMessage)
    }
  }
}


