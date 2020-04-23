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
    describe("Twitter") {
      it("should identify urls") {
        Seq(
          Url.from("https://twitter.com/gospeak_io").get -> "@gospeak_io",
          Url.from("https://twitter.com/gospeak_io/").get -> "@gospeak_io",
        ).foreach {
          case (u: Url.Twitter, handle) => u.handle shouldBe handle
          case (u, _) => fail(s"${u.value} is a Twitter url")
        }
      }
    }
    describe("LinkedIn") {
      it("should identify urls") {
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
    }
    describe("YouTube") {
      it("should identify urls") {
        Seq(
          Url.from("https://www.youtube.com/c/HumanTalksParis").get -> "HumanTalksParis",
          Url.from("https://www.youtube.com/channel/UCKFAwlgWiAB4vUpgnS63qog").get -> "UCKFAwlgWiAB4vUpgnS63qog",
          Url.from("https://www.youtube.com/channel/UCKFAwlgWiAB4vUpgnS63qog/videos").get -> "UCKFAwlgWiAB4vUpgnS63qog",
          Url.from("https://www.youtube.com/playlist?list=PLjkHSzY9VuL96Z9gpNrlwztU3w72zccCx").get -> "PLjkHSzY9VuL96Z9gpNrlwztU3w72zccCx",
          Url.from("https://www.youtube.com/watch?v=QfmVc9c_8Po&list=PLjkHSzY9VuL96Z9gpNrlwztU3w72zccCx").get -> "QfmVc9c_8Po",
          Url.from("https://www.youtube.com/watch?v=QfmVc9c_8Po").get -> "QfmVc9c_8Po",
          Url.from("https://youtu.be/QfmVc9c_8Po").get -> "QfmVc9c_8Po",
        ).foreach {
          case (u: Url.YouTube, handle) => u.handle shouldBe handle
          case (u, _) => fail(s"${u.value} is a YouTube url")
        }
      }
    }
    describe("Meetup") {
      it("should identify urls") {
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
    }
    describe("Github") {
      it("should identify urls") {
        Seq(
          Url.from("https://github.com/gospeak-io").get -> "gospeak-io",
          Url.from("https://github.com/gospeak-io/").get -> "gospeak-io",
          Url.from("https://github.com/gospeak-io/gospeak").get -> "gospeak",
        ).foreach {
          case (u: Url.Github, handle) => u.handle shouldBe handle
          case (u, _) => fail(s"${u.value} is a Github url")
        }
      }
    }
    describe("ParsedUrl") {
      it("should identify parts of the url") {
        ParsedUrl.from("https://loicknuchel.fr/") shouldBe Success(ParsedUrl(
          protocol = "https",
          host = "loicknuchel.fr",
          port = None,
          path = Seq(),
          parameters = Map(),
          fragment = None))
        ParsedUrl.from("http://www.loicknuchel.fr:8080/blog/test.html?page=1&test=true#header") shouldBe Success(ParsedUrl(
          protocol = "http",
          host = "www.loicknuchel.fr",
          port = Some(8080),
          path = Seq("blog", "test.html"),
          parameters = Map("page" -> "1", "test" -> "true"),
          fragment = Some("header")))
      }
      it("should compute domain") {
        val url = ParsedUrl(protocol = "https", host = "", port = None, path = Seq(), parameters = Map(), fragment = None)
        url.domain shouldBe ""
        url.copy(host = "loicknuchel.fr").domain shouldBe "loicknuchel.fr"
        url.copy(host = "blog.loicknuchel.fr").domain shouldBe "loicknuchel.fr"
        url.copy(host = "a.b.c.loicknuchel.fr").domain shouldBe "loicknuchel.fr"
      }
      it("should compute file") {
        val url = ParsedUrl(protocol = "https", host = "loicknuchel.fr", port = None, path = Seq(), parameters = Map(), fragment = None)
        url.file shouldBe None
        url.copy(path = Seq("blog")).file shouldBe None
        url.copy(path = Seq("index.html")).file shouldBe Some("index.html")
        url.copy(path = Seq("blog", "index.html")).file shouldBe Some("index.html")
      }
    }
  }
}
