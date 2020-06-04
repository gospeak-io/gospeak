package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.VideoRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.libs.scala.domain.Url

class VideoRepoSqlSpec extends RepoSpec {
  describe("VideoRepoSql") {
    describe("Queries") {
      describe("video_sources") {
        it("should build insert") {
          val q = VideoRepoSql.insert(video.id, externalEvent.id)
          check(q, s"INSERT INTO ${tableSources.stripSuffix(" vis")} (${mapFields(fieldsSources, _.stripPrefix("vis."))}) VALUES (?, NULL, NULL, NULL, ?)")
        }
        it("should build delete") {
          val q = VideoRepoSql.delete(video.id, externalEvent.id)
          check(q, s"DELETE FROM $tableSources WHERE video_id=? AND external_event_id=?")
        }
        it("should build selectOne by id") {
          val q = VideoRepoSql.selectOne(video.id, externalEvent.id)
          check(q, s"SELECT $fieldsSources FROM $tableSources WHERE video_id=? AND external_event_id=? ORDER BY vis.video_id IS NULL, vis.video_id")
        }
        it("should build sum for video_id") {
          val q = VideoRepoSql.sum(video.id)
          check(q, s"SELECT COUNT(*) FROM $tableSources WHERE video_id=? GROUP BY vis.video_id ORDER BY vis.video_id IS NULL, vis.video_id LIMIT 1")
        }
        it("should build sum for external event") {
          val q = VideoRepoSql.sum(externalEvent.id)
          check(q, s"SELECT COUNT(*) FROM $tableSources WHERE external_event_id=? GROUP BY vis.external_event_id ORDER BY vis.external_event_id IS NULL, vis.external_event_id LIMIT 1")
        }
      }
      describe("videos") {
        it("should build insert") {
          val q = VideoRepoSql.insert(video)
          check(q, s"INSERT INTO ${table.stripSuffix(" vi")} (${mapFields(fieldsInsert, _.stripPrefix("vi."))}) VALUES (${mapFields(fieldsInsert, _ => "?")})")
        }
        it("should build update") {
          val q = VideoRepoSql.update(video.data, now)
          check(q, s"UPDATE $table SET title=?, description=?, tags=?, duration=?, lang=?, views=?, likes=?, dislikes=?, comments=?, updated_at=? WHERE id=?")
        }
        it("should build delete") {
          val q = VideoRepoSql.delete(video.data.url)
          check(q, s"DELETE FROM $table WHERE id=?")
        }
        it("should build selectOne by id") {
          val q = VideoRepoSql.selectOne(video.id)
          check(q, s"SELECT $fields FROM $table WHERE vi.id=? $orderBy LIMIT 1")
        }
        it("should build selectRandom") {
          val q = VideoRepoSql.selectOneRandom()
          val sql = s"SELECT $fields FROM $table  $orderBy LIMIT 1 OFFSET FLOOR(RANDOM() * (SELECT COUNT(*) FROM videos))"
          q.fr.query.sql shouldBe sql
          // check(q, sql)
        }
        it("should build selectPage") {
          val q = VideoRepoSql.selectPage(params)
          check(q, s"SELECT $fields FROM $table $orderBy LIMIT 20 OFFSET 0")
        }
        it("should build selectAll") {
          val q = VideoRepoSql.selectAll(externalEvent.id)
          check(q, s"SELECT $fields FROM $tableWithSources WHERE vis.external_event_id=? $orderBy")
        }
        it("should build selectAllForChannel") {
          val q = VideoRepoSql.selectAllForChannel(Url.Videos.Channel.Id("id"))
          check(q, s"SELECT $fields FROM $table WHERE vi.channel_id=? $orderBy")
        }
        it("should build selectAllForPlaylist") {
          val q = VideoRepoSql.selectAllForPlaylist(Url.Videos.Playlist.Id("id"))
          check(q, s"SELECT $fields FROM $table WHERE vi.playlist_id=? $orderBy")
        }
        it("should build countChannelId") {
          val q = VideoRepoSql.countChannelId(Url.Videos.Channel.Id("id"))
          check(q, s"SELECT COUNT(*) FROM $table WHERE vi.channel_id=? GROUP BY vi.channel_id ORDER BY vi.channel_id IS NULL, vi.channel_id")
        }
        it("should build countPlaylistId") {
          val q = VideoRepoSql.countPlaylistId(Url.Videos.Playlist.Id("id"))
          check(q, s"SELECT COUNT(*) FROM $table WHERE vi.playlist_id=? GROUP BY vi.playlist_id ORDER BY vi.playlist_id IS NULL, vi.playlist_id")
        }
      }
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
