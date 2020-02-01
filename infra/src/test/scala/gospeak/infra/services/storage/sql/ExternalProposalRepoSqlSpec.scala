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
        check(q, s"UPDATE $table SET title=?, duration=?, description=?, slides=?, video=?, tags=?, updated_at=?, updated_by=? WHERE id=?")
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
    }
  }
}

object ExternalProposalRepoSqlSpec {
  val table = "external_proposals ep"
  val fields: String = mapFields("id, talk_id, event_id, title, duration, description, speakers, slides, video, tags, created_at, created_by, updated_at, updated_by", "ep." + _)
  val orderBy = "ORDER BY ep.title IS NULL, ep.title, ep.created_at IS NULL, ep.created_at"
}
