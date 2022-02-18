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

class AdminRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with AdminRepo {
  def getStats(): IO[DbStats] = for {
    cfps <- CFPS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    comments <- COMMENTS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    contacts <- CONTACTS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    credentials <- CREDENTIALS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    events <- EVENTS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    eventRsvps <- EVENT_RSVPS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    externalCfps <- EXTERNAL_CFPS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    externalEvents <- EXTERNAL_EVENTS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    externalProposals <- EXTERNAL_PROPOSALS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    flywaySchemaHistory <- fr"SELECT count(*) FROM flyway_schema_history".query[Long].unique.transact(xa)
    groups <- GROUPS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    groupSettings <- GROUP_SETTINGS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    groupMembers <- GROUP_MEMBERS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    logins <- LOGINS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    partners <- PARTNERS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    proposals <- PROPOSALS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    proposalRatings <- PROPOSAL_RATINGS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    sponsors <- SPONSORS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    sponsorPacks <- SPONSOR_PACKS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    talks <- TALKS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    users <- USERS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    userRequests <- USER_REQUESTS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    venues <- VENUES.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
    videos <- VIDEOS.select.fields(AggField("count(*)")).orderBy().one[Long].run(xa)
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
