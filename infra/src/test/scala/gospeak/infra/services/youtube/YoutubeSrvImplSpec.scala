package gospeak.infra.services.youtube

import java.time.Instant

import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.scala.domain.Secret
import gospeak.libs.youtube.YoutubeClient

class YoutubeSrvImplSpec extends BaseSpec {
  val secret: Secret = Secret(
    """{
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
      |}"""
      .stripMargin)
  val youtubeClient: YoutubeClient = YoutubeClient.create(secret)
  val youtubeSrvImpl: YoutubeSrvImpl = new YoutubeSrvImpl(youtubeClient)
  describe("videos") {
    it("should get all videos") {

      val now = Instant.now()
      val res = youtubeSrvImpl.videos("UCVelKVoLQIhwx9C2LWf-CDA")(now).unsafeRunSync()

      res shouldBe Seq()
    }
  }
}
