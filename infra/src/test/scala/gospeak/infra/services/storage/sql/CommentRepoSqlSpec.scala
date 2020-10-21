package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.CommentRepoSql._
import gospeak.infra.services.storage.sql.CommentRepoSqlSpec._
import gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class CommentRepoSqlSpec extends RepoSpec {
  describe("CommentRepoSql") {
    it("should add and fetch a comment") {
      val (user, group, cfp, partner, venue, contact, event, talk, proposal, ctx) = createProposal().unsafeRunSync()
      commentRepo.getComments(proposal.id).unsafeRunSync().map(_.comment) shouldBe List()
      commentRepo.getOrgaComments(proposal.id).unsafeRunSync().map(_.comment) shouldBe List()
      val comment1 = commentRepo.addComment(proposal.id, commentData1.copy(answers = None))(ctx).unsafeRunSync()
      comment1.data shouldBe commentData1.copy(answers = None)
      commentRepo.getComments(proposal.id).unsafeRunSync().map(_.comment) shouldBe List(comment1)
      commentRepo.getOrgaComments(proposal.id).unsafeRunSync().map(_.comment) shouldBe List()
      val comment2 = commentRepo.addOrgaComment(proposal.id, commentData2.copy(answers = None))(ctx).unsafeRunSync()
      comment2.data shouldBe commentData2.copy(answers = None)
      commentRepo.getComments(proposal.id).unsafeRunSync().map(_.comment) shouldBe List(comment1)
      commentRepo.getOrgaComments(proposal.id).unsafeRunSync().map(_.comment) shouldBe List(comment2)

      commentRepo.getComments(event.id).unsafeRunSync().map(_.comment) shouldBe List()
      val comment3 = commentRepo.addComment(event.id, commentData3.copy(answers = None))(ctx).unsafeRunSync()
      comment3.data shouldBe commentData3.copy(answers = None)
      commentRepo.getComments(event.id).unsafeRunSync().map(_.comment) shouldBe List(comment3)
    }
    it("should check queries") {
      check(insert(event.id, comment), s"INSERT INTO ${table.stripSuffix(" co")} (${mapFields(fields, _.stripPrefix("co."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(insert(proposal.id, comment), s"INSERT INTO ${table.stripSuffix(" co")} (${mapFields(fields, _.stripPrefix("co."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(selectAll(event.id, comment.kind), s"SELECT $fieldsFull FROM $tableFull WHERE co.event_id=? AND co.kind=? $orderBy")
      check(selectAll(proposal.id, comment.kind), s"SELECT $fieldsFull FROM $tableFull WHERE co.proposal_id=? AND co.kind=? $orderBy")
    }
  }
}

object CommentRepoSqlSpec {
  val table = "comments co"
  val selectFields: String = mapFields("id, kind, answers, text, created_at, created_by", "co." + _)
  val fields: String = mapFields("event_id, proposal_id", "co." + _) + ", " + selectFields
  val orderBy = "ORDER BY co.created_at IS NULL, co.created_at"

  private val tableFull = s"$table INNER JOIN $userTable ON co.created_by=u.id"
  private val fieldsFull = s"$selectFields, $userFields"
}
