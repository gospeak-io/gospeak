package gospeak.infra.services.storage.sql.database.tables

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import fr.loicknuchel.safeql.Table._
import fr.loicknuchel.safeql._
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup, MeetupUser, MeetupVenue}
import gospeak.core.services.slack.domain.SlackToken
import gospeak.libs.scala.domain._

import scala.concurrent.duration.FiniteDuration

/**
 * Generated file, do not update it!
 *
 * Regenerate it using Gospeak CLI (`gospeak.web.GsCLI` class) to keep it in sync with the database state.
 *
 * --
 *
 * Class generated by fr.loicknuchel.safeql.gen.writer.ScalaWriter
 */
class CFPS private(getAlias: Option[String] = Some("c")) extends Table.SqlTable("PUBLIC", "cfps", getAlias) {
  type Self = CFPS

  val ID: SqlFieldRaw[Cfp.Id, CFPS] = SqlField(this, "id", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 1)
  val GROUP_ID: SqlFieldRef[Group.Id, CFPS, GROUPS] = SqlField(this, "group_id", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 2, GROUPS.table.ID)
  val SLUG: SqlFieldRaw[Cfp.Slug, CFPS] = SqlField(this, "slug", "VARCHAR(120) NOT NULL", JdbcType.VarChar, nullable = false, 3)
  val NAME: SqlFieldRaw[Cfp.Name, CFPS] = SqlField(this, "name", "VARCHAR(120) NOT NULL", JdbcType.VarChar, nullable = false, 4)
  val BEGIN: SqlFieldRaw[LocalDateTime, CFPS] = SqlField(this, "begin", "TIMESTAMP", JdbcType.Timestamp, nullable = true, 5)
  val CLOSE: SqlFieldRaw[LocalDateTime, CFPS] = SqlField(this, "close", "TIMESTAMP", JdbcType.Timestamp, nullable = true, 6)
  val DESCRIPTION: SqlFieldRaw[Markdown, CFPS] = SqlField(this, "description", "VARCHAR(4096) NOT NULL", JdbcType.VarChar, nullable = false, 7)
  val TAGS: SqlFieldRaw[List[Tag], CFPS] = SqlField(this, "tags", "VARCHAR(150) NOT NULL", JdbcType.VarChar, nullable = false, 8)
  val CREATED_AT: SqlFieldRaw[Instant, CFPS] = SqlField(this, "created_at", "TIMESTAMP NOT NULL", JdbcType.Timestamp, nullable = false, 9)
  val CREATED_BY: SqlFieldRef[User.Id, CFPS, USERS] = SqlField(this, "created_by", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 10, USERS.table.ID)
  val UPDATED_AT: SqlFieldRaw[Instant, CFPS] = SqlField(this, "updated_at", "TIMESTAMP NOT NULL", JdbcType.Timestamp, nullable = false, 11)
  val UPDATED_BY: SqlFieldRef[User.Id, CFPS, USERS] = SqlField(this, "updated_by", "CHAR(36) NOT NULL", JdbcType.Char, nullable = false, 12, USERS.table.ID)

  override def getFields: List[SqlField[_, CFPS]] = List(ID, GROUP_ID, SLUG, NAME, BEGIN, CLOSE, DESCRIPTION, TAGS, CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY)

  override def getSorts: List[Sort] = List(Sort("date", "date", NonEmptyList.of(CLOSE.desc, NAME.asc)))

  override def searchOn: List[SqlField[_, CFPS]] = List(ID, SLUG, NAME, DESCRIPTION, TAGS)

  override def getFilters: List[Filter] = List()

  def alias(alias: String): CFPS = new CFPS(Some(alias))
}

private[database] object CFPS {
  val table = new CFPS() // table instance, should be accessed through `gospeak.infra.services.storage.sql.database.Tables` object
}
