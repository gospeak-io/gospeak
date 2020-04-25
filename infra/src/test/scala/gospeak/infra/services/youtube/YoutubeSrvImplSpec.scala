package gospeak.infra.services.youtube

import gospeak.libs.scala.domain.Secret
import gospeak.libs.youtube.YoutubeClient
import org.scalatest.{FunSpec, Matchers}

class YoutubeSrvImplSpec extends FunSpec with Matchers {
  val secret: Secret = Secret(
    """{}"""
      .stripMargin)
  val youtubeClient: YoutubeClient = YoutubeClient.create(secret)
  val youtubeSrvImpl: YoutubeSrvImpl = new YoutubeSrvImpl(youtubeClient)
  describe("videos") {
    it("should get all videos") {

      val res = youtubeSrvImpl.videos("UCVelKVoLQIhwx9C2LWf-CDA").unsafeRunSync()

      res shouldBe Seq()
    }
  }
}
