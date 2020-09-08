package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.VideoRepoSql._
import gospeak.infra.services.storage.sql.VideoRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.libs.scala.domain.Url

class VideoRepoSqlSpec extends RepoSpec {
  describe("VideoRepoSql") {
    it("should handle crud operations") {
      val (user, ctx) = createAdmin().unsafeRunSync()
      val externalEvent = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      videoRepo.list(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      val video = videoRepo.create(videoData1, externalEvent.id)(ctx).unsafeRunSync()
      videoRepo.list(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(video)

      val data = videoData2.copy(url = videoData1.url, channel = videoData1.channel, playlist = videoData1.playlist, publishedAt = videoData1.publishedAt)
      videoRepo.edit(data, externalEvent.id)(ctx).unsafeRunSync()
      videoRepo.list(params)(ctx.userAwareCtx).unsafeRunSync().items.map(_.data) shouldBe List(data)

      videoRepo.remove(videoData1, externalEvent.id)(ctx).unsafeRunSync() shouldBe true
      videoRepo.list(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
    }
    it("should merge identical video for different events") {
      val (user, ctx) = createAdmin().unsafeRunSync()
      val externalEvent1 = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val externalEvent2 = externalEventRepo.create(externalEventData2)(ctx).unsafeRunSync()
      val video1 = videoRepo.create(videoData1.copy(title = "aaa"), externalEvent1.id)(ctx).unsafeRunSync()
      val video2 = videoRepo.create(videoData2.copy(title = "bbb"), externalEvent1.id)(ctx).unsafeRunSync()
      videoRepo.create(videoData1.copy(title = "aaa"), externalEvent2.id)(ctx).unsafeRunSync()

      videoRepo.list(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(video1, video2)
      videoRepo.remove(videoData1, externalEvent2.id)(ctx).unsafeRunSync() shouldBe false
      videoRepo.list(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(video1, video2)
      videoRepo.remove(videoData1, externalEvent1.id)(ctx).unsafeRunSync() shouldBe true
      videoRepo.list(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(video2)
    }
    it("should select a page") {
      val (user, ctx) = createAdmin().unsafeRunSync()
      val externalEvent = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val video1 = videoRepo.create(videoData1.copy(title = "aaa", lang = "fr"), externalEvent.id)(ctx).unsafeRunSync()
      val video2 = videoRepo.create(videoData2.copy(title = "bbb", lang = "en"), externalEvent.id)(ctx).unsafeRunSync()

      videoRepo.list(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(video1, video2)
      videoRepo.list(params.page(2))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      videoRepo.list(params.pageSize(5))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(video1, video2)
      videoRepo.list(params.search(video1.title))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(video1)
      videoRepo.list(params.orderBy("lang"))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(video2, video1)
    }
    it("should be able to read correctly") {
      val (user, ctx) = createAdmin().unsafeRunSync()
      val externalEvent = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val video = videoRepo.create(videoData1, externalEvent.id)(ctx).unsafeRunSync()

      videoRepo.find(video.id).unsafeRunSync() shouldBe Some(video)
      videoRepo.findRandom().unsafeRunSync() shouldBe Some(video)
      videoRepo.list(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(video)
      videoRepo.listAll(externalEvent.id).unsafeRunSync() shouldBe List(video)
      videoRepo.listAllForChannel(video.channel.id).unsafeRunSync() shouldBe List(video)
      video.playlist.foreach(p => videoRepo.listAllForPlaylist(p.id).unsafeRunSync() shouldBe List(video))
      videoRepo.count(externalEvent.id).unsafeRunSync() shouldBe 1
      videoRepo.countForChannel(video.channel.id).unsafeRunSync() shouldBe 1
      video.playlist.foreach(p => videoRepo.countForPlaylist(p.id).unsafeRunSync() shouldBe 1)
    }
    it("should check queries") {
      check(insert(video.id, externalEvent.id), s"INSERT INTO ${tableSources.stripSuffix(" vis")} (${mapFields(fieldsSources, _.stripPrefix("vis."))}) VALUES (?, ?, ?, ?, ?)")
      check(delete(video.id, externalEvent.id), s"DELETE FROM $tableSources WHERE vis.video_id=? AND vis.external_event_id=?")
      check(selectOne(video.id, externalEvent.id), s"SELECT $fieldsSources FROM $tableSources WHERE vis.video_id=? AND vis.external_event_id=? ORDER BY vis.video_id IS NULL, vis.video_id")
      check(sum(video.id), s"SELECT COUNT(*) FROM $tableSources WHERE vis.video_id=? GROUP BY vis.video_id ORDER BY vis.video_id IS NULL, vis.video_id LIMIT 1")
      check(sum(externalEvent.id), s"SELECT COUNT(*) FROM $tableSources WHERE vis.external_event_id=? GROUP BY vis.external_event_id ORDER BY vis.external_event_id IS NULL, vis.external_event_id LIMIT 1")

      check(insert(video), s"INSERT INTO ${table.stripSuffix(" vi")} (${mapFields(fieldsInsert, _.stripPrefix("vi."))}) VALUES (${mapFields(fieldsInsert, _ => "?")})")
      check(update(video.data, now), s"UPDATE $table SET title=?, description=?, tags=?, duration=?, lang=?, views=?, likes=?, dislikes=?, comments=?, updated_at=? WHERE vi.id=?")
      check(delete(video.data.url), s"DELETE FROM $table WHERE vi.id=?")
      check(selectOne(video.id), s"SELECT $fields FROM $table WHERE vi.id=? $orderBy LIMIT 1")
      unsafeCheck(selectOneRandom(), s"SELECT $fields FROM $table $orderBy LIMIT 1 OFFSET FLOOR(RANDOM() * (SELECT COUNT(*) FROM videos vi))")
      check(selectPage(params), s"SELECT $fields FROM $table $orderBy LIMIT 20 OFFSET 0")
      check(selectAll(externalEvent.id), s"SELECT $fields FROM $tableWithSources WHERE vis.external_event_id=? $orderBy")
      check(selectAllForChannel(Url.Videos.Channel.Id("id")), s"SELECT $fields FROM $table WHERE vi.channel_id=? $orderBy")
      check(selectAllForPlaylist(Url.Videos.Playlist.Id("id")), s"SELECT $fields FROM $table WHERE vi.playlist_id=? $orderBy")
      check(countChannelId(Url.Videos.Channel.Id("id")), s"SELECT COUNT(*) FROM $table WHERE vi.channel_id=? GROUP BY vi.channel_id ORDER BY vi.channel_id IS NULL, vi.channel_id")
      check(countPlaylistId(Url.Videos.Playlist.Id("id")), s"SELECT COUNT(*) FROM $table WHERE vi.playlist_id=? GROUP BY vi.playlist_id ORDER BY vi.playlist_id IS NULL, vi.playlist_id")
    }
  }
}

object VideoRepoSqlSpec {

  import RepoSpec._

  val tableSources = "video_sources vis"
  val fieldsSources: String = mapFields("video_id, talk_id, proposal_id, external_proposal_id, external_event_id", "vis." + _)

  val table = "videos vi"
  val fieldsInsert: String = mapFields("platform, url, id, channel_id, channel_name, playlist_id, playlist_name, title, description, tags, published_at, duration, lang, views, likes, dislikes, comments, updated_at", "vi." + _)
  val fields: String = mapFields("url, channel_id, channel_name, playlist_id, playlist_name, title, description, tags, published_at, duration, lang, views, likes, dislikes, comments, updated_at", "vi." + _)
  val orderBy = "ORDER BY vi.title IS NULL, vi.title, vi.published_at IS NULL, vi.published_at"

  private val tableWithSources = s"$table INNER JOIN $tableSources ON vi.id=vis.video_id"
}
