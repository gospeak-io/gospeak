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
class TALKS private(getAlias: Option[String] = Some("t")) extends Table.SqlTable("PUBLIC", "talks", getAlias) {
  type Self = TALKS

  val ID: SqlField[Talk.Id, TALKS] = SqlField(this, "id", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 1)
  val SLUG: SqlField[Talk.Slug, TALKS] = SqlField(this, "slug", "VARCHAR(120) NOT NULL", JdbcType.VarChar, nullable = false, 2)
  val STATUS: SqlField[Talk.Status, TALKS] = SqlField(this, "status", "VARCHAR(10) NOT NULL", JdbcType.VarChar, nullable = false, 3)
  val TITLE: SqlField[Talk.Title, TALKS] = SqlField(this, "title", "VARCHAR(120) NOT NULL", JdbcType.VarChar, nullable = false, 4)
  val DURATION: SqlField[FiniteDuration, TALKS] = SqlField(this, "duration", "BIGINT NOT NULL", JdbcType.BigInt, nullable = false, 5)
  val DESCRIPTION: SqlField[Markdown, TALKS] = SqlField(this, "description", "VARCHAR(4096) NOT NULL", JdbcType.VarChar, nullable = false, 6)
  val MESSAGE: SqlField[Markdown, TALKS] = SqlField(this, "message", "VARCHAR(4096) DEFAULT '' NOT NULL", JdbcType.VarChar, nullable = false, 15)
  val SPEAKERS: SqlField[NonEmptyList[User.Id], TALKS] = SqlField(this, "speakers", "VARCHAR(184) NOT NULL", JdbcType.VarChar, nullable = false, 7)
  val SLIDES: SqlField[Url.Slides, TALKS] = SqlField(this, "slides", "VARCHAR(1024)", JdbcType.VarChar, nullable = true, 8)
  val VIDEO: SqlField[Url.Video, TALKS] = SqlField(this, "video", "VARCHAR(1024)", JdbcType.VarChar, nullable = true, 9)
  val TAGS: SqlField[List[Tag], TALKS] = SqlField(this, "tags", "VARCHAR(150) NOT NULL", JdbcType.VarChar, nullable = false, 10)
  val CREATED_AT: SqlField[Instant, TALKS] = SqlField(this, "created_at", "TIMESTAMP NOT NULL", JdbcType.Timestamp, nullable = false, 11)
  val CREATED_BY: SqlFieldRef[User.Id, TALKS, USERS] = SqlField(this, "created_by", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 12, USERS.table.ID)
  val UPDATED_AT: SqlField[Instant, TALKS] = SqlField(this, "updated_at", "TIMESTAMP NOT NULL", JdbcType.Timestamp, nullable = false, 13)
  val UPDATED_BY: SqlFieldRef[User.Id, TALKS, USERS] = SqlField(this, "updated_by", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 14, USERS.table.ID)

  override def getFields: List[SqlField[_, TALKS]] = List(ID, SLUG, STATUS, TITLE, DURATION, DESCRIPTION, MESSAGE, SPEAKERS, SLIDES, VIDEO, TAGS, CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY)

  override def getSorts: List[Sort] = List(Sort("title", "title", NonEmptyList.of(Field.Order(STATUS, asc = true, Some("? = 'Archived'")), TITLE.asc)))

  override def searchOn: List[SqlField[_, TALKS]] = List(ID, SLUG, STATUS, TITLE, DESCRIPTION, MESSAGE, TAGS)

  override def getFilters: List[Filter] = List()

  def alias(alias: String): TALKS = new TALKS(Some(alias))
}

private[database] object TALKS {
  val table = new TALKS() // table instance, should be accessed through `gospeak.infra.services.storage.sql.database.Tables` object
}
