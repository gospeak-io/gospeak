package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import fr.gospeak.core.domain.User._
import fr.gospeak.infra.services.storage.sql.tables.UserTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class UserTableSpec extends TableSpec {
  private val login = Login(ProviderId("providerId"), ProviderKey("providerKey"))
  private val pass = Password(Hasher("hasher"), PasswordValue("password"), Some(Salt("salt")))
  private val loginRef = LoginRef(login, user.id)
  private val credentials = Credentials(login, pass)

  describe("UserTable") {
    describe("logins") {
      it("should build insertLoginRef query") {
        val q = insertLoginRef(loginRef)
        q.sql shouldBe "INSERT INTO logins (provider_id, provider_key, user_id) VALUES (?, ?, ?)"
        check(q)
      }
    }
    describe("credentials") {
      it("should build insertCredentials query") {
        val q = insertCredentials(credentials)
        q.sql shouldBe "INSERT INTO credentials (provider_id, provider_key, hasher, password, salt) VALUES (?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build updateCredentials query") {
        val q = updateCredentials(login)(pass)
        q.sql shouldBe "UPDATE credentials SET hasher=?, password=?, salt=? WHERE provider_id=? AND provider_key=?"
        check(q)
      }
      it("should build deleteCredentials query") {
        val q = deleteCredentials(login)
        q.sql shouldBe "DELETE FROM credentials WHERE provider_id=? AND provider_key=?"
        check(q)
      }
      it("should build selectCredentials query") {
        val q = selectCredentials(login)
        q.sql shouldBe "SELECT provider_id, provider_key, hasher, password, salt FROM credentials WHERE provider_id=? AND provider_key=?"
        check(q)
      }
    }
    it("should build insert query") {
      val q = insert(user)
      q.sql shouldBe "INSERT INTO users (id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, created, updated) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      check(q)
    }
    it("should build update query") {
      val q = update(user)
      q.sql shouldBe "UPDATE users SET slug=?, first_name=?, last_name=?, email=?, updated=? WHERE id=?"
      check(q)
    }
    it("should build validateAccount query") {
      val q = validateAccount(user.id, now)
      q.sql shouldBe "UPDATE users SET email_validated=? WHERE id=?"
      check(q)
    }
    it("should build selectOne with login query") {
      val q = selectOne(login)
      q.sql shouldBe "SELECT u.id, u.slug, u.first_name, u.last_name, u.email, u.email_validated, u.avatar, u.avatar_source, u.created, u.updated FROM users u INNER JOIN logins l ON u.id=l.user_id WHERE l.provider_id=? AND l.provider_key=?"
      check(q)
    }
    it("should build selectOne with email query") {
      val q = selectOne(user.email)
      q.sql shouldBe "SELECT id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, created, updated FROM users WHERE email=?"
      check(q)
    }
    it("should build selectOne with slug query") {
      val q = selectOne(user.slug)
      q.sql shouldBe "SELECT id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, created, updated FROM users WHERE slug=?"
      check(q)
    }
    it("should build selectAll query") {
      val q = selectAll(NonEmptyList.of(user.id, user.id))
      q.sql shouldBe "SELECT id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, created, updated FROM users WHERE id IN (?, ?) "
      check(q)
    }
  }
}
