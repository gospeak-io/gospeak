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
class EXTERNAL_EVENTS private(getAlias: Option[String] = Some("ee")) extends Table.SqlTable("PUBLIC", "external_events", getAlias) {
  type Self = EXTERNAL_EVENTS

  val ID: SqlField[ExternalEvent.Id, EXTERNAL_EVENTS] = SqlField(this, "id", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 1)
  val NAME: SqlField[Event.Name, EXTERNAL_EVENTS] = SqlField(this, "name", "VARCHAR(120) NOT NULL", JdbcType.VarChar, nullable = false, 2)
  val KIND: SqlField[Event.Kind, EXTERNAL_EVENTS] = SqlField(this, "kind", "VARCHAR(12) DEFAULT 'Conference' NOT NULL", JdbcType.VarChar, nullable = false, 23)
  val LOGO: SqlField[Logo, EXTERNAL_EVENTS] = SqlField(this, "logo", "VARCHAR(1024)", JdbcType.VarChar, nullable = true, 3)
  val DESCRIPTION: SqlField[Markdown, EXTERNAL_EVENTS] = SqlField(this, "description", "VARCHAR(4096) NOT NULL", JdbcType.VarChar, nullable = false, 4)
  val START: SqlField[LocalDateTime, EXTERNAL_EVENTS] = SqlField(this, "start", "TIMESTAMP", JdbcType.Timestamp, nullable = true, 5)
  val FINISH: SqlField[LocalDateTime, EXTERNAL_EVENTS] = SqlField(this, "finish", "TIMESTAMP", JdbcType.Timestamp, nullable = true, 6)
  val LOCATION: SqlField[GMapPlace, EXTERNAL_EVENTS] = SqlField(this, "location", "VARCHAR(4096)", JdbcType.VarChar, nullable = true, 7)
  val LOCATION_ID: SqlField[String, EXTERNAL_EVENTS] = SqlField(this, "location_id", "VARCHAR(150)", JdbcType.VarChar, nullable = true, 8)
  val LOCATION_LAT: SqlField[Double, EXTERNAL_EVENTS] = SqlField(this, "location_lat", "DOUBLE PRECISION", JdbcType.Double, nullable = true, 9)
  val LOCATION_LNG: SqlField[Double, EXTERNAL_EVENTS] = SqlField(this, "location_lng", "DOUBLE PRECISION", JdbcType.Double, nullable = true, 10)
  val LOCATION_LOCALITY: SqlField[String, EXTERNAL_EVENTS] = SqlField(this, "location_locality", "VARCHAR(50)", JdbcType.VarChar, nullable = true, 11)
  val LOCATION_COUNTRY: SqlField[String, EXTERNAL_EVENTS] = SqlField(this, "location_country", "VARCHAR(30)", JdbcType.VarChar, nullable = true, 12)
  val URL: SqlField[Url, EXTERNAL_EVENTS] = SqlField(this, "url", "VARCHAR(1024)", JdbcType.VarChar, nullable = true, 13)
  val TICKETS_URL: SqlField[Url, EXTERNAL_EVENTS] = SqlField(this, "tickets_url", "VARCHAR(1024)", JdbcType.VarChar, nullable = true, 14)
  val VIDEOS_URL: SqlField[Url.Videos, EXTERNAL_EVENTS] = SqlField(this, "videos_url", "VARCHAR(1024)", JdbcType.VarChar, nullable = true, 15)
  val TWITTER_ACCOUNT: SqlField[TwitterAccount, EXTERNAL_EVENTS] = SqlField(this, "twitter_account", "VARCHAR(120)", JdbcType.VarChar, nullable = true, 16)
  val TWITTER_HASHTAG: SqlField[TwitterHashtag, EXTERNAL_EVENTS] = SqlField(this, "twitter_hashtag", "VARCHAR(120)", JdbcType.VarChar, nullable = true, 17)
  val TAGS: SqlField[List[Tag], EXTERNAL_EVENTS] = SqlField(this, "tags", "VARCHAR(150) NOT NULL", JdbcType.VarChar, nullable = false, 18)
  val CREATED_AT: SqlField[Instant, EXTERNAL_EVENTS] = SqlField(this, "created_at", "TIMESTAMP NOT NULL", JdbcType.Timestamp, nullable = false, 19)
  val CREATED_BY: SqlFieldRef[User.Id, EXTERNAL_EVENTS, USERS] = SqlField(this, "created_by", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 20, USERS.table.ID)
  val UPDATED_AT: SqlField[Instant, EXTERNAL_EVENTS] = SqlField(this, "updated_at", "TIMESTAMP NOT NULL", JdbcType.Timestamp, nullable = false, 21)
  val UPDATED_BY: SqlFieldRef[User.Id, EXTERNAL_EVENTS, USERS] = SqlField(this, "updated_by", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 22, USERS.table.ID)

  override def getFields: List[SqlField[_, EXTERNAL_EVENTS]] = List(ID, NAME, KIND, LOGO, DESCRIPTION, START, FINISH, LOCATION, LOCATION_ID, LOCATION_LAT, LOCATION_LNG, LOCATION_LOCALITY, LOCATION_COUNTRY, URL, TICKETS_URL, VIDEOS_URL, TWITTER_ACCOUNT, TWITTER_HASHTAG, TAGS, CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY)

  override def getSorts: List[Sort] = List(Sort("start", "start", NonEmptyList.of(START.desc, NAME.asc)))

  override def searchOn: List[SqlField[_, EXTERNAL_EVENTS]] = List(ID, NAME, DESCRIPTION, LOCATION, URL, TWITTER_ACCOUNT, TWITTER_HASHTAG, TAGS)

  override def getFilters: List[Filter] = List()

  def alias(alias: String): EXTERNAL_EVENTS = new EXTERNAL_EVENTS(Some(alias))
}

private[database] object EXTERNAL_EVENTS {
  val table = new EXTERNAL_EVENTS() // table instance, should be accessed through `gospeak.infra.services.storage.sql.database.Tables` object
}