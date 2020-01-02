package fr.gospeak.web.api.swagger

import java.io.File

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.util.Try

class SwaggerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf) extends ApiCtrl(cc, silhouette, conf) {
  def getSpec: Action[AnyContent] = CustomUserAwareAction { implicit req =>
    val file = new File("web/app/fr/gospeak/web/api/swagger/gospeak.conf")
    val spec = ConfigFactory.parseFile(file).resolve()
    val json = spec.root().render(ConfigRenderOptions.concise())
    Try(Json.parse(json)).toIO.map(Ok(_))
  }

  def getUi: Action[AnyContent] = CustomUserAwareAction { implicit req =>
    IO.pure(Ok(html.swaggerUi()))
  }
}
