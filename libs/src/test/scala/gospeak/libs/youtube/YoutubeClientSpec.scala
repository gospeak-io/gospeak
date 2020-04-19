package gospeak.libs.youtube

import java.time.Instant

import com.google.api.services.youtube.model.ChannelContentDetails
import com.google.api.services.youtube.model.ChannelContentDetails.RelatedPlaylists
import com.softwaremill.diffx.scalatest.DiffMatcher
import gospeak.libs.scala.domain.Secret
import gospeak.libs.youtube.domain._
import org.scalatest.{FunSpec, Inside, Matchers}

import scala.collection.immutable

class YoutubeClientSpec extends FunSpec with Matchers with DiffMatcher with Inside {
  // you should paste your key here for testing
  val secret: String =
    """
      |{}
      |""".stripMargin
  private val youtubeClient = YoutubeClient.create(Secret(secret))

  describe("channelBy") {
    it("should retrieve channel information") {
      val value = youtubeClient.channelBy("UCVelKVoLQIhwx9C2LWf-CDA").unsafeRunSync()
      inside(value) {
        case Right(ChannelsResponse(etag, None, items, kind, None, None, None, None)) =>
          etag should startWith("\"nxOHAKTVB7baOKsQgTtJIyGxcs8")
          kind shouldBe "youtube#channelListResponse"
          items should contain theSameElementsAs List(
            Channel(
              "\"nxOHAKTVB7baOKsQgTtJIyGxcs8/cd9b2xUCZ7rIS6n0LlbEZI4q3QE\"",
              "UCVelKVoLQIhwx9C2LWf-CDA",
              "youtube#channel",
              Some(new ChannelContentDetails()
                .setRelatedPlaylists(new RelatedPlaylists()
                  .setUploads("UUVelKVoLQIhwx9C2LWf-CDA")
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
      val value = youtubeClient.playlistItems("PLv7xGPH0RMUTbzjcYSIMxGXA8RrQWdYGh").unsafeRunSync()
      inside(value) {
        case Right(PlaylistItems(etag, None, items: immutable.Seq[PlaylistItem], kind, nextPageToken, None, None, None)) =>
          etag should startWith("\"nxOHAKTVB7baOKsQgTtJIyGxcs8")
          // nextPage
          nextPageToken shouldBe None
          //kind
          kind shouldBe "youtube#playlistItemListResponse"
          //items
          items should contain theSameElementsAs Seq(PlaylistItem(Some(ContentDetails(
            None, None, None, Some("NkH9WNE0OJc"),
            Some(Instant.parse("2018-11-08T11:28:57Z")))),
            "\"nxOHAKTVB7baOKsQgTtJIyGxcs8/n9JuVV-5TQNm4e5RTaVlQd11T7s\"",
            "UEx2N3hHUEgwUk1VVGJ6amNZU0lNeEdYQThSclFXZFlHaC41NkI0NEY2RDEwNTU3Q0M2", "youtube#playlistItem", None),
            PlaylistItem(Some(ContentDetails(None, None, None, Some("R60eYvVZ1q8"), Some(Instant.parse("2019-05-15T11:09:12Z")))),
              "\"nxOHAKTVB7baOKsQgTtJIyGxcs8/p3Afs8X9FP7qS8kYistOFeMqlOo\"",
              "UEx2N3hHUEgwUk1VVGJ6amNZU0lNeEdYQThSclFXZFlHaC4yODlGNEE0NkRGMEEzMEQy", "youtube#playlistItem", None),
            PlaylistItem(Some(ContentDetails(None, None, None, Some("s-7vdEXhxes"), Some(Instant.parse("2019-09-20T12:37:15Z")))),
              "\"nxOHAKTVB7baOKsQgTtJIyGxcs8/zXRkAXSqQ2yom_7zHaD97-m4TYk\"",
              "UEx2N3hHUEgwUk1VVGJ6amNZU0lNeEdYQThSclFXZFlHaC4wMTcyMDhGQUE4NTIzM0Y5", "youtube#playlistItem",
              None),
          )
      }
    }
    it("should fail when id does not exist") {
      val value = youtubeClient.playlistItems("UCbyWrAbUv7dxGcZ1nDvjpQw").unsafeRunSync()
      value shouldBe Left(YoutubeErrors(404,
        List(
          YoutubeError(Some("youtube.playlistItem"),
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
  describe("search") {
    it("should retrieve results") {
      val value = youtubeClient.search("UCVelKVoLQIhwx9C2LWf-CDA", "youtube#video").unsafeRunSync()
      val items: Seq[SearchResult] = value.right.get.items
      items.length shouldBe 43
      val expected = Seq("F8C_iPGhHoI",
        "IN8DeMfbWVs",
        "i94gJ4-tvfU",
        "KCgkFpt9dfk",
        "d1rMYQZLVek",
        "zVbIbqmHU-4",
        "y1-Gh0bMsUo",
        "mQhtADuqriA",
        "VKe9EE4MUxk",
        "K-tXEkGTzfE",
        "jWhee5h5yJ0",
        "h_exGbGGePI",
        "Em4EgeD69oU",
        "FO2YFmTB1Tw",
        "0P6-r7_GIaQ",
        "RZADfe_vPWo",
        "AeTsLXGnbos",
        "PjUsN6TU-3w",
        "KpUHaMhYIUk",
        "cbfFkAPFxY4",
        "pXnYjCI33Mc",
        "0AtyQ-F6jrw",
        "E606Nxnxg14",
        "1pXgqd064-4",
        "vhJAHlKiFSI",
        "Hza3U8QA9w0",
        "Ot1tOyB0PPE",
        "pRmwrp_99fA",
        "8zFPLClf-Qw",
        "MKQ8gUGdKGs",
        "s-7vdEXhxes",
        "eJb3xYNcTzw",
        "vGO-h9qH0rw",
        "UemFPnjvx44",
        "Q3EKu_Eu5Gg",
        "itGmiTS_IPw",
        "NkH9WNE0OJc",
        "xUudC8S8M6s",
        "c6ZqYk01fbc",
        "oOE36iJ7xFk",
        "XQaFlNQuJjA",
        "xZBrfxUo7kM",
        "ADSlYYjnnQE")

      val ids = items.map(_.id.getVideoId)
      ids should contain theSameElementsAs  expected
    }
  }

  describe("videos") {
    it("should return selected videos") {

      val result = youtubeClient.videos(Seq("itGmiTS_IPw",
        "NkH9WNE0OJc",
        "xUudC8S8M6s",
        "c6ZqYk01fbc",
        "oOE36iJ7xFk"))
        .unsafeRunSync().right.get

      inside(result) { case VideosListResponse(kind, etag, info, items: Seq[VideoItem]) =>
        kind shouldBe "youtube#videoListResponse"
        etag should startWith("\"nxOHAKTVB7baOKsQgTtJIyGxcs8")
        info shouldBe PageInfo(5, 5)
        val expected: Seq[VideoItem] = List(VideoItem("youtube#video", "\"nxOHAKTVB7baOKsQgTtJIyGxcs8/BXVdRCbX4lVPaLX6DSq33Tuql3E\"",
          "itGmiTS_IPw", Some(Instant.parse("2019-06-14T09:53:15Z")),
          Some("UCVelKVoLQIhwx9C2LWf-CDA"), Some("[SC] Entre industrialisation et artisanat, le métier de développeur - Arnaud Lemaire"),
          Some(
            """Une conférence pour se poser la question de ce qu’est notre métier, et de pourquoi celui-ci est loin de se borner à la simple écriture de code source.
              |
              |De comment sortir de la posture du développeur en tant que simple exécutant, et pourquoi notre métier a une très forte dimension stratégique.
              |
              |Enfin en regardant nos pratiques, méthodes et outils, nous replacerons ceux-ci dans leurs contextes en posant la question de ce qu’est l’ingénierie logicielle.""".stripMargin),
          Some(39), Some(1), Seq()),
          VideoItem("youtube#video", "\"nxOHAKTVB7baOKsQgTtJIyGxcs8/vHjI97OHigZf0pEoEahqsb8prus\"", "NkH9WNE0OJc", Some(Instant.parse("2018-11-08T11:28:57Z")), Some("UCVelKVoLQIhwx9C2LWf-CDA"), Some("[Rennes DevOps] Quels choix d'hébergement possibles pour des données de santé ?"
          ), Some(
            """Nicolas, Anas et Quentin nous ont proposé de parler de l'hébergement des données de santé.
              |
              |Certains dans le coin doivent bien travailler plus ou moins avec des données sensibles.Les données de santés en font partie.
              |
              |Peut-être que la radio de votre carie qui se retrouve sur Internet ne vous fait pas peur.Mais peut-être que certains spécialistes savent des choses un peu moins avouables sur vous :)
              |
              |Après, ils vont nous parler de trucs moins rigolos comme de la réglementation. Mais comme ils vont nous payer un apéro à la fin c'est plutôt cool :)
              |
              |#firebase, #vmware, #k8s, #docker, #objectstorage
              |
              |Si vous lisez encore ceci, vous vous dites que vous n'avez pas de données de santé. Mais si ça fonctionne pour la santé, ça va fonctionner aussi pour le reste.
              |
              |Détail du contenu :
              |· Contraintes et liberté sur les données de santé
              |· Solutions d'hébergement HDS existantes
              |· Présentation de choix d'architecture hébergement de startups (problématiques et solutions)
              |
              |Par :
              |- Nicolas Verdier (OVH)
              |- Anas Ameziane (Follow)
              |- Quentin Decré (Follow)""".stripMargin), Some(13), Some(0), null),
          VideoItem("youtube#video", "\"nxOHAKTVB7baOKsQgTtJIyGxcs8/Rz4_7B6zZsKs33a59YYH6sqV-Eg\"", "xUudC8S8M6s", Some(Instant.parse("2018-10-15T15:08:19Z")),
            Some("UCVelKVoLQIhwx9C2LWf-CDA"), Some("[BreizhJUG] Au delà des brokers: un tour de l'environnement Kafka - Florent Ramière"),
            Some(
              """Apache Kafka ne se résume pas aux brokers, il y a tout un écosystème open-source qui gravite autour.Je vous propose ainsi de découvrir les principaux composants comme Kafka Streams, KSQL, Kafka Connect, Rest proxy, Schema Registry, MirrorMaker, etc.
                |Venez avec vos questions, le plus la session sera interactive, le mieux elle sera!
                |
                |Slides:https://www.slideshare.net/FlorentRamiere/jug-ecosystem""".stripMargin
            ), Some(13), Some(0), Seq()),
          VideoItem("youtube#video", "\"nxOHAKTVB7baOKsQgTtJIyGxcs8/qlmgvyUCvjQ64U4tv3tTNLbiMck\"",
            "c6ZqYk01fbc", Some(Instant.parse("2018-03-15T22:57:36Z")),
            Some("UCVelKVoLQIhwx9C2LWf-CDA"),
            Some("[Docker MeetUp] Tour d'horizon de Kubernetes (David Gageot)"),
            Some(
              """Au travers de vrais exemples de code, nous allons faire un tour d'horizon de Kubernetes: Déploiement de services, Pattern Sidecar, Extension de la platforme, Introduction a Istio, Expérience développeur, Docker for Desktop, Google Kubernetes Engine.
                |Bref, de quoi bien commencer avec Kubermetes!
                |Speaker : David Gageot""".stripMargin), Some(15), Some(0), Seq("kubernetes", "istio")),
          VideoItem("youtube#video", "\"nxOHAKTVB7baOKsQgTtJIyGxcs8/_07_nRK2r_wdnyTn-UpsWMqwmvs\"",
            "oOE36iJ7xFk", Some(Instant.parse("2018-04-18T12:31:10Z")),
            Some("UCVelKVoLQIhwx9C2LWf-CDA"),
            Some("Full-remote : guide de survie en environnement distant (Matthias Dugué)"),
            Some(
              """Travailler en équipe n'est jamais un défi simple. Travailler à distance est un enjeu encore plus complexe.
                | Collaborer avec une équipe entièrement distribuée relève de l'exploit.
                | Pourtant de plus de plus de projets (collaboratifs, associatifs, ou startups) choisissent ce mode de fonctionnement, qui offre aussi de nombreux avantages.
                |
                |Avant même que les concepts de full-remote, de co-working, et de BYOD ne deviennent populaires, les mouvements Open Source se sont attelés à la tâche difficile de faire travailler ensemble des gens en les reliant uniquement par le réseau.
                |
                |Après plusieurs années passées à collaborer avec des gens sur de nombreux projets, Open Source ou non, petit retour d'expérience du full-remote, ce qu'il engage, ce qu'il faut savoir, et les outils indispensables à un travail asynchrone efficace, ensemble.""".stripMargin
            ), Some(19), Some(2), Seq()))
        items shouldBe expected
      }
    }
  }
}
