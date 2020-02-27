package gospeak.infra.services.storage.sql

import gospeak.core.domain.ExternalCfp
import gospeak.infra.services.storage.sql.ExternalCfpRepoSqlSpec._
import gospeak.infra.services.storage.sql.ExternalEventRepoSqlSpec.{table => eventTable, fields => eventFields}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class ExternalCfpRepoSqlSpec extends RepoSpec {
  describe("ExternalCfpRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = ExternalCfpRepoSql.insert(externalCfp)
        check(q, s"INSERT INTO ${table.stripSuffix(" ec")} (${mapFields(fields, _.stripPrefix("ec."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = ExternalCfpRepoSql.update(externalCfp.id)(externalCfp.data, user.id, now)
        check(q, s"UPDATE $table SET description=?, begin=?, close=?, url=?, updated_at=?, updated_by=? WHERE id=?")
      }
      it("should build selectAll for event") {
        val q = ExternalCfpRepoSql.selectAll(externalEvent.id)
        check(q, s"SELECT $fields FROM $table WHERE ec.event_id=? $orderBy")
      }
      it("should build selectOneFull for cfp id") {
        val q = ExternalCfpRepoSql.selectOneFull(externalCfp.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE ec.id=? $orderBy LIMIT 1")
      }
      it("should build selectOneCommon for internal") {
        val q = ExternalCfpRepoSql.selectOneCommon(cfp.slug)
        val req = s"SELECT $commonFields FROM $commonTable WHERE c.slug=? $commonOrderBy LIMIT 1"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectOneCommon for external") {
        val q = ExternalCfpRepoSql.selectOneCommon(externalCfp.id)
        val req = s"SELECT $commonFields FROM $commonTable WHERE c.id=? $commonOrderBy LIMIT 1"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectCommonPage") {
        val q = ExternalCfpRepoSql.selectCommonPageIncoming(params)
        val req = s"SELECT $commonFields FROM $commonTable " +
          s"WHERE (c.close IS NULL OR c.close >= ?) " +
          s"ORDER BY c.close IS NULL, c.close, c.name IS NULL, c.name " +
          s"LIMIT 20 OFFSET 0"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectDuplicatesFull") {
        val q = ExternalCfpRepoSql.selectDuplicatesFull(ExternalCfp.DuplicateParams(
          cfpUrl = Some("a"),
          cfpName = Some("a"),
          cfpEndDate = Some(ldt),
          eventUrl = Some("a"),
          eventStartDate = Some(ldt),
          twitterAccount = Some("a"),
          twitterHashtag = Some("a")))
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE ec.url LIKE ? OR ec.close=? $orderBy")
      }
      it("should build selectDuplicatesFull empty") {
        val q = ExternalCfpRepoSql.selectDuplicatesFull(ExternalCfp.DuplicateParams.defaults)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE ec.id='no-match' $orderBy")
      }
    }
  }
}

object ExternalCfpRepoSqlSpec {
  val table = "external_cfps ec"
  val fields: String = mapFields("id, event_id, description, begin, close, url, created_at, created_by, updated_at, updated_by", "ec." + _)
  val orderBy = "ORDER BY ec.close IS NULL, ec.close"

  val tableFull = s"$table INNER JOIN $eventTable ON ec.event_id=ee.id"
  val fieldsFull = s"$fields, $eventFields"

  val commonTable: String = "(" +
    "(SELECT c.name, g.logo, c.begin, c.close, g.location, c.description, c.tags, null as ext_id, null  as ext_url, null    as ext_event_start, null     as ext_event_finish, null  as ext_event_url, null          as ext_tickets_url, null         as ext_videos_url, null as twitter_account, null as twitter_hashtag, c.slug as int_slug, g.id as group_id, g.slug as group_slug FROM cfps c INNER JOIN groups g ON c.group_id=g.id) UNION " +
    "(SELECT e.name, e.logo, c.begin, c.close, e.location, c.description, e.tags, c.id as ext_id, c.url as ext_url, e.start as ext_event_start, e.finish as ext_event_finish, e.url as ext_event_url, e.tickets_url as ext_tickets_url, e.videos_url as ext_videos_url,       e.twitter_account,       e.twitter_hashtag, null   as int_slug, null as group_id,   null as group_slug FROM external_cfps c INNER JOIN external_events e ON c.event_id=e.id)) c"
  val commonFields: String = mapFields("name, logo, begin, close, location, description, tags, ext_id, ext_url, ext_event_start, ext_event_finish, ext_event_url, ext_tickets_url, ext_videos_url, twitter_account, twitter_hashtag, int_slug, group_id, group_slug", "c." + _)
  val commonOrderBy = "ORDER BY c.close IS NULL, c.close, c.name IS NULL, c.name"
}
