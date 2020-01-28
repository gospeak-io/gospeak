package gospeak.web.utils

import java.io.{File, FileNotFoundException}

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import play.api.libs.json.{JsValue, Json}
import gospeak.libs.scala.Extensions._

import scala.util.Try

object OpenApiUtils {
  val specPath = "app/gospeak/web/api/swagger/gospeak.openapi.conf"

  def loadSpec(): Try[JsValue] = {
    Try(new File(s"web/$specPath")).filterWith(_.exists(), f => new FileNotFoundException(f.getAbsolutePath))
      .orElse(Try(new File(specPath)).filterWith(_.exists(), f => new FileNotFoundException(f.getAbsolutePath)))
      .flatMap(loadSpec)
  }

  private def loadSpec(file: File): Try[JsValue] = {
    val spec = ConfigFactory.parseFile(file).resolve()
    val json = spec.root().render(ConfigRenderOptions.concise())
    Try(Json.parse(json))
  }
}
