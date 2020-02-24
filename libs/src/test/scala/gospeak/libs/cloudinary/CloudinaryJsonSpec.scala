package gospeak.libs.cloudinary

import gospeak.libs.cloudinary.CloudinaryJson._
import gospeak.libs.cloudinary.domain._
import gospeak.libs.scala.FileUtils
import io.circe.parser.decode
import org.scalatest.{FunSpec, Matchers}

class CloudinaryJsonSpec extends FunSpec with Matchers {
  private val basePath = Some("libs/src/test/resources/cloudinary").filter(FileUtils.exists).getOrElse("src/test/resources/cloudinary")

  it("should parse upload response") {
    decode[CloudinaryUploadResponse](FileUtils.read(basePath + "/upload.json").get).toTry.get
  }
}
