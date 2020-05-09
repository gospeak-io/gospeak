package gospeak.infra.services.youtube

import java.time.Instant

import gospeak.core.domain.Video.{ChannelRef, PlaylistRef}
import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Secret, Url}
import gospeak.libs.youtube.YoutubeClient

class YoutubeSrvImplSpec extends BaseSpec {
  // Add your google api key here
  private val secret: Secret = Secret(
    """{}"""
      .stripMargin)
  private val now: Instant = Instant.now()

  ignore("YoutubeSrvImpl") {
    val client = YoutubeClient.create(secret)
    val srv = new YoutubeSrvImpl(client)

    describe("channelVideos") {
      it("should get all videos by channel id") {
        val videos = srv.channelVideos(ChannelRef("UCVelKVoLQIhwx9C2LWf-CDA", "BreizhCamp"))(now).unsafeRunSync().get
        videos.length shouldBe 228
        videos.map(_.url) should contain allElementsOf Seq(
          "https://www.youtube.com/watch?v=NkH9WNE0OJc",
          "https://www.youtube.com/watch?v=s-7vdEXhxes").map(Url.Video.from(_).get)
      }
    }

    describe("playlistVideos") {
      it("should get all videos by playlist id") {
        val videos = srv.playlistVideos(PlaylistRef("PLv7xGPH0RMUTbzjcYSIMxGXA8RrQWdYGh", "Rennes DevOps"))(now).unsafeRunSync().get
        videos.map(_.url) shouldBe Seq(
          "https://www.youtube.com/watch?v=NkH9WNE0OJc",
          "https://www.youtube.com/watch?v=R60eYvVZ1q8",
          "https://www.youtube.com/watch?v=s-7vdEXhxes").map(Url.Video.from(_).get)
      }
    }
  }
}
