package gospeak.web.utils

import java.io.File

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import play.api.libs.json.{JsValue, Json}

import scala.util.Try

object OpenApiUtils {
  val specPath = "app/fr/gospeak/web/api/swagger/gospeak.openapi.conf"

  def loadSpec(): Try[JsValue] = {
    val file1 = new File(s"web/$specPath")
    // current folder is "gospeak" in "sbt run" but "gospeak/web" in "sbt test"
    val file = if (file1.exists()) file1 else new File(specPath)
    val spec = ConfigFactory.parseFile(file).resolve()
    val json = spec.root().render(ConfigRenderOptions.concise())
    Try(Json.parse(json))
  }
}
