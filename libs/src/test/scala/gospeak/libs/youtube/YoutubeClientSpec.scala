package gospeak.libs.youtube

import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Secret, Url}
import gospeak.libs.testingutils.BaseSpec

class YoutubeClientSpec extends BaseSpec {
  // you should paste your key here for testing
  val secret: Secret = Secret(
    """{}""".stripMargin)

  ignore("YoutubeClient") {
    val client: YoutubeClient = YoutubeClient.create(secret).get

    describe("getChannelId") {
      it("should get channel id with user name") {
        val url = Url.YouTube.Channel.from("https://www.youtube.com/user/BreizhCamp").get
        val channel = client.getChannelId(url).unsafeRunSync().get
        channel shouldBe "UCVelKVoLQIhwx9C2LWf-CDA"
      }
      it("should get channel id with channel id") {
        val url = Url.YouTube.Channel.from("https://www.youtube.com/channel/UCKFAwlgWiAB4vUpgnS63qog").get
        val channel = client.getChannelId(url).unsafeRunSync().get
        channel shouldBe "UCKFAwlgWiAB4vUpgnS63qog"
      }
      it("should get channel id with custom url") {
        val url = Url.YouTube.Channel.from("https://www.youtube.com/HumanTalksParis").get
        val channel = client.getChannelId(url).unsafeRunSync().get
        channel shouldBe "UCKFAwlgWiAB4vUpgnS63qog"
      }
      it("should get channel id with custom url bis") {
        val url = Url.YouTube.Channel.from("https://www.youtube.com/c/HumanTalksParis").get
        val channel = client.getChannelId(url).unsafeRunSync().get
        channel shouldBe "UCKFAwlgWiAB4vUpgnS63qog"
      }
    }
    describe("getChannel") {
      it("should get channel with user name") {
        val url = Url.YouTube.Channel.from("https://www.youtube.com/user/BreizhCamp").get
        val channel = client.getChannel(url).unsafeRunSync().get
        channel.id shouldBe "UCVelKVoLQIhwx9C2LWf-CDA"
      }
      it("should get channel with channel id") {
        val url = Url.YouTube.Channel.from("https://www.youtube.com/channel/UCKFAwlgWiAB4vUpgnS63qog").get
        val channel = client.getChannel(url).unsafeRunSync().get
        channel.id shouldBe "UCKFAwlgWiAB4vUpgnS63qog"
      }
      it("should get channel with custom url") {
        val url = Url.YouTube.Channel.from("https://www.youtube.com/HumanTalksParis").get
        val channel = client.getChannel(url).unsafeRunSync().get
        channel.id shouldBe "UCKFAwlgWiAB4vUpgnS63qog"
      }
      it("should get channel with custom url bis") {
        val url = Url.YouTube.Channel.from("https://www.youtube.com/c/HumanTalksParis").get
        val channel = client.getChannel(url).unsafeRunSync().get
        channel.id shouldBe "UCKFAwlgWiAB4vUpgnS63qog"
      }
    }
    describe("getPlaylist") {
      it("should get playlist with id") {
        val url = Url.YouTube.Playlist.from("https://www.youtube.com/playlist?list=PLs13l-4BLe9cKAJEZJoh5u1UQMACty7Xi").get
        val playlist = client.getPlaylist(url).unsafeRunSync().get
        playlist.id shouldBe "PLs13l-4BLe9cKAJEZJoh5u1UQMACty7Xi"
        playlist.title shouldBe Some("Octobre 2019")
        playlist.channelTitle shouldBe Some("Human Talks Paris")
        playlist.items shouldBe Some(4)
      }
    }
    describe("listChannelVideos") {
      it("should list channel videos") {
        val videos = client.listChannelVideos(Url.Videos.Channel.Id("UCKFAwlgWiAB4vUpgnS63qog")).unsafeRunSync().get
        videos.items.length shouldBe 50
        videos.items.map(_.id) should contain allElementsOf List("JyNq_-OJ3dA", "5JjcxOfCSl0")
      }
      it("should fail on bad id") {
        val error = client.listChannelVideos(Url.Videos.Channel.Id("abc")).unsafeRunSync().left.get
        error.code shouldBe 400
      }
    }
    describe("listPlaylistVideos") {
      it("should list playlist videos") {
        val videos = client.listPlaylistVideos(Url.Videos.Playlist.Id("PLs13l-4BLe9cKAJEZJoh5u1UQMACty7Xi")).unsafeRunSync().get
        videos.items.length shouldBe 4
        videos.items.map(_.id) shouldBe List("7wf3NDUq1jw", "9J9ouo-VNao", "r3xdCYP9mVs", "AoVFq4rqv5g")
      }
      it("should fail on bad id") {
        val error = client.listPlaylistVideos(Url.Videos.Playlist.Id("abc")).unsafeRunSync().left.get
        error.code shouldBe 404
      }
    }
    describe("getVideoDetails") {
      it("should get video details for ids") {
        val videos = client.getVideoDetails(Seq("7wf3NDUq1jw", "9J9ouo-VNao", "r3xdCYP9mVs", "AoVFq4rqv5g").map(Url.Video.Id)).unsafeRunSync().get
        videos.length shouldBe 4
        videos.map(_.id) shouldBe List("7wf3NDUq1jw", "9J9ouo-VNao", "r3xdCYP9mVs", "AoVFq4rqv5g")
      }
      it("should ignore bad ids") {
        val videos = client.getVideoDetails(Seq("7wf3NDUq1jw", "abc").map(Url.Video.Id)).unsafeRunSync().get
        videos.length shouldBe 1
        videos.map(_.id) shouldBe List("7wf3NDUq1jw")
      }
    }
  }
}
