package gospeak.infra.services

import cats.effect.IO
import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.http.FakeHttpClient
import gospeak.libs.http.HttpClient.Response
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Html, Url}

class EmbedSrvSpec extends BaseSpec {
  // private val srv = new EmbedSrv(new HttpClientImpl)
  private val srv = new EmbedSrv(new FakeHttpClient({
    case "https://fr.slideshare.net/loicknuchel/fp-is-coming" => IO.pure(Response(200, """<meta class="twitter_player" value="https://www.slideshare.net/slideshow/embed_code/key/jsCDUgaNXc0jlg" name="twitter:player" />""", Map()))
    case "https://speakerdeck.com/mickaelandrieu/10-minutes-pour-choisir-sa-licence-open-source" => IO.pure(Response(200, """<div data-id="553b9b48a6184f66866ad13b6216e357" data-ratio="1.77777777777778">""", Map()))
    case "https://talks.pixelastic.com/slides/memory-humantalks-2015" => IO.pure(Response(200, """<div class="reveal">""", Map()))
    case url => IO.raiseError(new Exception(s"Not handled url: $url"))
  }))

  describe("EmbedSrv") {
    it("should return a default embed when url is not recognized") {
      val url = Url.from("https://gospeak.io/speakers").get
      val embed = Html(
        """<div class="no-embed">
          |  Not embeddable: <a href="https://gospeak.io/speakers" target="_blank">https://gospeak.io/speakers</a>
          |</div>""".stripMargin)
      srv.embedCode(url).unsafeRunSync() shouldBe embed
    }
    describe("SyncService") {
      import EmbedSrv.SyncService._
      it("should generate embed code for YouTube") {
        val url = Url.from("https://www.youtube.com/watch?v=QfmVc9c_8Po").get
        val embed = Html("""<iframe width="560" height="315" src="https://www.youtube.com/embed/QfmVc9c_8Po" frameborder="0" allowfullscreen></iframe>""")
        YouTube.embed(url) shouldBe Some(embed)
        srv.embedCode(url).unsafeRunSync() shouldBe embed
      }
      it("should generate embed code for Dailymotion") {
        val url = Url.from("https://www.dailymotion.com/video/x7urqi0").get
        val embed = Html("""<iframe src="https://www.dailymotion.com/embed/video/x7urqi0" width="560" height="315" frameborder="0" allowfullscreen></iframe>""")
        Dailymotion.embed(url) shouldBe Some(embed)
        srv.embedCode(url).unsafeRunSync() shouldBe embed
      }
      it("should generate embed code for Vimeo") {
        val url = Url.from("https://vimeo.com/380320538").get
        val embed = Html("""<iframe src="https://player.vimeo.com/video/380320538" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>""")
        Vimeo.embed(url) shouldBe Some(embed)
        srv.embedCode(url).unsafeRunSync() shouldBe embed
      }
      it("should generate embed code for Google slides") {
        val url = Url.from("https://docs.google.com/presentation/d/1uKcynpm7MZuFQu4VNpSeSu1V7A_a-P1u_8ugdoWGQTU").get
        val embed = Html("""<iframe src="https://docs.google.com/presentation/d/1uKcynpm7MZuFQu4VNpSeSu1V7A_a-P1u_8ugdoWGQTU/embed" width="960" height="569" frameborder="0" allowfullscreen="true" mozallowfullscreen="true" webkitallowfullscreen="true"></iframe>""")
        GoogleSlides.embed(url) shouldBe Some(embed)
        srv.embedCode(url).unsafeRunSync() shouldBe embed
      }
      it("should generate embed code for Slides.com") {
        val url = Url.from("https://slides.com/parishumantalks/humantalks-fevrier-2020-manomano").get
        val embed = Html("""<iframe src="https:////slides.com/parishumantalks/humantalks-fevrier-2020-manomano/embed?style=light" width="576" height="420" scrolling="no" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>""")
        SlidesDotCom.embed(url) shouldBe Some(embed)
        srv.embedCode(url).unsafeRunSync() shouldBe embed
      }
      it("should generate embed code for a Pdf") {
        val url = Url.from("https://gospeak.io/test.pdf").get
        val embed = Html("""<iframe src="https://gospeak.io/test.pdf" width="576" height="420" scrolling="no" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>""")
        Pdf.embed(url) shouldBe Some(embed)
        srv.embedCode(url).unsafeRunSync() shouldBe embed
      }
    }
    describe("AsyncService") {
      import EmbedSrv.AsyncService._
      it("should generate embed code for SlideShare") {
        val url = Url.from("https://fr.slideshare.net/loicknuchel/fp-is-coming").get
        val embed = Html("""<iframe src="https://www.slideshare.net/slideshow/embed_code/key/jsCDUgaNXc0jlg" width="595" height="485" frameborder="0" marginwidth="0" marginheight="0" scrolling="no" style="border:1px solid #CCC; border-width:1px; margin-bottom:5px; max-width: 100%;" allowfullscreen></iframe>""")
        SlideShare.embed(url, """<meta class="twitter_player" value="https://www.slideshare.net/slideshow/embed_code/key/jsCDUgaNXc0jlg" name="twitter:player" />""") shouldBe Some(embed)
        srv.embedCode(url).unsafeRunSync() shouldBe embed
      }
      it("should generate embed code for SpeakerDeck") {
        val url = Url.from("https://speakerdeck.com/mickaelandrieu/10-minutes-pour-choisir-sa-licence-open-source").get
        val embed = Html("""<script async class="speakerdeck-embed" data-id="553b9b48a6184f66866ad13b6216e357" data-ratio="1.77777777777778" src="//speakerdeck.com/assets/embed.js"></script>""")
        SpeakerDeck.embed(url, """<div data-id="553b9b48a6184f66866ad13b6216e357" data-ratio="1.77777777777778">""") shouldBe Some(embed)
        srv.embedCode(url).unsafeRunSync() shouldBe embed
      }
      it("should generate embed code for Html slides") {
        val url = Url.from("https://talks.pixelastic.com/slides/memory-humantalks-2015").get
        val embed = Html("""<iframe src="https://talks.pixelastic.com/slides/memory-humantalks-2015" width="595" height="485" frameborder="0"></iframe>""")
        HtmlSlides.embed(url, """<div class="reveal">""") shouldBe Some(embed)
        srv.embedCode(url).unsafeRunSync() shouldBe embed
      }
    }
  }
}
