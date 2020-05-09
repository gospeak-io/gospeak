package gospeak.libs.scala.domain

import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Url.ParsedUrl
import gospeak.libs.testingutils.BaseSpec

import scala.util.Success

class UrlSpec extends BaseSpec {
  describe("Url") {
    it("should build only valid url") {
      Url.from("") shouldBe a[Left[_, _]]
      Url.from("test") shouldBe a[Left[_, _]]
      Url.from("example.com") shouldBe a[Left[_, _]]

      Url.from("http://example") shouldBe a[Right[_, _]]
      Url.from("http://example.fr") shouldBe a[Right[_, _]]
      Url.from("http://sub.domain.ext/path/to/file?p1=value&p2=other#fragment") shouldBe a[Right[_, _]]
    }
    it("should identify Twitter urls") {
      Seq(
        Url.from("https://twitter.com/gospeak_io").get -> "@gospeak_io",
        Url.from("https://twitter.com/gospeak_io/").get -> "@gospeak_io",
      ).foreach {
        case (u: Url.Twitter, handle) => u.handle shouldBe handle
        case (u, _) => fail(s"${u.value} is a Twitter url")
      }
    }
    it("should identify LinkedIn urls") {
      Seq(
        Url.from("https://www.linkedin.com/in/loicknuchel").get -> "loicknuchel",
        Url.from("http://fr.linkedin.com/in/loicknuchel/").get -> "loicknuchel",
        Url.from("https://www.linkedin.com/company/humantalksparis").get -> "humantalksparis",
        Url.from("https://www.linkedin.com/company/14816213/admin").get -> "14816213",
      ).foreach {
        case (u: Url.LinkedIn, handle) => u.handle shouldBe handle
        case (u, _) => fail(s"${u.value} is a LinkedIn url")
      }
    }
    it("should identify YouTube urls") {
      Seq(
        ("https://loicknuchel.fr", "", ""),
        ("https://www.youtube.com/user/BreizhCamp", "channel", "BreizhCamp"),
        ("https://www.youtube.com/c/HumanTalksParis", "channel", "HumanTalksParis"),
        ("https://www.youtube.com/channel/UCKFAwlgWiAB4vUpgnS63qog", "channel", "UCKFAwlgWiAB4vUpgnS63qog"),
        ("https://www.youtube.com/channel/UCKFAwlgWiAB4vUpgnS63qog//videos", "channel", "UCKFAwlgWiAB4vUpgnS63qog"),
        ("https://www.youtube.com/playlist?list=PLjkHSzY9VuL96Z9gpNrlwztU3w72zccCx", "playlist", "PLjkHSzY9VuL96Z9gpNrlwztU3w72zccCx"),
        ("https://www.youtube.com/watch?v=QfmVc9c_8Po&list=PLjkHSzY9VuL96Z9gpNrlwztU3w72zccCx", "video", "QfmVc9c_8Po"),
        ("https://www.youtube.com/watch?v=QfmVc9c_8Po", "video", "QfmVc9c_8Po"),
        ("https://youtu.be/QfmVc9c_8Po", "video", "QfmVc9c_8Po"),
        ("https://www.youtube.com/feed/subscriptions", "youtube", "subscriptions"),
      ).map { case (u, t, h) => (Url.from(u).get, t, h) }.foreach {
        case (u: Url.YouTube.Channel, "channel", handle) => u.handle shouldBe handle
        case (u: Url.YouTube.Playlist, "playlist", handle) => u.handle shouldBe handle
        case (u: Url.YouTube.Video, "video", handle) => u.handle shouldBe handle
        case (u: Url.YouTube, "youtube", handle) => u.handle shouldBe handle
        case (u, k, _) => if (k.nonEmpty) fail(s"Unexpected result for url: ${u.value}")
      }
    }
    it("should identify Vimeo urls") {
      Seq(
        ("https://loicknuchel.fr", "", ""),
        ("https://vimeo.com/parisweb", "channel", "parisweb"),
        ("https://vimeo.com/showcase/6597308", "showcase", "6597308"),
        ("https://vimeo.com/showcase/6597308/video/380320538", "video", "380320538"),
        ("https://vimeo.com/380320538", "video", "380320538"),
        ("https://vimeo.com/380320538#t=10s", "video", "380320538"),
        ("https://vimeo.com/fr/upgrade", "vimeo", "upgrade"),
      ).map { case (u, t, h) => (Url.from(u).get, t, h) }.foreach {
        case (u: Url.Vimeo.Channel, "channel", handle) => u.handle shouldBe handle
        case (u: Url.Vimeo.Showcase, "showcase", handle) => u.handle shouldBe handle
        case (u: Url.Vimeo.Video, "video", handle) => u.handle shouldBe handle
        case (u: Url.Vimeo, "vimeo", handle) => u.handle shouldBe handle
        case (u, k, _) => if (k.nonEmpty) fail(s"Unexpected result for url: ${u.value}")
      }
    }
    it("should identify Meetup urls") {
      Seq(
        Url.from("https://www.meetup.com/HumanTalks-Paris").get -> "HumanTalks-Paris",
        Url.from("https://www.meetup.com/HumanTalks-Paris/events/269180953/").get -> "269180953",
        Url.from("https://www.meetup.com/members/14321102").get -> "14321102",
        Url.from("https://www.meetup.com/fr-FR/members/14321102/").get -> "14321102",
        Url.from("https://www.meetup.com/fr-FR/members/14321102/?op=&memberId=14321102/").get -> "14321102",
      ).foreach {
        case (u: Url.Meetup, handle) => u.handle shouldBe handle
        case (u, _) => fail(s"${u.value} is a Meetup url")
      }
    }
    it("should identify Github urls") {
      Seq(
        Url.from("https://github.com/gospeak-io").get -> "gospeak-io",
        Url.from("https://github.com/gospeak-io/").get -> "gospeak-io",
        Url.from("https://github.com/gospeak-io/gospeak").get -> "gospeak",
      ).foreach {
        case (u: Url.Github, handle) => u.handle shouldBe handle
        case (u, _) => fail(s"${u.value} is a Github url")
      }
    }
    describe("ParsedUrl") {
      it("should identify parts of the url") {
        ParsedUrl.from("https://loicknuchel.fr/") shouldBe Success(ParsedUrl(
          protocol = "https",
          host = "loicknuchel.fr",
          port = None,
          path = List(),
          parameters = Map(),
          fragment = None))
        ParsedUrl.from("http://www.loicknuchel.fr:8080/blog/test.html?page=1&test=true#header") shouldBe Success(ParsedUrl(
          protocol = "http",
          host = "www.loicknuchel.fr",
          port = Some(8080),
          path = List("blog", "test.html"),
          parameters = Map("page" -> "1", "test" -> "true"),
          fragment = Some("header")))
      }
      it("should compute domain") {
        val url = ParsedUrl(protocol = "https", host = "", port = None, path = List(), parameters = Map(), fragment = None)
        url.domain shouldBe ""
        url.copy(host = "loicknuchel.fr").domain shouldBe "loicknuchel.fr"
        url.copy(host = "blog.loicknuchel.fr").domain shouldBe "loicknuchel.fr"
        url.copy(host = "a.b.c.loicknuchel.fr").domain shouldBe "loicknuchel.fr"
      }
      it("should compute file") {
        val url = ParsedUrl(protocol = "https", host = "loicknuchel.fr", port = None, path = List(), parameters = Map(), fragment = None)
        url.file shouldBe None
        url.copy(path = List("blog")).file shouldBe None
        url.copy(path = List("index.html")).file shouldBe Some("index.html")
        url.copy(path = List("blog", "index.html")).file shouldBe Some("index.html")
      }
    }
  }
}
