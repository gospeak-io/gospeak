package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.VideoRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec

class VideoRepoSqlSpec extends RepoSpec {
  describe("VideoRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = VideoRepoSql.insert(video)
        check(q, s"INSERT INTO ${table.stripSuffix(" vi")} (${mapFields(fieldsInsert, _.stripPrefix("vi."))}) VALUES (${mapFields(fieldsInsert, _ => "?")})")
      }
      it("should build update") {
        val q = VideoRepoSql.update(video.data, now)
        check(q, s"UPDATE $table SET title=?, description=?, tags=?, duration=?, lang=?, views=?, likes=?, dislikes=?, comments=?, updated_at=? WHERE id=?")
      }
      it("should build selectOne by id") {
        val q = VideoRepoSql.selectOne(video.url.videoId)
        check(q, s"SELECT $fields FROM $table WHERE vi.id=? $orderBy")
      }
      it("should build selectPage") {
        val q = VideoRepoSql.selectPage(params)
        check(q, s"SELECT $fields FROM $table $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build countChannelId") {
        val q = VideoRepoSql.countChannelId("id")
        check(q, s"SELECT COUNT(*) FROM $table WHERE vi.channel_id=? GROUP BY vi.channel_id ORDER BY vi.channel_id IS NULL, vi.channel_id")
      }
      it("should build countPlaylistId") {
        val q = VideoRepoSql.countPlaylistId("id")
        check(q, s"SELECT COUNT(*) FROM $table WHERE vi.playlist_id=? GROUP BY vi.playlist_id ORDER BY vi.playlist_id IS NULL, vi.playlist_id")
      }
    }
  }
}

object VideoRepoSqlSpec {

  import RepoSpec._

  val table = "videos vi"
  val fieldsInsert: String = mapFields("platform, url, id, channel_id, channel_name, playlist_id, playlist_name, title, description, tags, published_at, duration, lang, views, likes, dislikes, comments, updated_at", "vi." + _)
  val fields: String = mapFields("url, channel_id, channel_name, playlist_id, playlist_name, title, description, tags, published_at, duration, lang, views, likes, dislikes, comments, updated_at", "vi." + _)
  val orderBy = "ORDER BY vi.title IS NULL, vi.title, vi.published_at IS NULL, vi.published_at"
}
