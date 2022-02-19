package gospeak.infra.services.recaptcha

import gospeak.core.services.recaptcha.RecaptchaConf
import gospeak.infra.services.recaptcha.RecaptchaSrvImpl.Response
import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.http.HttpClientImpl
import gospeak.libs.scala.domain.Secret

import java.time.Instant

class RecaptchaSrvImplSpec extends BaseSpec {
  private val localConf = RecaptchaConf.Enabled("...", Secret("..."))
  private val client = new RecaptchaSrvImpl(localConf, new HttpClientImpl)

  describe("RecaptchaSrvImpl") {
    ignore("should call Recaptcha") {
      client.validate(Some(Secret("..."))).unsafeRunSync()
    }
    it("should decode json") {
      client.parse("""{"success": false, "error-codes": ["invalid-input-response"]}""") shouldBe
        Right(Response(success = false, None, None, List("invalid-input-response")))
      client.parse("""{"success": true, "challenge_ts": "2022-02-19T11:21:43Z", "hostname": "localhost"}""") shouldBe
        Right(Response(success = true, Some(Instant.parse("2022-02-19T11:21:43Z")), Some("localhost"), List()))
    }
  }
}
