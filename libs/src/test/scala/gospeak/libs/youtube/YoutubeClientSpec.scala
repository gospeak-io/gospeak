package gospeak.libs.youtube

import java.io.ByteArrayInputStream
import java.time.Instant

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.ChannelContentDetails
import com.google.api.services.youtube.model.ChannelContentDetails.RelatedPlaylists
import gospeak.libs.youtube.domain.{Channel, ChannelsResponse, ContentDetails, PlaylistItem, PlaylistItems, YError, YoutubeErrors}
import org.scalatest.{FunSpec, Inside, Matchers}

import scala.collection.JavaConverters._
import scala.collection.immutable

class YoutubeClientSpec extends FunSpec with Matchers with Inside {

  // you should paste your key here for testing
  val secrets: String =
    """{
       |
       |}
      |""".stripMargin
  val SCOPES: Seq[String] = Seq("https://www.googleapis.com/auth/youtube.readonly")
  val HTTP_TRANSPORT: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport
  val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance
  val credential: GoogleCredential = GoogleCredential.fromStream(new ByteArrayInputStream(secrets.getBytes))
    .createScoped(SCOPES.asJava);
  val youtube: YouTube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).build
  val youtubeClient = new YoutubeClient(youtube)

  describe("channelBy") {
    it("should retrieve channel information") {

      val value = youtubeClient.channelBy("UCbyWrAbUv7dxGcZ1nDvjpQw").unsafeRunSync()


      inside(value) {
        case Right(ChannelsResponse(etag,
        None,
        items,
        kind,
        None,
        None,
        None,
        None))
        =>

          etag should startWith("\"tnVOtk4NeGU6nDncDTE5m9SmuHc")
          kind shouldBe "youtube#channelListResponse"
          items should contain theSameElementsAs List(Channel("\"tnVOtk4NeGU6nDncDTE5m9SmuHc/4sJiFPiaRzdcfJE3HZnK--QAXXo\"",
            "UCbyWrAbUv7dxGcZ1nDvjpQw",
            "youtube#channel",
            Some(new ChannelContentDetails()
              .setRelatedPlaylists(new RelatedPlaylists()
                .setUploads("UUbyWrAbUv7dxGcZ1nDvjpQw")
                .setWatchHistory("HL")
                .setWatchLater("WL"))),
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None))
      }
    }
  }

  describe("playListItems") {
    it("should retrieve items") {
      val value = youtubeClient.playlistItems("PL1d9UGnF3u-sMQ9SOAhv0oqgdG_dXzkRC").unsafeRunSync()
      inside(value) {
        case Right(PlaylistItems(etag, None, items: immutable.Seq[PlaylistItem], kind, nextPageToken, None, None, None)) =>

          etag should startWith("\"tnVOtk4NeGU6nDncDTE5m9SmuHc")
          // nextPage
          nextPageToken shouldBe Some("CAUQAA")
          //kind
          kind shouldBe "youtube#playlistItemListResponse"
          //items
          items should contain theSameElementsAs Seq(PlaylistItem(Some(ContentDetails(
            None, None, None, Some("EJbP-4nU-Tk"),
            Some(Instant.parse("2020-02-21T07:23:02Z")))),
            "\"tnVOtk4NeGU6nDncDTE5m9SmuHc/pGBZncyw0r94aTtOhJ98_sjMh50\"",
            "UEwxZDlVR25GM3Utc01ROVNPQWh2MG9xZ2RHX2RYemtSQy4yODlGNEE0NkRGMEEzMEQy", "youtube#playlistItem", None),
            PlaylistItem(Some(ContentDetails(None, None, None, Some("I8CCgYlc6pk"), Some(Instant.parse("2020-02-21T07:23:27Z")))),
              "\"tnVOtk4NeGU6nDncDTE5m9SmuHc/ZVMZ4GPP_BoWMxlQXXNN044rYwU\"",
              "UEwxZDlVR25GM3Utc01ROVNPQWh2MG9xZ2RHX2RYemtSQy41NkI0NEY2RDEwNTU3Q0M2", "youtube#playlistItem", None),
            PlaylistItem(Some(ContentDetails(None, None, None, Some("wbFpwSDhntc"), Some(Instant.parse("2020-02-21T07:23:28Z")))),
              "\"tnVOtk4NeGU6nDncDTE5m9SmuHc/w5JU_iCFQfrK9N1TszpPdBM4M6M\"",
              "UEwxZDlVR25GM3Utc01ROVNPQWh2MG9xZ2RHX2RYemtSQy4zMDg5MkQ5MEVDMEM1NTg2", "youtube#playlistItem",
              None), PlaylistItem(Some(ContentDetails(None, None, None, Some("c1wTjVYtV2s"),
              Some(Instant.parse("2020-02-21T07:23:02Z")))), "\"tnVOtk4NeGU6nDncDTE5m9SmuHc/XqTSF4hqXGf77951VqsU35c3glo\"",
              "UEwxZDlVR25GM3Utc01ROVNPQWh2MG9xZ2RHX2RYemtSQy4wMTcyMDhGQUE4NTIzM0Y5",
              "youtube#playlistItem", None),
            PlaylistItem(Some(ContentDetails(None, None, None, Some("U2FJo-mesJU"), Some(Instant.parse("2020-02-21T07:23:12Z")))),
              "\"tnVOtk4NeGU6nDncDTE5m9SmuHc/34y0j_eFE4GkoD09S1dLeaUI32c\"",
              "UEwxZDlVR25GM3Utc01ROVNPQWh2MG9xZ2RHX2RYemtSQy5EMEEwRUY5M0RDRTU3NDJC",
              "youtube#playlistItem", None))
      }
    }

    it("should fail when id does not exist") {
      val value = youtubeClient.playlistItems("UCbyWrAbUv7dxGcZ1nDvjpQw").unsafeRunSync()
      value shouldBe Left(YoutubeErrors(404,
        List(
          YError(Some("youtube.playlistItem"),
            Some("playlistId"),
            Some("parameter"),
            Some("The playlist identified with the requests <code>playlistId</code> parameter cannot be found."),
            Some("playlistNotFound"),
          )),
        Some(
          """404 Not Found
            |{
            |  "code" : 404,
            |  "errors" : [ {
            |    "domain" : "youtube.playlistItem",
            |    "location" : "playlistId",
            |    "locationType" : "parameter",
            |    "message" : "The playlist identified with the requests <code>playlistId</code> parameter cannot be found.",
            |    "reason" : "playlistNotFound"
            |  } ],
            |  "message" : "The playlist identified with the requests <code>playlistId</code> parameter cannot be found."
            |}""".stripMargin)))
    }
  }

  describe("videos") {
    it("should retrieve results") {
      val value = youtubeClient.search("UCbyWrAbUv7dxGcZ1nDvjpQw").unsafeRunSync()
      val items = value.right.get.items
      items.length shouldBe 50
      items.map(_.id.getVideoId) shouldBe Seq("Vt91rBZ12ok",
        "VlxaZ1jlYk0",
        "ZzLVBNVe4IM",
        "E7Moc8IwLf8",
        "LpIiH-8qDBM",
        "YJHvgJesjNc",
        "iCGh3bbB1Zo",
        "7dNFs_Zz9dM",
        "mC_gj8E-fo4",
        "jKXxTFAOb4A",
        "71mKBF1mN48",
        "fCju4SyxiyM",
        "jpRrViAToRQ",
        "FhStIlZi93o",
        "1GSR9kGNYRg",
        "QIkGNcr8dsY",
        "MrFGGXoEHyI",
        "BwSJxxVcjXc",
        "U7DQ79RvFW8",
        "KHGol2SocOo",
        "EkISULjH2B4",
        "cQ3cGxvXxoY",
        "UxeY-EaTGIU",
        "OTi-ixRo7K8",
        "zTxWbAHZRas",
        "H7jOLzK_mZo",
        "G4rYNmBoHIg",
        "BhtFSMJwoFU",
        "f7EcHVTdf-0",
        "diDmR9uvtSs",
        "kMkudZyEOGU",
        "XB6uxAZ3x_Q",
        "CJJ0GH8ACA4",
        "j3bq7_Bwb5o",
        "twdm4ZIQuBQ",
        "G3L7W7ohwD0",
        "-fjsH-jJGJc",
        "knPHV7Fvhno",
        "Bsp712nECmg",
        "Qzv5TAhoSK0",
        "DG3WxbtDPeo",
        "RcvlcHvRgrY",
        "wo_WoY-V49A",
        "5Kz9X10t3zQ",
        "XClhHa5NPBc",
        "g3kuTdZ0eN8",
        "TGiE5PKQ6K8",
        "upQvgpGWhnw",
        "6wjlOCmjntE",
        "ht9tAlQbEys")
    }
  }
}
