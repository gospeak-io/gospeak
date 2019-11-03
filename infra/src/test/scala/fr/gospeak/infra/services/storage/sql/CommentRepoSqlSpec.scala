package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.CommentRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class CommentRepoSqlSpec extends RepoSpec {
  describe("CommentRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = CommentRepoSql.insert((event.id, comment))
        check(q, s"INSERT INTO ${table.stripSuffix(" co")} (${mapFields(fields, _.stripPrefix("co."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build selectAll") {
        val q = CommentRepoSql.selectAll(event.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE co.event_id=? $orderBy")
      }
    }
  }
}

object CommentRepoSqlSpec {

  import RepoSpec._

  val table = "comments co"
  val selectFields: String = mapFields("id, kind, answers, text, created_by, created_at", "co." + _)
  val fields: String = mapFields("event_id, proposal_id", "co." + _) + ", " + selectFields
  val orderBy = "ORDER BY co.created_at IS NULL, co.created_at"

  private val tableFull = s"$table INNER JOIN $userTable ON co.created_by=u.id"
  private val fieldsFull = s"$selectFields, $userFields"
}
