package gospeak.libs.cloudinary

import gospeak.libs.cloudinary.CloudinaryJson._
import gospeak.libs.cloudinary.domain._
import gospeak.libs.scala.FileUtils
import gospeak.libs.testingutils.BaseSpec
import io.circe.parser.decode

class CloudinaryJsonSpec extends BaseSpec {
  private val basePath = FileUtils.adaptLocalPath("libs/src/test/resources/cloudinary")

  it("should parse upload response") {
    decode[CloudinaryUploadResponse](FileUtils.read(basePath + "/upload.json").get).toTry.get
  }
}
