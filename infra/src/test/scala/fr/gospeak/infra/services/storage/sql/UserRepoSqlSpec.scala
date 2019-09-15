package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.User._
import fr.gospeak.infra.services.storage.sql.UserRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class UserRepoSqlSpec extends RepoSpec {
  private val login = Login(ProviderId("providerId"), ProviderKey("providerKey"))
  private val pass = Password(Hasher("hasher"), PasswordValue("password"), Some(Salt("salt")))
  private val loginRef = LoginRef(login, user.id)
  private val credentials = Credentials(login, pass)

  describe("UserRepoSql") {
    it("should create and retrieve a user") {
      userRepo.find(userData1.email).unsafeRunSync() shouldBe None
      userRepo.create(userData1, now).unsafeRunSync()
      userRepo.find(userData1.email).unsafeRunSync().map(_.email) shouldBe Some(userData1.email)
    }
    it("should fail on duplicate slug") {
      userRepo.create(userData1, now).unsafeRunSync()
      an[Exception] should be thrownBy userRepo.create(userData2.copy(slug = userData1.slug), now).unsafeRunSync()
    }
    it("should fail on duplicate email") {
      userRepo.create(userData1, now).unsafeRunSync()
      an[Exception] should be thrownBy userRepo.create(userData2.copy(email = userData1.email), now).unsafeRunSync()
    }
    it("should select users by ids") {
      val user1 = userRepo.create(userData1, now).unsafeRunSync()
      val user2 = userRepo.create(userData2, now).unsafeRunSync()
      userRepo.create(userData3, now).unsafeRunSync()
      userRepo.list(Seq(user1.id, user2.id)).unsafeRunSync() should contain theSameElementsAs Seq(user1, user2)
    }
    it("should select all speakers for a group") {
      userRepo.speakers(group.id, params).unsafeRunSync().items shouldBe Seq()

      val user1 = userRepo.create(userData1, now).unsafeRunSync()
      val user2 = userRepo.create(userData2, now).unsafeRunSync()
      val talk1 = talkRepo.create(talkData1, user1.id, now).unsafeRunSync()
      val group1 = groupRepo.create(groupData1, user1.id, now).unsafeRunSync()
      val cfp1 = cfpRepo.create(group1.id, cfpData1, user1.id, now).unsafeRunSync()
      val prop1 = proposalRepo.create(talk1.id, cfp1.id, proposalData1, NonEmptyList.of(user1.id, user2.id), user1.id, now).unsafeRunSync()

      userRepo.speakers(group1.id, params).unsafeRunSync().items.map(_.id) should contain theSameElementsAs prop1.speakers.toList
    }
    describe("Queries") {
      describe("logins") {
        it("should build insertLoginRef") {
          val q = UserRepoSql.insertLoginRef(loginRef)
          q.sql shouldBe s"INSERT INTO $loginsTable ($loginsFields) VALUES (${mapFields(loginsFields, _ => "?")})"
          check(q)
        }
      }
      describe("credentials") {
        it("should build insertCredentials") {
          val q = UserRepoSql.insertCredentials(credentials)
          q.sql shouldBe s"INSERT INTO $credentialsTable ($credentialsFields) VALUES (${mapFields(credentialsFields, _ => "?")})"
          check(q)
        }
        it("should build updateCredentials") {
          val q = UserRepoSql.updateCredentials(login)(pass)
          q.sql shouldBe s"UPDATE $credentialsTable SET hasher=?, password=?, salt=? WHERE provider_id=? AND provider_key=?"
          check(q)
        }
        it("should build deleteCredentials") {
          val q = UserRepoSql.deleteCredentials(login)
          q.sql shouldBe s"DELETE FROM $credentialsTable WHERE provider_id=? AND provider_key=?"
          check(q)
        }
        it("should build selectCredentials") {
          val q = UserRepoSql.selectCredentials(login)
          q.sql shouldBe s"SELECT $credentialsFields FROM $credentialsTable WHERE provider_id=? AND provider_key=?"
          check(q)
        }
      }
      it("should build insert") {
        val q = UserRepoSql.insert(user)
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = UserRepoSql.update(user)
        q.sql shouldBe s"UPDATE $table SET slug=?, first_name=?, last_name=?, email=?, status=?, description=?, company=?, location=?, twitter=?, linkedin=?, phone=?, website=?, updated=? WHERE id=?"
        check(q)
      }
      it("should build validateAccount") {
        val q = UserRepoSql.validateAccount(user.email, now)
        q.sql shouldBe s"UPDATE $table SET email_validated=? WHERE email=?"
        check(q)
      }
      it("should build selectOne with login") {
        val q = UserRepoSql.selectOne(login)
        q.sql shouldBe s"SELECT ${mapFields(fields, "u." + _)} FROM $table u INNER JOIN logins l ON u.id=l.user_id WHERE l.provider_id=? AND l.provider_key=?"
        check(q)
      }
      it("should build selectOne with email") {
        val q = UserRepoSql.selectOne(user.email)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE email=?"
        check(q)
      }
      it("should build selectOne with slug") {
        val q = UserRepoSql.selectOne(user.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE slug=?"
        check(q)
      }
      it("should build selectOne with id") {
        val q = UserRepoSql.selectOne(user.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id=?"
        check(q)
      }
      it("should build selectOnePublic with slug") {
        val q = UserRepoSql.selectOnePublic(user.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE status=? AND slug=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = UserRepoSql.selectPage(NonEmptyList.of(user.id), params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE id IN (?)  ORDER BY first_name IS NULL, first_name OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE id IN (?)  "
        check(s)
        check(c)
      }
      it("should build selectPagePublic") {
        val (s, c) = UserRepoSql.selectPagePublic(params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE status=?  ORDER BY first_name IS NULL, first_name OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE status=?  "
        check(s)
        check(c)
      }
      it("should build selectAll with ids") {
        val q = UserRepoSql.selectAll(NonEmptyList.of(user.id, user.id))
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id IN (?, ?) "
        check(q)
      }
    }
  }
}

object UserRepoSqlSpec {
  val loginsTable = "logins"
  val loginsFields = "provider_id, provider_key, user_id"

  val credentialsTable = "credentials"
  val credentialsFields = "provider_id, provider_key, hasher, password, salt"

  val table = "users"
  val fields = "id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, status, description, company, location, twitter, linkedin, phone, website, created, updated"
}
