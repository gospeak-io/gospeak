package gospeak.infra.services

import gospeak.infra.services.ScraperSrv.parseElement
import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.http.HttpClient.Response
import gospeak.libs.http.{FakeHttpClient, HttpClientImpl}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.FileUtils
import gospeak.libs.scala.domain.PageData.{FullItem, RowItem, Size, SizedItem}
import gospeak.libs.scala.domain.Url
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.{Decoder, Encoder}
import org.jsoup.Jsoup

class ScraperSrvSpec extends BaseSpec {
  private val path = FileUtils.adaptLocalPath("infra/src/test/resources/scraper")
  private val encoder: Encoder[Response] = deriveEncoder[Response]
  private val decoder: Decoder[Response] = deriveDecoder[Response]
  private val urlToPath = Map(
    "https://www.youtube.com/watch?v=ITdCVM3YYdc" -> "youtube-ITdCVM3YYdc.json")
  private val fake = new FakeHttpClient(url => urlToPath.get(url).map(read).toIO(new Exception(s"no path for $url")))
  private val srv = new ScraperSrv(fake)

  describe("ScraperSrv") {
    it("should parse tags to items") {
      List(
        ("title", "<title>Comment Animal Crossing</title>", FullItem("html:title", "Comment Animal Crossing", Map())),
        ("meta", "<meta charset=\"utf-8\" />", FullItem("charset", "utf-8", Map())),
        ("meta", "<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />", FullItem("X-UA-Compatible", "IE=edge", Map())),
        ("meta", "<meta http-equiv=\"origin-trial\" data-feature=\"Web Components V0\" data-expires=\"2020-10-23\" content=\"AhbmRDASY7NuOZD9cFMgQihZ+fQ==\">", FullItem("origin-trial", "AhbmRDASY7NuOZD9cFMgQihZ+fQ==", Map("data-feature" -> "Web Components V0", "data-expires" -> "2020-10-23"))),
        ("meta", "<meta id=\"globalTrackingUrl\" content=\"https://www.linkedin.com/li/track\">", FullItem("globalTrackingUrl", "https://www.linkedin.com/li/track", Map())),
        ("meta", "<meta name=\"title\" content=\"Comment Animal Crossing est devenu\">", FullItem("title", "Comment Animal Crossing est devenu", Map())),
        ("meta", "<meta property=\"og:site_name\" content=\"YouTube\">", FullItem("og:site_name", "YouTube", Map())),
        ("meta", "<meta itemprop=\"name\" content=\"Comment Animal Crossing est devenu\">", FullItem("name", "Comment Animal Crossing est devenu", Map())),
        ("meta", "<meta content=\"2490221586\" class=\"fb_og_meta\" property=\"fb:app_id\" name=\"fb_app_id\">", FullItem("fb:app_id", "2490221586", Map("class" -> "fb_og_meta", "name" -> "fb_app_id"))),
        ("meta", "<meta content=\"test\">", FullItem("", "", Map("content" -> "test"))),
        ("link", "<link rel=\"image_src\" href=\"https://i.ytimg.com/vi/ITdCVM3YYdc/maxresdefault.jpg\">", FullItem("image_src", "https://i.ytimg.com/vi/ITdCVM3YYdc/maxresdefault.jpg", Map())),
        ("link", "<link rel=\"shortcut icon\" href=\"https://s.ytimg.com/yts/img/favicon-vfl8qSV2F.ico\" type=\"image/x-icon\" >", FullItem("shortcut icon", "https://s.ytimg.com/yts/img/favicon-vfl8qSV2F.ico", Map("type" -> "image/x-icon"))),
      ).foreach { case (name, tag, result) => parseElement(Jsoup.parse(tag).selectFirst(name)) shouldBe result }
    }
    ignore("should save an http result") {
      val url = "https://www.youtube.com/watch?v=ITdCVM3YYdc"
      val file = "youtube-ITdCVM3YYdc.json"
      val res = new HttpClientImpl().get(url).unsafeRunSync()
      write(file, res)
      val r = read(file)
      r shouldBe res
    }
    it("should extract metas from youtube page") {
      val url = Url.from("https://www.youtube.com/watch?v=ITdCVM3YYdc").get
      val data = srv.fetchMetas(url).unsafeRunSync()
      // data.metas.foreach { case (k, v) => if (v.length > 1) {println(s"  - $k"); v.foreach { case (v, m) => println(s"    - $v $m") }} else println(s"  - $k: ${v.head._1} ${v.head._2}")}
      data.url shouldBe url
      data.title shouldBe Some("Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie")
      data.description shouldBe Some("Construire une audience qui achète : http://marketingmania.fr/audience Avec ses animaux mignons et ses graphismes colorés, Animal Crossing ressemble à un jeu...")
      data.image shouldBe Some("https://i.ytimg.com/vi/ITdCVM3YYdc/maxresdefault.jpg")
      data.icons shouldBe List(
        SizedItem("https://s.ytimg.com/yts/img/favicon_144-vfliLAfaB.png", Some(Size(144, 144))),
        SizedItem("https://s.ytimg.com/yts/img/favicon_96-vflW9Ec0w.png", Some(Size(96, 96))),
        SizedItem("https://s.ytimg.com/yts/img/favicon_48-vflVjB_Qk.png", Some(Size(48, 48))),
        SizedItem("https://s.ytimg.com/yts/img/favicon_32-vflOogEID.png", Some(Size(32, 32))),
        SizedItem("https://s.ytimg.com/yts/img/favicon-vfl8qSV2F.ico", None))
      data.color shouldBe Some("#ff0000")
      data.keywords shouldBe List("marketing mania", "stan leloup", "stanislas leloup", "comment convaincre", "influence", "manipulation", "influence et manipulation", "marketing", "webmarketing")
      data.canonical shouldBe Some("https://www.youtube.com/watch?v=ITdCVM3YYdc")
      data.siteName shouldBe Some("YouTube")

      data.metas shouldBe Map(
        "alternate" -> List(
          RowItem("https://m.youtube.com/watch?v=ITdCVM3YYdc", Map("media" -> "handheld")),
          RowItem("https://m.youtube.com/watch?v=ITdCVM3YYdc", Map("media" -> "only screen and (max-width: 640px)")),
          RowItem("android-app://com.google.android.youtube/http/www.youtube.com/watch?v=ITdCVM3YYdc", Map()),
          RowItem("ios-app://544007664/vnd.youtube/www.youtube.com/watch?v=ITdCVM3YYdc", Map()),
          RowItem("http://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DITdCVM3YYdc", Map("type" -> "application/json+oembed", "title" -> "Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie")),
          RowItem("http://www.youtube.com/oembed?format=xml&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DITdCVM3YYdc", Map("type" -> "text/xml+oembed", "title" -> "Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie"))),
        "al:android:app_name" -> List(RowItem("YouTube", Map())),
        "al:android:package" -> List(RowItem("com.google.android.youtube", Map())),
        "al:android:url" -> List(RowItem("vnd.youtube://www.youtube.com/watch?v=ITdCVM3YYdc&feature=applinks", Map())),
        "al:ios:app_name" -> List(RowItem("YouTube", Map())),
        "al:ios:app_store_id" -> List(RowItem("544007664", Map())),
        "al:ios:url" -> List(RowItem("vnd.youtube://www.youtube.com/watch?v=ITdCVM3YYdc&feature=applinks", Map())),
        "al:web:url" -> List(RowItem("https://www.youtube.com/watch?v=ITdCVM3YYdc&feature=applinks", Map())),
        "canonical" -> List(RowItem("https://www.youtube.com/watch?v=ITdCVM3YYdc", Map())),
        "Date" -> List(RowItem("Fri, 31 Jul 2020 11:36:07 GMT", Map())),
        "description" -> List(RowItem("Construire une audience qui achète : http://marketingmania.fr/audience Avec ses animaux mignons et ses graphismes colorés, Animal Crossing ressemble à un jeu...", Map())),
        "Expires" -> List(RowItem("Tue, 27 Apr 1971 19:44:06 GMT", Map())),
        "fb:app_id" -> List(RowItem("87741124305", Map())),
        "html:title" -> List(RowItem("Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie - YouTube", Map())),
        "icon" -> List(
          RowItem("https://s.ytimg.com/yts/img/favicon_32-vflOogEID.png", Map("sizes" -> "32x32")),
          RowItem("https://s.ytimg.com/yts/img/favicon_48-vflVjB_Qk.png", Map("sizes" -> "48x48")),
          RowItem("https://s.ytimg.com/yts/img/favicon_96-vflW9Ec0w.png", Map("sizes" -> "96x96")),
          RowItem("https://s.ytimg.com/yts/img/favicon_144-vfliLAfaB.png", Map("sizes" -> "144x144"))),
        "image_src" -> List(RowItem("https://i.ytimg.com/vi/ITdCVM3YYdc/maxresdefault.jpg", Map())),
        "keywords" -> List(RowItem("marketing mania, stan leloup, stanislas leloup, comment convaincre, influence, manipulation, influence et manipulation, marketing, webmarketing, techniques d...", Map())),
        "og:description" -> List(RowItem("Construire une audience qui achète : http://marketingmania.fr/audience Avec ses animaux mignons et ses graphismes colorés, Animal Crossing ressemble à un jeu...", Map())),
        "og:image" -> List(RowItem("https://i.ytimg.com/vi/ITdCVM3YYdc/maxresdefault.jpg", Map())),
        "og:image:height" -> List(RowItem("720", Map())),
        "og:image:width" -> List(RowItem("1280", Map())),
        "og:site_name" -> List(RowItem("YouTube", Map())),
        "og:title" -> List(RowItem("Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie", Map())),
        "og:type" -> List(RowItem("video.other", Map())),
        "og:url" -> List(RowItem("https://www.youtube.com/watch?v=ITdCVM3YYdc", Map())),
        "og:video:height" -> List(RowItem("720", Map())), "og:video:url" -> List(RowItem("https://www.youtube.com/embed/ITdCVM3YYdc", Map())),
        "og:video:secure_url" -> List(RowItem("https://www.youtube.com/embed/ITdCVM3YYdc", Map())),
        "og:video:tag" -> List(RowItem("marketing mania", Map()), RowItem("stan leloup", Map()), RowItem("stanislas leloup", Map()), RowItem("comment convaincre", Map()), RowItem("influence", Map()), RowItem("manipulation", Map()), RowItem("influence et manipulation", Map()), RowItem("marketing", Map()), RowItem("webmarketing", Map()), RowItem("techniques de persuasion", Map()), RowItem("persuasion", Map()), RowItem("animal crossing", Map()), RowItem("animal crossing new horizons", Map()), RowItem("animal crossing new horizons switch", Map()), RowItem("psychologie animal crossing", Map()), RowItem("domestic cosy", Map()), RowItem("domesticité cosy", Map()), RowItem("premium mediocre", Map()), RowItem("venkatesh rao", Map())),
        "og:video:type" -> List(RowItem("text/html", Map())),
        "og:video:width" -> List(RowItem("1280", Map())),
        "origin-trial" -> List(
          RowItem("AhbmRDASY7NuOZD9cFMgQihZ+mQpCwa8WTGdTx82vSar9ddBQbziBfZXZg+ScofvEZDdHQNCEwz4yM7HjBS9RgkAAABneyJvcmlnaW4iOiJodHRwczovL3lvdXR1YmUuY29tOjQ0MyIsImZlYXR1cmUiOiJXZWJDb21wb25lbnRzVjAiLCJleHBpcnkiOjE2MDM0ODY4NTYsImlzU3ViZG9tYWluIjp0cnVlfQ==", Map("data-feature" -> "Web Components V0", "data-expires" -> "2020-10-23")),
          RowItem("Av2+1qfUp3MwEfAFcCccykS1qFmvLiCrMZ//pHQKnRZWG9dldVo8HYuJmGj2wZ7nDg+xE4RQMQ+Ku1zKM3PvYAIAAABmeyJvcmlnaW4iOiJodHRwczovL2dvb2dsZS5jb206NDQzIiwiZmVhdHVyZSI6IldlYkNvbXBvbmVudHNWMCIsImV4cGlyeSI6MTYwMzgzNjc3MiwiaXNTdWJkb21haW4iOnRydWV9", Map("data-feature" -> "Web Components V0", "data-expires" -> "2020-10-27")),
          RowItem("AixUK+8UEShlt6+JX1wy9eg+XL+eV5PYSEDPH3C90JNVbIkE1Rg1FyVUfu2bZ/y6Pm1xbPLzuwHYHjv4uKPNnA4AAABqeyJvcmlnaW4iOiJodHRwczovL2dvb2dsZXByb2QuY29tOjQ0MyIsImZlYXR1cmUiOiJXZWJDb21wb25lbnRzVjAiLCJleHBpcnkiOjE2MTAwNjQ0MjMsImlzU3ViZG9tYWluIjp0cnVlfQ==", Map("data-feature" -> "Web Components V0", "data-expires" -> "2021-01-08")),
          RowItem("AhHpq2nUT6fqP0Kmkq49EWIcl2P1LK1ceU05BoiVnWi8ZIWDdmX/kMwL+ZtuC3oIf0tns8XnO5fm946JEzPVEwgAAABqeyJvcmlnaW4iOiJodHRwczovL2MuZ29vZ2xlcnMuY29tOjQ0MyIsImZlYXR1cmUiOiJXZWJDb21wb25lbnRzVjAiLCJleHBpcnkiOjE2MTIyMjM5OTksImlzU3ViZG9tYWluIjp0cnVlfQ==", Map("data-feature" -> "Web Components V0", "data-expires" -> "2021-03-09"))),
        "shortcut icon" -> List(RowItem("https://s.ytimg.com/yts/img/favicon-vfl8qSV2F.ico", Map("type" -> "image/x-icon"))),
        "theme-color" -> List(RowItem("#ff0000", Map())),
        "title" -> List(RowItem("Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie", Map())),
        "twitter:app:id:googleplay" -> List(RowItem("com.google.android.youtube", Map())),
        "twitter:app:id:ipad" -> List(RowItem("544007664", Map())),
        "twitter:app:id:iphone" -> List(RowItem("544007664", Map())),
        "twitter:app:name:googleplay" -> List(RowItem("YouTube", Map())),
        "twitter:app:name:ipad" -> List(RowItem("YouTube", Map())),
        "twitter:app:name:iphone" -> List(RowItem("YouTube", Map())),
        "twitter:app:url:googleplay" -> List(RowItem("https://www.youtube.com/watch?v=ITdCVM3YYdc", Map())),
        "twitter:app:url:ipad" -> List(RowItem("vnd.youtube://www.youtube.com/watch?v=ITdCVM3YYdc&feature=applinks", Map())),
        "twitter:app:url:iphone" -> List(RowItem("vnd.youtube://www.youtube.com/watch?v=ITdCVM3YYdc&feature=applinks", Map())),
        "twitter:card" -> List(RowItem("player", Map())),
        "twitter:description" -> List(RowItem("Construire une audience qui achète : http://marketingmania.fr/audience Avec ses animaux mignons et ses graphismes colorés, Animal Crossing ressemble à un jeu...", Map())),
        "twitter:image" -> List(RowItem("https://i.ytimg.com/vi/ITdCVM3YYdc/maxresdefault.jpg", Map())),
        "twitter:player" -> List(RowItem("https://www.youtube.com/embed/ITdCVM3YYdc", Map())),
        "twitter:player:height" -> List(RowItem("720", Map())),
        "twitter:player:width" -> List(RowItem("1280", Map())),
        "twitter:site" -> List(RowItem("@youtube", Map())),
        "twitter:title" -> List(RowItem("Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie", Map())),
        "twitter:url" -> List(RowItem("https://www.youtube.com/watch?v=ITdCVM3YYdc", Map())),
        "X-UA-Compatible" -> List(RowItem("IE=edge", Map())))

      /*
        - title
          - <meta name="title"         content="Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie">
          - <meta property="og:title"  content="Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie">
          - <meta name="twitter:title" content="Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie">
          - <title>Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie - YouTube</title>
        - description
          - <meta name="description"         content="Construire une audience qui achète : http://marketingmania.fr/audience Avec ses animaux mignons et ses graphismes colorés, Animal Crossing ressemble à un jeu...">
          - <meta property="og:description"  content="Construire une audience qui achète : http://marketingmania.fr/audience Avec ses animaux mignons et ses graphismes colorés, Animal Crossing ressemble à un jeu...">
          - <meta name="twitter:description" content="Construire une audience qui achète : http://marketingmania.fr/audience Avec ses animaux mignons et ses graphismes colorés, Animal Crossing ressemble à un jeu...">
        - image
          - <link rel="image_src"         href="https://i.ytimg.com/vi/ITdCVM3YYdc/maxresdefault.jpg">
          - <meta property="og:image"  content="https://i.ytimg.com/vi/ITdCVM3YYdc/maxresdefault.jpg"> / <meta property="og:image:width" content="1280"> / <meta property="og:image:height" content="720">
          - <meta name="twitter:image" content="https://i.ytimg.com/vi/ITdCVM3YYdc/maxresdefault.jpg">
        - site icon
          - <link rel="shortcut icon"     href="https://s.ytimg.com/yts/img/favicon-vfl8qSV2F.ico" type="image/x-icon" >
          - <link rel="icon"              href="https://s.ytimg.com/yts/img/favicon_32-vflOogEID.png" sizes="32x32" >
          - <link rel="icon"              href="https://s.ytimg.com/yts/img/favicon_48-vflVjB_Qk.png" sizes="48x48" >
          - <link rel="icon"              href="https://s.ytimg.com/yts/img/favicon_96-vflW9Ec0w.png" sizes="96x96" >
          - <link rel="icon"              href="https://s.ytimg.com/yts/img/favicon_144-vfliLAfaB.png" sizes="144x144" >
        - color
          - <meta name="theme-color" content="#ff0000">
        - keywords
          - <meta name="keywords" content="marketing mania, stan leloup, stanislas leloup, comment convaincre, influence, manipulation, influence et manipulation, marketing, webmarketing, techniques d...">
        - links
          - <link rel="canonical"                                href="https://www.youtube.com/watch?v=ITdCVM3YYdc">
          - <meta property="og:url"                           content="https://www.youtube.com/watch?v=ITdCVM3YYdc">
          - <meta name="twitter:url"                          content="https://www.youtube.com/watch?v=ITdCVM3YYdc">
          - <meta name="twitter:app:url:googleplay"           content="https://www.youtube.com/watch?v=ITdCVM3YYdc">
          - <meta property="al:web:url"                       content="https://www.youtube.com/watch?v=ITdCVM3YYdc&amp;feature=applinks">
          - <link rel="alternate"                                href="https://m.youtube.com/watch?v=ITdCVM3YYdc" media="handheld">
          - <link rel="alternate"                                href="https://m.youtube.com/watch?v=ITdCVM3YYdc" media="only screen and (max-width: 640px)">
          - <meta name="twitter:player"                       content="https://www.youtube.com/embed/ITdCVM3YYdc"> / <meta name="twitter:player:width" content="1280"> / <meta name="twitter:player:height" content="720">
          - <meta property="og:video:url"                     content="https://www.youtube.com/embed/ITdCVM3YYdc">
          - <meta property="og:video:secure_url"              content="https://www.youtube.com/embed/ITdCVM3YYdc">
          - <link rel="alternate" type="application/json+oembed" href="http://www.youtube.com/oembed?format=json&amp;url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DITdCVM3YYdc" title="Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie">
          - <link rel="alternate" type="text/xml+oembed"         href="http://www.youtube.com/oembed?format=xml&amp;url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DITdCVM3YYdc"  title="Comment Animal Crossing est devenu le gagnant “surprise” de la pandémie">
          - <meta name="twitter:app:url:ipad"                 content="vnd.youtube://www.youtube.com/watch?v=ITdCVM3YYdc&amp;feature=applinks">
          - <meta name="twitter:app:url:iphone"               content="vnd.youtube://www.youtube.com/watch?v=ITdCVM3YYdc&amp;feature=applinks">
          - <meta property="al:ios:url"                       content="vnd.youtube://www.youtube.com/watch?v=ITdCVM3YYdc&amp;feature=applinks">
          - <meta property="al:android:url"                   content="vnd.youtube://www.youtube.com/watch?v=ITdCVM3YYdc&amp;feature=applinks">
          - <link rel="alternate"                                href="android-app://com.google.android.youtube/http/www.youtube.com/watch?v=ITdCVM3YYdc">
          - <link rel="alternate"                                href="ios-app://544007664/vnd.youtube/www.youtube.com/watch?v=ITdCVM3YYdc">
        - site
          - <meta property="og:site_name"            content="YouTube">
          - <meta property="al:ios:app_name"         content="YouTube">
          - <meta property="al:android:app_name"     content="YouTube">
          - <meta name="twitter:app:name:iphone"     content="YouTube">
          - <meta name="twitter:app:name:ipad"       content="YouTube">
          - <meta name="twitter:app:name:googleplay" content="YouTube">
        - other
          - <meta property="al:ios:app_store_id" content="544007664">
          - <meta property="al:android:package" content="com.google.android.youtube">
          - <meta property="og:type" content="video.other">
          - <meta property="og:video:type" content="text/html">
          - <meta property="og:video:width" content="1280">
          - <meta property="og:video:height" content="720">
          - <meta property="og:video:tag" content="marketing mania">
          - <meta property="og:video:tag" content="stan leloup">
          - <meta property="fb:app_id" content="87741124305">
          - <meta name="twitter:card" content="player">
          - <meta name="twitter:site" content="@youtube">
          - <meta name="twitter:app:id:iphone" content="544007664">
          - <meta name="twitter:app:id:ipad" content="544007664">
          - <meta name="twitter:app:id:googleplay" content="com.google.android.youtube">
       */
    }
  }

  private def write(file: String, content: Response): Unit =
    FileUtils.write(s"$path/$file", encoder.apply(content).spaces2SortKeys).get

  private def read(file: String): Response = {
    val content = FileUtils.read(s"$path/$file").get
    parse(content).flatMap(decoder.decodeJson).right.get
  }
}
