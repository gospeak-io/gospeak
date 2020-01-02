package fr.gospeak.web.api.swagger

import java.io.File

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.UICtrl
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.util.Try

class SwaggerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf) extends UICtrl(cc, silhouette, conf) {
  private val spec = SwaggerCtrl.loadSwaggerSpec().get

  def getSpec: Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(spec))
  }

  def getUi: Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.swaggerUi()))
  }
}

object SwaggerCtrl {
  def loadSwaggerSpec(): Try[JsValue] = {
    val file = new File("web/app/fr/gospeak/web/api/swagger/gospeak.conf")
    val spec = ConfigFactory.parseFile(file).resolve()
    val json = spec.root().render(ConfigRenderOptions.concise())
    Try(Json.parse(json))
  }
}
