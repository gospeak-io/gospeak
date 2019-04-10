package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.User._
import fr.gospeak.infra.services.storage.sql.UserRepoSql._
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
      userRepo.speakers(group.id, page).unsafeRunSync().items shouldBe Seq()

      val user1 = userRepo.create(userData1, now).unsafeRunSync()
      val user2 = userRepo.create(userData2, now).unsafeRunSync()
      val talk1 = talkRepo.create(user1.id, talkData1, now).unsafeRunSync()
      val group1 = groupRepo.create(groupData1, user1.id, now).unsafeRunSync()
      val cfp1 = cfpRepo.create(group1.id, cfpData1, user1.id, now).unsafeRunSync()
      val prop1 = proposalRepo.create(talk1.id, cfp1.id, proposalData1, NonEmptyList.of(user1.id, user2.id), user1.id, now).unsafeRunSync()

      userRepo.speakers(group1.id, page).unsafeRunSync().items.map(_.id) should contain theSameElementsAs prop1.speakers.toList
    }
    describe("Queries") {
      describe("logins") {
        it("should build insertLoginRef") {
          val q = insertLoginRef(loginRef)
          q.sql shouldBe "INSERT INTO logins (provider_id, provider_key, user_id) VALUES (?, ?, ?)"
          check(q)
        }
      }
      describe("credentials") {
        it("should build insertCredentials") {
          val q = insertCredentials(credentials)
          q.sql shouldBe "INSERT INTO credentials (provider_id, provider_key, hasher, password, salt) VALUES (?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build updateCredentials") {
          val q = updateCredentials(login)(pass)
          q.sql shouldBe "UPDATE credentials SET hasher=?, password=?, salt=? WHERE provider_id=? AND provider_key=?"
          check(q)
        }
        it("should build deleteCredentials") {
          val q = deleteCredentials(login)
          q.sql shouldBe "DELETE FROM credentials WHERE provider_id=? AND provider_key=?"
          check(q)
        }
        it("should build selectCredentials") {
          val q = selectCredentials(login)
          q.sql shouldBe "SELECT provider_id, provider_key, hasher, password, salt FROM credentials WHERE provider_id=? AND provider_key=?"
          check(q)
        }
      }
      it("should build insert") {
        val q = insert(user)
        q.sql shouldBe "INSERT INTO users (id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, created, updated) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(user)
        q.sql shouldBe "UPDATE users SET slug=?, first_name=?, last_name=?, email=?, updated=? WHERE id=?"
        check(q)
      }
      it("should build validateAccount") {
        val q = validateAccount(user.id, now)
        q.sql shouldBe "UPDATE users SET email_validated=? WHERE id=?"
        check(q)
      }
      it("should build selectOne with login") {
        val q = selectOne(login)
        q.sql shouldBe "SELECT u.id, u.slug, u.first_name, u.last_name, u.email, u.email_validated, u.avatar, u.avatar_source, u.created, u.updated FROM users u INNER JOIN logins l ON u.id=l.user_id WHERE l.provider_id=? AND l.provider_key=?"
        check(q)
      }
      it("should build selectOne with email") {
        val q = selectOne(user.email)
        q.sql shouldBe "SELECT id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, created, updated FROM users WHERE email=?"
        check(q)
      }
      it("should build selectOne with slug") {
        val q = selectOne(user.slug)
        q.sql shouldBe "SELECT id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, created, updated FROM users WHERE slug=?"
        check(q)
      }
      it("should build selectAll with ids") {
        val q = selectAll(NonEmptyList.of(user.id, user.id))
        q.sql shouldBe "SELECT id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, created, updated FROM users WHERE id IN (?, ?) "
        check(q)
      }
    }
  }
}
