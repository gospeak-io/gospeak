package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.CommentRepoSqlSpec._
import gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec

class CommentRepoSqlSpec extends RepoSpec {
  describe("CommentRepoSql") {
    describe("Queries") {
      it("should build insert with event") {
        val q = CommentRepoSql.insert(event.id, comment)
        check(q, s"INSERT INTO ${table.stripSuffix(" co")} (${mapFields(fields, _.stripPrefix("co."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build insert with proposal") {
        val q = CommentRepoSql.insert(proposal.id, comment)
        check(q, s"INSERT INTO ${table.stripSuffix(" co")} (${mapFields(fields, _.stripPrefix("co."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build selectAll with event") {
        val q = CommentRepoSql.selectAll(event.id, comment.kind)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE co.event_id=? AND co.kind=? $orderBy")
      }
      it("should build selectAll with proposal") {
        val q = CommentRepoSql.selectAll(proposal.id, comment.kind)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE co.proposal_id=? AND co.kind=? $orderBy")
      }
    }
  }
}

object CommentRepoSqlSpec {

  import RepoSpec._

  val table = "comments co"
  val selectFields: String = mapFields("id, kind, answers, text, created_at, created_by", "co." + _)
  val fields: String = mapFields("event_id, proposal_id", "co." + _) + ", " + selectFields
  val orderBy = "ORDER BY co.created_at IS NULL, co.created_at"

  private val tableFull = s"$table INNER JOIN $userTable ON co.created_by=u.id"
  private val fieldsFull = s"$selectFields, $userFields"
}
