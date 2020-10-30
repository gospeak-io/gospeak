package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.User
import gospeak.core.domain.utils.FakeCtx
import gospeak.infra.services.storage.sql.ExternalProposalRepoSqlSpec.{table => externalProposalTable}
import gospeak.infra.services.storage.sql.GroupRepoSqlSpec.{table => groupTable}
import gospeak.infra.services.storage.sql.ProposalRepoSqlSpec.{table => proposalTable}
import gospeak.infra.services.storage.sql.TalkRepoSqlSpec.{table => talkTable}
import gospeak.infra.services.storage.sql.UserRepoSql._
import gospeak.infra.services.storage.sql.UserRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.{mapFields, socialFields}
import gospeak.libs.scala.domain.Markdown

class UserRepoSqlSpec extends RepoSpec {
  describe("UserRepoSql") {
    it("should handle crud operations") {
      userRepo.find(userData1.slug).unsafeRunSync() shouldBe None
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      user.data shouldBe userData1
      val ctx = FakeCtx(now, user)
      userRepo.find(userData1.slug).unsafeRunSync() shouldBe Some(user)

      userRepo.edit(user.id)(userData2, now).unsafeRunSync().data shouldBe userData2
      userRepo.find(userData2.slug).unsafeRunSync().map(_.data) shouldBe Some(userData2)

      userRepo.edit(userData3)(ctx).unsafeRunSync().data shouldBe userData3
      userRepo.find(userData3.slug).unsafeRunSync().map(_.data) shouldBe Some(userData3)
      // no delete...

      userRepo.find(credentials.login).unsafeRunSync() shouldBe None
      userRepo.createLoginRef(credentials.login, user.id).unsafeRunSync()
      userRepo.find(credentials.login).unsafeRunSync().map(_.id) shouldBe Some(user.id)
      // no update and delete...

      userRepo.createCredentials(credentials).unsafeRunSync()
      userRepo.findCredentials(credentials.login).unsafeRunSync() shouldBe Some(credentials)
      userRepo.editCredentials(credentials.login)(credentials2.pass).unsafeRunSync()
      userRepo.removeCredentials(credentials.login).unsafeRunSync()
    }
    it("should fail on duplicate slug") {
      userRepo.create(userData1, now, None).unsafeRunSync()
      an[Exception] should be thrownBy userRepo.create(userData2.copy(slug = userData1.slug), now, None).unsafeRunSync()
    }
    it("should fail on duplicate email") {
      userRepo.create(userData1, now, None).unsafeRunSync()
      an[Exception] should be thrownBy userRepo.create(userData2.copy(email = userData1.email), now, None).unsafeRunSync()
    }
    it("should perform specific updates") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)

      userRepo.editStatus(User.Status.Public)(ctx).unsafeRunSync()
      userRepo.find(userData1.slug).unsafeRunSync().map(_.status) shouldBe Some(User.Status.Public)

      userRepo.editStatus(User.Status.Private)(ctx).unsafeRunSync()
      userRepo.find(userData1.slug).unsafeRunSync().map(_.status) shouldBe Some(User.Status.Private)
    }
    it("should select a page") {
      val user1 = userRepo.create(userData1.copy(status = User.Status.Public, mentoring = Some(Markdown("mmmmmm")), lastName = "bbbbbb"), now, None).unsafeRunSync()
      val user2 = userRepo.create(userData2.copy(status = User.Status.Public, mentoring = None, lastName = "aaaaaa"), now, None).unsafeRunSync()
      val userFull1 = User.Full(user1, 0, 0, 0)
      val userFull2 = User.Full(user2, 0, 0, 0)

      userRepo.listPublic(params).unsafeRunSync().items shouldBe List(userFull1, userFull2)
      userRepo.listPublic(params.page(2)).unsafeRunSync().items shouldBe List()
      userRepo.listPublic(params.pageSize(5)).unsafeRunSync().items shouldBe List(userFull1, userFull2)
      userRepo.listPublic(params.search(user1.lastName)).unsafeRunSync().items shouldBe List(userFull1)
      userRepo.listPublic(params.orderBy("name")).unsafeRunSync().items shouldBe List(userFull2, userFull1)
      userRepo.listPublic(params.filters("mentor" -> "true")).unsafeRunSync().items shouldBe List(userFull1)
      userRepo.listPublic(params.filters("mentor" -> "false")).unsafeRunSync().items shouldBe List(userFull2)
    }
    it("should be able to read correctly") {
      val (user, group, cfp, partner, venue, contact, event, talk, proposal, ctx) = createProposal(userData = userData1.copy(status = User.Status.Public)).unsafeRunSync()
      eventRepo.publish(event.slug)(ctx).unsafeRunSync()
      val userFull = User.Full(user, 1, 1, 1)

      userRepo.find(credentials.login).unsafeRunSync() shouldBe Some(user)
      userRepo.find(credentials).unsafeRunSync() shouldBe Some(user)
      userRepo.find(user.email).unsafeRunSync() shouldBe Some(user)
      userRepo.find(user.slug).unsafeRunSync() shouldBe Some(user)
      userRepo.find(user.id).unsafeRunSync() shouldBe Some(user)
      userRepo.findPublic(user.slug)(ctx.userAwareCtx).unsafeRunSync() shouldBe Some(userFull)
      userRepo.speakers(params)(ctx).unsafeRunSync().items shouldBe List(userFull)
      userRepo.speakersPublic(group.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(userFull)
      userRepo.speakerCountPublic(group.id).unsafeRunSync() shouldBe 1
      userRepo.listAllPublicSlugs()(ctx.userAwareCtx).unsafeRunSync() shouldBe List(user.id -> user.slug)
      userRepo.listPublic(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(userFull)
      userRepo.list(List(user.id)).unsafeRunSync() shouldBe List(user)
    }
    it("should check queries") {
      check(insertLoginRef(credentials.ref(user.id)), s"INSERT INTO ${loginsTable.stripSuffix(" lg")} (${mapFields(loginsFields, _.stripPrefix("lg."))}) VALUES (${mapFields(loginsFields, _ => "?")})")

      check(insertCredentials(credentials), s"INSERT INTO ${credentialsTable.stripSuffix(" cd")} (${mapFields(credentialsFields, _.stripPrefix("cd."))}) VALUES (${mapFields(credentialsFields, _ => "?")})")
      check(updateCredentials(credentials.login)(credentials.pass), s"UPDATE $credentialsTable SET hasher=?, password=?, salt=? WHERE cd.provider_id=? AND cd.provider_key=?")
      check(deleteCredentials(credentials.login), s"DELETE FROM $credentialsTable WHERE cd.provider_id=? AND cd.provider_key=?")
      check(selectCredentials(credentials.login), s"SELECT $credentialsFields FROM $credentialsTable WHERE cd.provider_id=? AND cd.provider_key=? $credentialsOrderBy")

      check(insert(user), s"INSERT INTO ${table.stripSuffix(" u")} (${mapFields(fields, _.stripPrefix("u."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(user.id)(user.data, now), s"UPDATE $table SET slug=?, status=?, first_name=?, last_name=?, email=?, avatar=?, title=?, bio=?, mentoring=?, company=?, location=?, phone=?, website=?, " +
        s"social_facebook=?, social_instagram=?, social_twitter=?, social_linkedIn=?, social_youtube=?, social_meetup=?, social_eventbrite=?, social_slack=?, social_discord=?, social_github=?, " +
        s"updated_at=? WHERE u.id=?")
      check(validateAccount(user.email, now), s"UPDATE $table SET email_validated=? WHERE u.email=?")
      check(selectOne(credentials.login), s"SELECT $fields FROM $tableWithLogin WHERE lg.provider_id=? AND lg.provider_key=? $orderBy")
      check(selectOne(user.email), s"SELECT $fields FROM $table WHERE u.email=? $orderBy")
      check(selectOne(user.slug), s"SELECT $fields FROM $table WHERE u.slug=? $orderBy")
      check(selectOne(user.id), s"SELECT $fields FROM $table WHERE u.id=? $orderBy")
      check(selectOnePublic(user.slug), s"SELECT $fieldsFull FROM $tableFull WHERE u.slug=? AND (u.status=? OR u.id=?) $groupByFull $orderByFull LIMIT 1")
      check(selectAllPublicSlugs(), s"SELECT u.id, u.slug FROM $table WHERE u.status=? $orderBy")
      check(selectPagePublic(params), s"SELECT $fieldsFull FROM $tableFull WHERE u.status=? $groupByFull $orderByFull LIMIT 20 OFFSET 0")
      check(selectPage(NonEmptyList.of(user.id), params)(orgaCtx), s"SELECT $fieldsFull FROM $tableFull WHERE u.id IN (?) $groupByFull $orderByFull LIMIT 20 OFFSET 0")
      check(selectAll(NonEmptyList.of(user.id, user.id)), s"SELECT $fields FROM $table WHERE u.id IN (?, ?) $orderBy")
    }
  }
}

object UserRepoSqlSpec {
  val loginsTable = "logins lg"
  val loginsFields: String = mapFields("provider_id, provider_key, user_id", "lg." + _)
  val loginsOrderBy = "ORDER BY cd.provider_id IS NULL, cd.provider_id, cd.provider_key IS NULL, cd.provider_key"

  val credentialsTable = "credentials cd"
  val credentialsFields: String = mapFields("provider_id, provider_key, hasher, password, salt", "cd." + _)
  val credentialsOrderBy = "ORDER BY cd.provider_id IS NULL, cd.provider_id, cd.provider_key IS NULL, cd.provider_key"

  val table = "users u"
  val fields: String = mapFields(s"id, slug, status, first_name, last_name, email, email_validated, email_validation_before_login, avatar, title, bio, mentoring, company, location, phone, website, $socialFields, created_at, updated_at", "u." + _)
  val orderBy = "ORDER BY u.last_name IS NULL, u.last_name, u.first_name IS NULL, u.first_name"

  val tableWithLogin = s"$table INNER JOIN $loginsTable ON u.id=lg.user_id"

  private val tableFull = s"$table LEFT OUTER JOIN $groupTable ON g.owners LIKE CONCAT('%', u.id, '%') LEFT OUTER JOIN $talkTable ON t.speakers LIKE CONCAT('%', u.id, '%') LEFT OUTER JOIN $proposalTable ON p.speakers LIKE CONCAT('%', u.id, '%') AND p.status=? LEFT OUTER JOIN $externalProposalTable ON ep.speakers LIKE CONCAT('%', u.id, '%') AND ep.status=?"
  private val fieldsFull = s"$fields, COALESCE(COUNT(DISTINCT g.id), 0) as groupCount, COALESCE(COUNT(DISTINCT t.id), 0) as talkCount, COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0) as proposalCount"
  private val groupByFull = s"GROUP BY $fields"
  private val orderByFull = "ORDER BY u.mentoring IS NULL IS NULL, u.mentoring IS NULL, (COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0)) IS NULL, (COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0)) DESC, " +
    "COALESCE(COUNT(DISTINCT t.id), 0) IS NULL, COALESCE(COUNT(DISTINCT t.id), 0) DESC, " +
    "MAX(p.created_at) IS NULL, MAX(p.created_at) DESC, " +
    "u.created_at IS NULL, u.created_at"
}
