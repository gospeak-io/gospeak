package fr.gospeak.web.api.swagger

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.{OpenApiUtils, UICtrl}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class SwaggerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf) extends UICtrl(cc, silhouette, conf) {
  private val spec = OpenApiUtils.loadSpec().get // to fail app on start

  def getSpec: Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(spec))
  }

  def getUi: Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.swaggerUi()))
  }
}
