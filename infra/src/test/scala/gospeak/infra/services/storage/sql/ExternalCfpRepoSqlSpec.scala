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
      it("should build selectOneFull for cfp id") {
        val q = ExternalCfpRepoSql.selectOneFull(externalCfp.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE ec.id=? LIMIT 1")
      }
      it("should build selectOneCommon for internal") {
        val q = ExternalCfpRepoSql.selectOneCommon(cfp.slug)
        val req = s"SELECT $commonFields FROM $commonTable WHERE c.slug=? LIMIT 1"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectOneCommon for external") {
        val q = ExternalCfpRepoSql.selectOneCommon(externalCfp.id)
        val req = s"SELECT $commonFields FROM $commonTable WHERE c.id=? LIMIT 1"
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
  val orderBy = "ORDER BY ec.close IS NULL, ec.close, ec.id IS NULL, ec.id"

  val tableFull = s"$table INNER JOIN $eventTable ON ec.event_id=ee.id"
  val fieldsFull = s"$fields, $eventFields"

  val commonTable: String = "(" +
    "(SELECT c.id,       c.slug, c.name, g.logo, null as url, c.begin, c.close, g.location, c.description, null    as event_start, null     as event_finish, null  as event_url, null as tickets_url, null as videos_url, null as twitter_account, null as twitter_hashtag, c.tags, g.id as group_id, g.slug as group_slug FROM cfps c INNER JOIN groups g ON c.group_id=g.id) UNION " +
    "(SELECT c.id, null as slug, e.name, e.logo,       c.url, c.begin, c.close, e.location, c.description, e.start as event_start, e.finish as event_finish, e.url as event_url,       e.tickets_url,       e.videos_url,       e.twitter_account,       e.twitter_hashtag, e.tags, null as group_id,   null as group_slug FROM external_cfps c INNER JOIN external_events e ON c.event_id=e.id)) c"
  val commonFields = "c.id, c.slug, c.name, c.logo, c.url, c.begin, c.close, c.location, c.description, c.event_start, c.event_finish, c.event_url, c.tickets_url, c.videos_url, c.twitter_account, c.twitter_hashtag, c.tags, c.group_id, c.group_slug"
}
