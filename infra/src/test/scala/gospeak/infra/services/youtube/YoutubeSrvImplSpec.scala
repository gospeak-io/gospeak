package gospeak.infra.services.youtube

import java.time.Instant

import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.scala.domain.Secret
import gospeak.libs.youtube.YoutubeClient

class YoutubeSrvImplSpec extends BaseSpec {
  val secret: Secret = Secret(
    """{}"""
      .stripMargin)
  val youtubeClient: YoutubeClient = YoutubeClient.create(secret)
  val youtubeSrvImpl: YoutubeSrvImpl = new YoutubeSrvImpl(youtubeClient)
  describe("videos") {
    it("should get all videos") {

      val now = Instant.now()
      val res = youtubeSrvImpl.videos("UCVelKVoLQIhwx9C2LWf-CDA")(now).unsafeRunSync()

      res shouldBe Seq()
    }
  }
}
