package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.ExternalProposalRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class ExternalProposalRepoSqlSpec extends RepoSpec {
  describe("ExternalProposalRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = ExternalProposalRepoSql.insert(externalProposal)
        check(q, s"INSERT INTO ${table.stripSuffix(" ep")} (${mapFields(fields, _.stripPrefix("ep."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = ExternalProposalRepoSql.update(externalProposal.id)(externalProposal.data, user.id, now)
        check(q, s"UPDATE $table SET title=?, duration=?, description=?, slides=?, video=?, tags=?, updated_at=?, updated_by=? WHERE id=? AND speakers LIKE ?")
      }
      it("should build delete") {
        val q = ExternalProposalRepoSql.delete(externalProposal.id, user.id)
        check(q, s"DELETE FROM $table WHERE ep.id=? AND ep.speakers LIKE ?")
      }
      it("should build selectOne") {
        val q = ExternalProposalRepoSql.selectOne(externalProposal.id)
        check(q, s"SELECT $fields FROM $table WHERE ep.id=? LIMIT 1")
      }
      it("should build selectPage") {
        val q = ExternalProposalRepoSql.selectPage(params)
        val req = s"SELECT $fields FROM $table $orderBy LIMIT 20 OFFSET 0"
        q.fr.query.sql shouldBe req
        check(q, req)
      }
      it("should build selectAllCommon") {
        val q = ExternalProposalRepoSql.selectAllCommon(talk.id)
        val req = s"SELECT $commonFields FROM $commonTable WHERE p.talk_id=? $commonOrderBy"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectAll") {
        val q = ExternalProposalRepoSql.selectAll(talk.id)
        check(q, s"SELECT $fields FROM $table WHERE ep.talk_id=? $orderBy")
      }
    }
  }
}

object ExternalProposalRepoSqlSpec {
  val table = "external_proposals ep"
  val fields: String = mapFields("id, talk_id, event_id, title, duration, description, speakers, slides, video, tags, created_at, created_by, updated_at, updated_by", "ep." + _)
  val orderBy = "ORDER BY ep.title IS NULL, ep.title, ep.created_at IS NULL, ep.created_at"

  val commonTable: String = "(" +
    "(SELECT p.id, false as external, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, c.id as cfp_id, c.slug as cfp_slug, c.name as cfp_name, e.id as event_id, e.slug as event_slug, e.name as event_name, e.start as event_start, null as event_ext_id, null   as event_ext_name, null    as event_ext_start, p.title,       p.status, p.duration, p.tags, p.created_at, p.created_by, p.updated_at, p.updated_by FROM proposals p          INNER JOIN talks t ON p.talk_id=t.id LEFT OUTER JOIN events e     ON p.event_id=e.id INNER JOIN cfps c ON p.cfp_id=c.id) UNION " +
    "(SELECT p.id, true  as external, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, null as cfp_id, null   as cfp_slug, null   as cfp_name, null as event_id, null   as event_slug, null   as event_name, null    as event_start, e.id as event_ext_id, e.name as event_ext_name, e.start as event_ext_start, p.title, null as status, p.duration, p.tags, p.created_at, p.created_by, p.updated_at, p.updated_by FROM external_proposals p INNER JOIN talks t ON p.talk_id=t.id INNER JOIN external_events e ON p.event_id=e.id)) p"
  val commonFields = "p.id, p.external, p.talk_id, p.talk_slug, p.talk_duration, p.cfp_id, p.cfp_slug, p.cfp_name, p.event_id, p.event_slug, p.event_name, p.event_start, p.event_ext_id, p.event_ext_name, p.event_ext_start, p.title, p.status, p.duration, p.tags, p.created_at, p.created_by, p.updated_at, p.updated_by"
  val commonOrderBy = "ORDER BY p.created_at IS NULL, p.created_at DESC"
}
