package gospeak.libs.youtube

import java.io.ByteArrayInputStream

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.ChannelContentDetails
import com.google.api.services.youtube.model.ChannelContentDetails.RelatedPlaylists
import gospeak.libs.youtube.domain.{Channel, ChannelsResponse}
import org.scalatest.{FunSpec, Matchers}

import scala.collection.JavaConverters._

class YoutubeClientSpec extends FunSpec with Matchers {

  // you should paste your key here for testing
  val secrets: String =
    """{
      |""".stripMargin
  val SCOPES: Seq[String] = Seq("https://www.googleapis.com/auth/youtube.readonly")
  val HTTP_TRANSPORT: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport
  val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance
  val credential: GoogleCredential = GoogleCredential.fromStream(new ByteArrayInputStream(secrets.getBytes))
    .createScoped(SCOPES.asJava);
  val youtube: YouTube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).build
  val youtubeClient = new YoutubeClient(youtube)
  ignore("channelBy") {
    it("should retrieve channel information") {

      val value = youtubeClient.channelBy("UCbyWrAbUv7dxGcZ1nDvjpQw").unsafeRunSync()

      value shouldBe Right(ChannelsResponse("tnVOtk4NeGU6nDncDTE5m9SmuHc/nxkWoZ4KXJ_COz8X31ZwYjlkA9g",
        None,
        items =
          List(Channel("tnVOtk4NeGU6nDncDTE5m9SmuHc/4sJiFPiaRzdcfJE3HZnK--QAXXo",
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
            None)),
        "youtube#channelListResponse",
        None,
        None,
        None,
        None))

    }
  }

  ignore("videos") {
    it("should retrieve results") {
      val value = youtubeClient.videos("UCVelKVoLQIhwx9C2LWf-CDA").unsafeRunSync()
      val items = value.right.get.items
      items.length shouldBe 50
      items.map(_.id.getVideoId) shouldBe Seq("Vt91rBZ12ok",
        "VlxaZ1jlYk0",
        "ZzLVBNVe4IM",
        "LpIiH-8qDBM",
        "E7Moc8IwLf8",
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
        "KHGol2SocOo",
        "U7DQ79RvFW8",
        "EkISULjH2B4",
        "UxeY-EaTGIU",
        "OTi-ixRo7K8",
        "cQ3cGxvXxoY",
        "zTxWbAHZRas",
        "G4rYNmBoHIg",
        "H7jOLzK_mZo",
        "BhtFSMJwoFU",
        "f7EcHVTdf-0",
        "diDmR9uvtSs",
        "kMkudZyEOGU",
        "CJJ0GH8ACA4",
        "XB6uxAZ3x_Q",
        "j3bq7_Bwb5o",
        "twdm4ZIQuBQ",
        "G3L7W7ohwD0",
        "-fjsH-jJGJc",
        "knPHV7Fvhno",
        "Bsp712nECmg",
        "Qzv5TAhoSK0",
        "DG3WxbtDPeo",
        "5Kz9X10t3zQ",
        "wo_WoY-V49A",
        "RcvlcHvRgrY",
        "XClhHa5NPBc",
        "g3kuTdZ0eN8",
        "TGiE5PKQ6K8",
        "upQvgpGWhnw",
        "6wjlOCmjntE",
        "ht9tAlQbEys")
    }
  }
}
