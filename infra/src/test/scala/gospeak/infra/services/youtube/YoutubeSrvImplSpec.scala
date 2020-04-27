package gospeak.infra.services.youtube

import java.time.Instant

import gospeak.core.domain.Video
import gospeak.core.domain.Video.{ChannelRef, PlaylistRef}
import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.scala.domain.{Secret, Url}
import gospeak.libs.youtube.YoutubeClient
import org.scalatest.Ignore

import scala.concurrent.duration.FiniteDuration

class YoutubeSrvImplSpec extends BaseSpec {
  // Add your google api key here
  val secret: Secret = Secret(
    """{}"""
      .stripMargin)
  val youtubeClient: YoutubeClient = YoutubeClient.create(secret)
  val youtubeSrvImpl: YoutubeSrvImpl = new YoutubeSrvImpl(youtubeClient)

  val now: Instant = Instant.now()
  val expectedVideos = Seq(Video(Url.Video.from("https://www.youtube.com/watch?v=NkH9WNE0OJc").right.get,
    ChannelRef("UCVelKVoLQIhwx9C2LWf-CDA", "UCVelKVoLQIhwx9C2LWf-CDA")
    , None,
    "[Rennes DevOps] Quels choix d'hébergement possibles pour des données de santé ?",
    "[Rennes DevOps] Quels choix d'hébergement possibles pour des données de santé ?",
    List(),
    Instant.parse("2018-11-08T11:28:57Z"),
    FiniteDuration(4200000000000L, "nanoseconds"),
    "fr",
    606,
    13,
    0,
    0,
    now),
    Video(Url.Video.from("https://www.youtube.com/watch?v=R60eYvVZ1q8").right.get,
      ChannelRef("UCVelKVoLQIhwx9C2LWf-CDA", "UCVelKVoLQIhwx9C2LWf-CDA"),
      None,
      "[Rennes devops] Coding dojo Ansible #2", "[Rennes devops] Coding dojo Ansible #2",
      List(),
      Instant.parse("2019-05-15T11:09:12Z"),
      FiniteDuration(4066000000000L, "nanoseconds"),
      "fr",
      82,
      1,
      0,
      0,
      now),
    Video(Url.Video.from(("https://www.youtube.com/watch?v=s-7vdEXhxes")).right.get,
      ChannelRef("UCVelKVoLQIhwx9C2LWf-CDA", "UCVelKVoLQIhwx9C2LWf-CDA"),
      None,
      "[Rennes DevOps - BreizhJUG] Sécurisez vos applications avec Keycloak - Jérôme Marchand",
      "[Rennes DevOps - BreizhJUG] Sécurisez vos applications avec Keycloak - Jérôme Marchand",
      List(), Instant.parse("2019-09-20T12:37:15Z"),
      FiniteDuration(5618000000000L, "nanoseconds"),
      "fr", 1589, 16, 1, 0, now))
  ignore("channelVideos") {
    it("should get all videos by channel id") {

      val res = youtubeSrvImpl.channelVideos(ChannelRef("UCVelKVoLQIhwx9C2LWf-CDA", ""))(now).unsafeRunSync()

      res.right.get.length shouldBe 228
      res.right.get should contain allElementsOf (expectedVideos)
    }
  }

  // you may update your expectedVideos data, because likes/comments/dislikes are subjects to continual updates
  ignore("playlistVideos") {
    it("should get all videos by playlist id") {

      val res = youtubeSrvImpl.playlistVideos(PlaylistRef("PLv7xGPH0RMUTbzjcYSIMxGXA8RrQWdYGh", ""))(now).unsafeRunSync()
      res.right.get shouldBe expectedVideos
    }

  }
}
