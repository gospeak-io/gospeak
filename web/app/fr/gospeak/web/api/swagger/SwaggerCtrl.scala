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
  private val spec = SwaggerCtrl.loadSpec().get // to fail app on start

  def getSpec: Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(spec))
  }

  def getUi: Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.swaggerUi()))
  }
}

object SwaggerCtrl {
  def loadSpec(): Try[JsValue] = {
    val path = "app/fr/gospeak/web/api/swagger/gospeak.openapi.conf"
    val file1 = new File(s"web/$path")
    // current folder is "gospeak" in "sbt run" but "gospeak/web" in "sbt test"
    val file = if (file1.exists()) file1 else new File(path)
    val spec = ConfigFactory.parseFile(file).resolve()
    val json = spec.root().render(ConfigRenderOptions.concise())
    Try(Json.parse(json))
  }
}
