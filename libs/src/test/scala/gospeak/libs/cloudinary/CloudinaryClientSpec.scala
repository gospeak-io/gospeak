package gospeak.libs.cloudinary

import gospeak.libs.cloudinary.domain.CloudinaryUploadRequest
import gospeak.libs.http.HttpClientImpl
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Creds, Secret}
import gospeak.libs.testingutils.BaseSpec

class CloudinaryClientSpec extends BaseSpec {
  private val conf = CloudinaryClient.Conf("cloud-name", Some("my-preset"), Some(Creds("123456", Secret("abcdef"))))
  private val http = new HttpClientImpl()

  describe("CloudinaryClient") {
    describe("signRequest") {
      it("should work when creds are defined") {
        val params = Map("public_id" -> "test")
        new CloudinaryClient(conf, http).sign(params) shouldBe Right("1e325677ad0677efe2cfb25f8bb7d80d6e5c99a4")
        new CloudinaryClient(conf.copy(creds = None), http).sign(params) shouldBe a[Left[_, _]]
      }
    }
    ignore("upload") {
      val validConf = CloudinaryClient.Conf("...", None, Some(Creds("...", Secret("..."))))
      val srv = new CloudinaryClient(validConf, http)
      val url = "https://image.flaticon.com/icons/png/512/168/168728.png"
      it("should upload an image") {
        val req = CloudinaryUploadRequest(
          file = url,
          folder = Some("test"),
          publicId = Some("test"))
        val res = srv.upload(req).unsafeRunSync().get
        res.public_id shouldBe "test/test"
        res.original_filename shouldBe "168728"
      }
    }
  }
}
