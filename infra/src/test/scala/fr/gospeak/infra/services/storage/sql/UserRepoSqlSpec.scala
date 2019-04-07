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
      userRepo.find(email).unsafeRunSync() shouldBe None
      userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      userRepo.find(email).unsafeRunSync().map(_.email) shouldBe Some(email)
    }
    it("should fail on duplicate slug") {
      userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      an[Exception] should be thrownBy userRepo.create(userSlug, firstName, lastName, email2, avatar, now).unsafeRunSync()
    }
    it("should fail on duplicate email") {
      userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      an[Exception] should be thrownBy userRepo.create(userSlug2, firstName, lastName, email, avatar, now).unsafeRunSync()
    }
    it("should select users by ids") {
      val user1 = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      val user2 = userRepo.create(userSlug2, firstName, lastName, email2, avatar, now).unsafeRunSync()
      userRepo.create(userSlug3, firstName, lastName, email3, avatar, now).unsafeRunSync()
      userRepo.list(Seq(user1.id, user2.id)).unsafeRunSync() should contain theSameElementsAs Seq(user1, user2)
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
