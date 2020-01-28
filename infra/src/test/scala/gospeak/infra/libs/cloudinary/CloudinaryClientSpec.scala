package gospeak.infra.libs.cloudinary

import gospeak.core.domain.utils.Creds
import gospeak.core.services.upload.UploadConf
import gospeak.infra.libs.cloudinary.domain.CloudinaryUploadRequest
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Secret
import org.scalatest.{FunSpec, Matchers}

class CloudinaryClientSpec extends FunSpec with Matchers {
  private val conf = UploadConf.Cloudinary("cloud-name", Some("my-preset"), Some(Creds("123456", Secret("abcdef"))))

  describe("CloudinaryClient") {
    describe("signRequest") {
      it("should work when creds are defined") {
        val params = Map("public_id" -> "test")
        new CloudinaryClient(conf).sign(params) shouldBe Right("1e325677ad0677efe2cfb25f8bb7d80d6e5c99a4")
        new CloudinaryClient(conf.copy(creds = None)).sign(params) shouldBe a[Left[_, _]]
        new CloudinaryClient(UploadConf.Url()).sign(params) shouldBe a[Left[_, _]]
      }
    }
    ignore("upload") {
      val validConf = UploadConf.Cloudinary("...", None, Some(Creds("...", Secret("..."))))
      val srv = new CloudinaryClient(validConf)
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
