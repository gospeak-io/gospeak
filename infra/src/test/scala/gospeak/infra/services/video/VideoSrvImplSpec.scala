package gospeak.infra.services.video

import gospeak.core.services.video.YoutubeConf
import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Secret, Url}

class VideoSrvImplSpec extends BaseSpec {
  // Add your google api key here
  private val secret: Secret = Secret(
    """{}"""
      .stripMargin)

  ignore("YoutubeSrvImpl") {
    val srv = VideoSrvImpl.from(YoutubeConf(Some(secret))).get

    describe("getChannelId") {
      it("should get the channel id for youtube") {
        srv.getChannelId(Url.Videos.Channel.from("https://www.youtube.com/HumanTalksParis").get).unsafeRunSync() shouldBe "UCKFAwlgWiAB4vUpgnS63qog"
        srv.getChannelId(Url.Videos.Channel.from("https://www.youtube.com/c/HumanTalksParis").get).unsafeRunSync() shouldBe "UCVelKVoLQIhwx9C2LWf-CDA"
        srv.getChannelId(Url.Videos.Channel.from("https://www.youtube.com/user/BreizhCamp").get).unsafeRunSync() shouldBe "UCKFAwlgWiAB4vUpgnS63qog"
        srv.getChannelId(Url.Videos.Channel.from("https://www.youtube.com/channel/UCKFAwlgWiAB4vUpgnS63qog").get).unsafeRunSync() shouldBe "UCKFAwlgWiAB4vUpgnS63qog"
      }
    }
    describe("listVideos") {
      it("should list videos for YouTube user") {
        val url = Url.Videos.from("https://www.youtube.com/user/BreizhCamp").get
        val videos = srv.listVideos(url).unsafeRunSync()
        videos.length shouldBe 228
        videos.map(_.url) should contain allElementsOf Seq(
          "https://www.youtube.com/watch?v=NkH9WNE0OJc",
          "https://www.youtube.com/watch?v=s-7vdEXhxes").map(Url.Video.from(_).get)
      }
      it("should list videos for YouTube channel name") {
        val url = Url.Videos.from("https://www.youtube.com/HumanTalksParis").get
        val videos = srv.listVideos(url).unsafeRunSync()
        videos.length shouldBe 154
        videos.map(_.url) should contain allElementsOf Seq(
          "https://www.youtube.com/watch?v=JyNq_-OJ3dA",
          "https://www.youtube.com/watch?v=VVgdEBfFoF8").map(Url.Video.from(_).get)
      }
      it("should list videos for YouTube channel id") {
        val url = Url.Videos.from("https://www.youtube.com/channel/UCVelKVoLQIhwx9C2LWf-CDA").get
        val videos = srv.listVideos(url).unsafeRunSync()
        videos.length shouldBe 228
        videos.map(_.url) should contain allElementsOf Seq(
          "https://www.youtube.com/watch?v=NkH9WNE0OJc",
          "https://www.youtube.com/watch?v=s-7vdEXhxes").map(Url.Video.from(_).get)
      }
      it("should list videos for YouTube playlist id") {
        val url = Url.Videos.from("https://www.youtube.com/playlist?list=PLv7xGPH0RMUTbzjcYSIMxGXA8RrQWdYGh").get
        val videos = srv.listVideos(url).unsafeRunSync()
        videos.map(_.url) shouldBe Seq(
          "https://www.youtube.com/watch?v=NkH9WNE0OJc",
          "https://www.youtube.com/watch?v=R60eYvVZ1q8",
          "https://www.youtube.com/watch?v=s-7vdEXhxes").map(Url.Video.from(_).get)
      }
      ignore("should list videos for Vimeo channel") {
        val url = Url.Videos.from("https://vimeo.com/parisweb").get
        val videos = srv.listVideos(url).unsafeRunSync()
        videos.map(_.url) shouldBe Seq().map(Url.Video.from(_).get)
      }
      ignore("should list videos for Vimeo showcase") {
        val url = Url.Videos.from("https://vimeo.com/showcase/6597308").get
        val videos = srv.listVideos(url).unsafeRunSync()
        videos.map(_.url) shouldBe Seq().map(Url.Video.from(_).get)
      }
    }
  }
}
