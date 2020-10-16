package gospeak.infra.services.storage.sql.database.tables

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup, MeetupUser, MeetupVenue}
import gospeak.core.services.slack.domain.SlackToken
import gospeak.libs.scala.domain._
import gospeak.libs.sql.dsl.Table._
import gospeak.libs.sql.dsl._

import scala.concurrent.duration.FiniteDuration

/**
 * Generated file, do not update it!
 *
 * Regenerate it using Gospeak CLI (`gospeak.web.GsCLI` class) to keep it in sync with the database state.
 *
 * --
 *
 * Class generated by gospeak.libs.sql.generator.writer.ScalaWriter
 */
class VIDEOS private(getAlias: Option[String] = Some("vi")) extends Table.SqlTable("PUBLIC", "videos", getAlias) {
  type Self = VIDEOS

  val PLATFORM: SqlField[String, VIDEOS] = SqlField(this, "platform", "VARCHAR(10) NOT NULL", JdbcType.VarChar, nullable = false, 1)
  val URL: SqlField[Url.Video, VIDEOS] = SqlField(this, "url", "VARCHAR(1024) NOT NULL", JdbcType.VarChar, nullable = false, 2)
  val ID: SqlField[Url.Video.Id, VIDEOS] = SqlField(this, "id", "VARCHAR(15) NOT NULL", JdbcType.VarChar, nullable = false, 3)
  val CHANNEL_ID: SqlField[Url.Videos.Channel.Id, VIDEOS] = SqlField(this, "channel_id", "VARCHAR(30) NOT NULL", JdbcType.VarChar, nullable = false, 4)
  val CHANNEL_NAME: SqlField[String, VIDEOS] = SqlField(this, "channel_name", "VARCHAR(120) NOT NULL", JdbcType.VarChar, nullable = false, 5)
  val PLAYLIST_ID: SqlField[Url.Videos.Playlist.Id, VIDEOS] = SqlField(this, "playlist_id", "VARCHAR(40)", JdbcType.VarChar, nullable = true, 6)
  val PLAYLIST_NAME: SqlField[String, VIDEOS] = SqlField(this, "playlist_name", "VARCHAR(120)", JdbcType.VarChar, nullable = true, 7)
  val TITLE: SqlField[String, VIDEOS] = SqlField(this, "title", "VARCHAR(120) NOT NULL", JdbcType.VarChar, nullable = false, 8)
  val DESCRIPTION: SqlField[String, VIDEOS] = SqlField(this, "description", "VARCHAR(4096) NOT NULL", JdbcType.VarChar, nullable = false, 9)
  val TAGS: SqlField[List[Tag], VIDEOS] = SqlField(this, "tags", "VARCHAR(150) NOT NULL", JdbcType.VarChar, nullable = false, 10)
  val PUBLISHED_AT: SqlField[Instant, VIDEOS] = SqlField(this, "published_at", "TIMESTAMP NOT NULL", JdbcType.Timestamp, nullable = false, 11)
  val DURATION: SqlField[FiniteDuration, VIDEOS] = SqlField(this, "duration", "BIGINT NOT NULL", JdbcType.BigInt, nullable = false, 12)
  val LANG: SqlField[String, VIDEOS] = SqlField(this, "lang", "VARCHAR(2) NOT NULL", JdbcType.VarChar, nullable = false, 13)
  val VIEWS: SqlField[Long, VIDEOS] = SqlField(this, "views", "BIGINT NOT NULL", JdbcType.BigInt, nullable = false, 14)
  val LIKES: SqlField[Long, VIDEOS] = SqlField(this, "likes", "BIGINT NOT NULL", JdbcType.BigInt, nullable = false, 15)
  val DISLIKES: SqlField[Long, VIDEOS] = SqlField(this, "dislikes", "BIGINT NOT NULL", JdbcType.BigInt, nullable = false, 16)
  val COMMENTS: SqlField[Long, VIDEOS] = SqlField(this, "comments", "BIGINT NOT NULL", JdbcType.BigInt, nullable = false, 17)
  val UPDATED_AT: SqlField[Instant, VIDEOS] = SqlField(this, "updated_at", "TIMESTAMP NOT NULL", JdbcType.Timestamp, nullable = false, 18)

  override def getFields: List[SqlField[_, VIDEOS]] = List(PLATFORM, URL, ID, CHANNEL_ID, CHANNEL_NAME, PLAYLIST_ID, PLAYLIST_NAME, TITLE, DESCRIPTION, TAGS, PUBLISHED_AT, DURATION, LANG, VIEWS, LIKES, DISLIKES, COMMENTS, UPDATED_AT)

  override def getSorts: List[Sort] = List(Sort("title", "title", NonEmptyList.of(TITLE.asc, PUBLISHED_AT.asc)))

  override def searchOn: List[SqlField[_, VIDEOS]] = List(ID, CHANNEL_NAME, PLAYLIST_NAME, TITLE, DESCRIPTION, TAGS)

  override def getFilters: List[Filter] = List()

  def alias(alias: String): VIDEOS = new VIDEOS(Some(alias))
}

private[database] object VIDEOS {
  val table = new VIDEOS() // table instance, should be accessed through `gospeak.infra.services.storage.sql.database.Tables` object
}
