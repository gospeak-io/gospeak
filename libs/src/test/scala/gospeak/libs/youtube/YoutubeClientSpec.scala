package gospeak.libs.youtube

import java.time.Instant

import gospeak.libs.scala.domain.Secret
import gospeak.libs.youtube.domain._
import org.scalatest.Inside
import org.scalatest.funspec.AnyFunSpec
import scala.collection.immutable
import scala.concurrent.duration.{FiniteDuration, MICROSECONDS}
import org.scalatest.matchers.should.Matchers

class YoutubeClientSpec extends AnyFunSpec with Matchers with Inside {
  // you should paste your key here for testing
  val secret: String =
    """
      |{
      |    "type": "service_account",
      |    "project_id": "gospeak-255017",
      |    "private_key_id": "4b6bc550c2917086e9cc2ca1968afa13566b2fd0",
      |    "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDKr/W2o/W25yqN\ngIL6lyqUj8GkGiVXl9h0U8I4CUTu0i/Hq8WGj1khEdMw9T89LwQbAOj6Eg759xAO\n4DhRPh+6VsHQVxCfoxVNZhiZGoBcPvrwQRIyQMrm3rtn++ASvT44YHIxPQUs813V\nnVM9bkaQ3bWvFmBKUgoQXGbaqveGekHbUUDS+QIeCu4ao0PAxZA6l/3iDiYvoMVr\nhz5AFcWTX5WQ2wftom63Q/FEUqZPC00hplw885h1h3r3JZp5zyKY6rEFRdfJQb4H\n4XLw05mMBislG5WbNzp/18eHClJP/YFXHZiFYO+oMvjHsY43TRmI7CrE8vvYxJ6p\nEb0TCrq/AgMBAAECggEAEsBzJYT6kQPG0A75QmEFGd5JT/rxqxTrr0RAgU0T1fR9\nyL9MWvkDVsF+ngm44nQ2Sp+ruwo73UYApx+sL8TLmQ9Shwl6Pdd2SEVCnIeMvWJz\nEBJoS8K3nHxmyJjPT9LqR4grIgFya4ibppoTi89nMxsP8zVJKBcTwnrwZeXQJXfn\n8NABpg3bhejNYVBokKeOKv+2zJjADod89JPPTzc7ihEF2mO2GoVFpK/gLUroqj6O\nDwRDQGKGyVMRG6QLF/G3FqE5HaEBI/caDuZQHbvSwR7eYOL8gnyNxEqoI/zEtEvr\nDem6JPR2iMLt6qHlB8lEJPv9akkLdgICBovuJsnDIQKBgQDsXHDPScMJKhwRMsZH\nGCZpY97VftUkAYbohsYqy8F0pexY2OVL1jY7DWl1br7DLH2knQLJZTpqZZAegPa5\nOuKHZxxJUhgvvt6cb7ZCRZYo4kkYhYmRyQhydwGbR3+RXK9VqknzB2t6+f0aUwKN\nnwWce7iWRpVa9qv5XEHjD7FuuwKBgQDbh0F72jrwUM68l7N/EAse5P/P4JcVzS2+\n7hyvevGaaAn0usx4tl9TXY2rv8Mgfm1r9MiiFTDjhYkCMfAKHf2jHiJ0UEodgv8h\nvxCUyJhh5WwptCacZklAlavMLLCFhkdookFR2M/IF9asD7JcTCYnKV3Eqmi8LoGx\nRIV6Ts+9zQKBgQDEjzeNWvUkGO3Qa54yn2XKPTCh8WEFGXP8yZ/hFSNjg1yionVF\ndPYSc9vgueFQZB50l9Iqc9F5i86nX25OqiaanegLHYdZpWxxQgGa6U2v4EcTanH2\nV+17a3ZdkL8IvsBdCEmJHwGF+oE+tAuqhLVg5g6igj5QsFRiAhQU5QcUYwKBgEZ3\no0iLY7HybnpRQ9f8oWU4YvkqgbUI2K9aJbEaiOVkkhWRxMLW38CV3j0MYClVC/DE\ncYa9wKS4H6OpvgCxYdJzgOHPSAszGoyNlVf9EBUUnOTCJEa9+rOVl8EBc2RZFyD6\nPHd2XjQ/mrQ+kaVY+EJH4AaaIOaPEyiA80uwcrTdAoGAUQW34q7iVpOsmEtU4ohg\najOyeAkkloyL77kr/l4GyQzvDYyfZhbgA0YGh4SLfn8zcuAcAm/Ou804ypmo2qcQ\niO5iURfaff+Kcu4xFN/pnXm3TvBzgVkGfJBBIXaQzSW05sSy/Igzn1XrCAzOfHus\nusTmu10vOlGieGcqKKbi8Mg=\n-----END PRIVATE KEY-----\n",
      |    "client_email": "gospeak-youtube@gospeak-255017.iam.gserviceaccount.com",
      |    "client_id": "110317782940401292833",
      |    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
      |    "token_uri": "https://oauth2.googleapis.com/token",
      |    "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
      |    "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/gospeak-youtube%40gospeak-255017.iam.gserviceaccount.com"
      |
      |}
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
              "youtube#channel"))
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

      val ids = items.map(_.id.id)
      ids should contain theSameElementsAs expected
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

      inside(result) { case VideosListResponse(kind, info, items: Seq[VideoItem]) =>
        kind shouldBe "youtube#videoListResponse"
        info shouldBe Some(PageInfo(5, 5))
        val expected: Seq[VideoItem] = List(VideoItem("youtube#video",
          "itGmiTS_IPw",
          Some(Instant.parse("2019-06-14T09:53:15Z")),
          Some("UCVelKVoLQIhwx9C2LWf-CDA"),
          Some("name"),
          Some("[SC] Entre industrialisation et artisanat, le métier de développeur - Arnaud Lemaire"),
          Some(
            """Une conférence pour se poser la question de ce qu’est notre métier, et de pourquoi celui-ci est loin de se borner à la simple écriture de code source.
              |
              |De comment sortir de la posture du développeur en tant que simple exécutant, et pourquoi notre métier a une très forte dimension stratégique.
              |
              |Enfin en regardant nos pratiques, méthodes et outils, nous replacerons ceux-ci dans leurs contextes en posant la question de ce qu’est l’ingénierie logicielle.""".stripMargin),
          Some(39), Some(1),
          Some(0),
          Some("En"),
          Some(10),
          Some("")
          , Seq()),
          VideoItem("youtube#video", "NkH9WNE0OJc",
            Some(Instant.parse("2018-11-08T11:28:57Z")),
            Some("UCVelKVoLQIhwx9C2LWf-CDA"),
            Some("name"),
            Some("[Rennes DevOps] Quels choix d'hébergement possibles pour des données de santé ?"
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
                |- Quentin Decré (Follow)""".stripMargin),
            Some(13), Some(0), Some(0),
            Some("En"),
            Some(10),
            Some("")
            , Seq()),
          VideoItem("youtube#video", "xUudC8S8M6s", Some(Instant.parse("2018-10-15T15:08:19Z")),
            Some("UCVelKVoLQIhwx9C2LWf-CDA"),
            Some("name"),
            Some("[BreizhJUG] Au delà des brokers: un tour de l'environnement Kafka - Florent Ramière"),
            Some(
              """Apache Kafka ne se résume pas aux brokers, il y a tout un écosystème open-source qui gravite autour.Je vous propose ainsi de découvrir les principaux composants comme Kafka Streams, KSQL, Kafka Connect, Rest proxy, Schema Registry, MirrorMaker, etc.
                |Venez avec vos questions, le plus la session sera interactive, le mieux elle sera!
                |
                |Slides:https://www.slideshare.net/FlorentRamiere/jug-ecosystem""".stripMargin
            ), Some(13), Some(0), Some(0),
            Some("En"),
            Some(10),
            Some("")
            , Seq()),
          VideoItem("youtube#video",
            "c6ZqYk01fbc", Some(Instant.parse("2018-03-15T22:57:36Z")),
            Some("UCVelKVoLQIhwx9C2LWf-CDA"),
            Some("name"),
            Some("[Docker MeetUp] Tour d'horizon de Kubernetes (David Gageot)"),
            Some(
              """Au travers de vrais exemples de code, nous allons faire un tour d'horizon de Kubernetes: Déploiement de services, Pattern Sidecar, Extension de la platforme, Introduction a Istio, Expérience développeur, Docker for Desktop, Google Kubernetes Engine.
                |Bref, de quoi bien commencer avec Kubermetes!
                |Speaker : David Gageot""".stripMargin),
            Some(15),
            Some(0),
            Some(0),
            Some("En"),
            Some(10),
            Some(""),
            Seq("kubernetes", "istio")),
          VideoItem("youtube#video",
            "oOE36iJ7xFk", Some(Instant.parse("2018-04-18T12:31:10Z")),
            Some("UCVelKVoLQIhwx9C2LWf-CDA"),
            Some("name"),
            Some("Full-remote : guide de survie en environnement distant (Matthias Dugué)"),
            Some(
              """Travailler en équipe n'est jamais un défi simple. Travailler à distance est un enjeu encore plus complexe.
                | Collaborer avec une équipe entièrement distribuée relève de l'exploit.
                | Pourtant de plus de plus de projets (collaboratifs, associatifs, ou startups) choisissent ce mode de fonctionnement, qui offre aussi de nombreux avantages.
                |
                |Avant même que les concepts de full-remote, de co-working, et de BYOD ne deviennent populaires, les mouvements Open Source se sont attelés à la tâche difficile de faire travailler ensemble des gens en les reliant uniquement par le réseau.
                |
                |Après plusieurs années passées à collaborer avec des gens sur de nombreux projets, Open Source ou non, petit retour d'expérience du full-remote, ce qu'il engage, ce qu'il faut savoir, et les outils indispensables à un travail asynchrone efficace, ensemble.""".stripMargin
            ), Some(19), Some(2), Some(0),
            Some("En"),
            Some(10),
            Some("")
            , Seq()))
        items shouldBe expected
      }
    }
  }

}
