package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits.toSqlInterpolator
import fr.loicknuchel.safeql.{AggField, Query}
import gospeak.core.domain.{DbStats, User}
import doobie.syntax.connectionio._
import gospeak.core.services.storage.AdminRepo
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.infra.services.storage.sql.utils.DoobieMappings._

import java.time.Instant

class AdminRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with AdminRepo {
  def getStats(since: Option[Instant]): IO[DbStats] = for {
    cfps <- CFPS.select.fields(AggField("count(*)")).where(since.map(CFPS.CREATED_AT.gt(_)).getOrElse(CFPS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    comments <- COMMENTS.select.fields(AggField("count(*)")).where(since.map(COMMENTS.CREATED_AT.gt(_)).getOrElse(COMMENTS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    contacts <- CONTACTS.select.fields(AggField("count(*)")).where(since.map(CONTACTS.CREATED_AT.gt(_)).getOrElse(CONTACTS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    credentials <- CREDENTIALS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    events <- EVENTS.select.fields(AggField("count(*)")).where(since.map(EVENTS.CREATED_AT.gt(_)).getOrElse(EVENTS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    eventRsvps <- EVENT_RSVPS.select.fields(AggField("count(*)")).where(since.map(EVENT_RSVPS.ANSWERED_AT.gt(_)).getOrElse(EVENT_RSVPS.ANSWERED_AT.notNull)).orderBy().one[Long].run(xa)
    externalCfps <- EXTERNAL_CFPS.select.fields(AggField("count(*)")).where(since.map(EXTERNAL_CFPS.CREATED_AT.gt(_)).getOrElse(EXTERNAL_CFPS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    externalEvents <- EXTERNAL_EVENTS.select.fields(AggField("count(*)")).where(since.map(EXTERNAL_EVENTS.CREATED_AT.gt(_)).getOrElse(EXTERNAL_EVENTS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    externalProposals <- EXTERNAL_PROPOSALS.select.fields(AggField("count(*)")).where(since.map(EXTERNAL_PROPOSALS.CREATED_AT.gt(_)).getOrElse(EXTERNAL_PROPOSALS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    flywaySchemaHistory <- fr"SELECT count(*) FROM flyway_schema_history".query[Long].unique.transact(xa)
    groups <- GROUPS.select.fields(AggField("count(*)")).where(since.map(GROUPS.CREATED_AT.gt(_)).getOrElse(GROUPS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    groupSettings <- GROUP_SETTINGS.select.fields(AggField("count(*)")).where(since.map(GROUP_SETTINGS.UPDATED_AT.gt(_)).getOrElse(GROUP_SETTINGS.UPDATED_AT.notNull)).orderBy().one[Long].run(xa)
    groupMembers <- GROUP_MEMBERS.select.fields(AggField("count(*)")).where(since.map(GROUP_MEMBERS.JOINED_AT.gt(_)).getOrElse(GROUP_MEMBERS.JOINED_AT.notNull)).orderBy().one[Long].run(xa)
    logins <- LOGINS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    partners <- PARTNERS.select.fields(AggField("count(*)")).where(since.map(PARTNERS.CREATED_AT.gt(_)).getOrElse(PARTNERS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    proposals <- PROPOSALS.select.fields(AggField("count(*)")).where(since.map(PROPOSALS.CREATED_AT.gt(_)).getOrElse(PROPOSALS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    proposalRatings <- PROPOSAL_RATINGS.select.fields(AggField("count(*)")).where(since.map(PROPOSAL_RATINGS.CREATED_AT.gt(_)).getOrElse(PROPOSAL_RATINGS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    sponsors <- SPONSORS.select.fields(AggField("count(*)")).where(since.map(SPONSORS.CREATED_AT.gt(_)).getOrElse(SPONSORS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    sponsorPacks <- SPONSOR_PACKS.select.fields(AggField("count(*)")).where(since.map(SPONSOR_PACKS.CREATED_AT.gt(_)).getOrElse(SPONSOR_PACKS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    talks <- TALKS.select.fields(AggField("count(*)")).where(since.map(TALKS.CREATED_AT.gt(_)).getOrElse(TALKS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    users <- USERS.select.fields(AggField("count(*)")).where(since.map(USERS.CREATED_AT.gt(_)).getOrElse(USERS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    userRequests <- USER_REQUESTS.select.fields(AggField("count(*)")).where(since.map(USER_REQUESTS.CREATED_AT.gt(_)).getOrElse(USER_REQUESTS.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    venues <- VENUES.select.fields(AggField("count(*)")).where(since.map(VENUES.CREATED_AT.gt(_)).getOrElse(VENUES.CREATED_AT.notNull)).orderBy().one[Long].run(xa)
    videos <- VIDEOS.select.fields(AggField("count(*)")).where(since.map(VIDEOS.UPDATED_AT.gt(_)).getOrElse(VIDEOS.UPDATED_AT.notNull)).orderBy().one[Long].run(xa)
    videoSources <- VIDEO_SOURCES.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
  } yield DbStats(Map(
    "cfps" -> cfps,
    "comments" -> comments,
    "contacts" -> contacts,
    "credentials" -> credentials,
    "events" -> events,
    "eventRsvps" -> eventRsvps,
    "externalCfps" -> externalCfps,
    "externalEvents" -> externalEvents,
    "externalProposals" -> externalProposals,
    "flywaySchemaHistory" -> flywaySchemaHistory,
    "groups" -> groups,
    "groupSettings" -> groupSettings,
    "groupMembers" -> groupMembers,
    "logins" -> logins,
    "partners" -> partners,
    "proposals" -> proposals,
    "proposalRatings" -> proposalRatings,
    "sponsors" -> sponsors,
    "sponsorPacks" -> sponsorPacks,
    "talks" -> talks,
    "users" -> users,
    "userRequests" -> userRequests,
    "venues" -> venues,
    "videos" -> videos,
    "videoSources" -> videoSources))
}
