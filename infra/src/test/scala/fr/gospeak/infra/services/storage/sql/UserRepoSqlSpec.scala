package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.User._
import fr.gospeak.infra.services.storage.sql.UserRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

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
          check(q, s"INSERT INTO ${loginsTable.stripSuffix(" lg")} (${mapFields(loginsFields, _.stripPrefix("lg."))}) VALUES (${mapFields(loginsFields, _ => "?")})")
        }
      }
      describe("credentials") {
        it("should build insertCredentials") {
          val q = UserRepoSql.insertCredentials(credentials)
          check(q, s"INSERT INTO ${credentialsTable.stripSuffix(" cd")} (${mapFields(credentialsFields, _.stripPrefix("cd."))}) VALUES (${mapFields(credentialsFields, _ => "?")})")
        }
        it("should build updateCredentials") {
          val q = UserRepoSql.updateCredentials(login)(pass)
          check(q, s"UPDATE $credentialsTable SET cd.hasher=?, cd.password=?, cd.salt=? WHERE cd.provider_id=? AND cd.provider_key=?")
        }
        it("should build deleteCredentials") {
          val q = UserRepoSql.deleteCredentials(login)
          check(q, s"DELETE FROM $credentialsTable WHERE cd.provider_id=? AND cd.provider_key=?")
        }
        it("should build selectCredentials") {
          val q = UserRepoSql.selectCredentials(login)
          check(q, s"SELECT $credentialsFields FROM $credentialsTable WHERE cd.provider_id=? AND cd.provider_key=?")
        }
      }
      it("should build insert") {
        val q = UserRepoSql.insert(user)
        check(q, s"INSERT INTO ${table.stripSuffix(" u")} (${mapFields(fields, _.stripPrefix("u."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = UserRepoSql.update(user)
        check(q, s"UPDATE $table SET u.slug=?, u.first_name=?, u.last_name=?, u.email=?, u.status=?, u.bio=?, u.company=?, u.location=?, u.twitter=?, u.linkedin=?, u.phone=?, u.website=?, u.updated=? WHERE u.id=?")
      }
      it("should build validateAccount") {
        val q = UserRepoSql.validateAccount(user.email, now)
        check(q, s"UPDATE $table SET u.email_validated=? WHERE u.email=?")
      }
      it("should build selectOne with login") {
        val q = UserRepoSql.selectOne(login)
        check(q, s"SELECT $fields FROM $tableWithLogin WHERE lg.provider_id=? AND lg.provider_key=? $orderBy")
      }
      it("should build selectOne with email") {
        val q = UserRepoSql.selectOne(user.email)
        check(q, s"SELECT $fields FROM $table WHERE u.email=? $orderBy")
      }
      it("should build selectOne with slug") {
        val q = UserRepoSql.selectOne(user.slug)
        check(q, s"SELECT $fields FROM $table WHERE u.slug=? $orderBy")
      }
      it("should build selectOne with id") {
        val q = UserRepoSql.selectOne(user.id)
        check(q, s"SELECT $fields FROM $table WHERE u.id=? $orderBy")
      }
      it("should build selectOnePublic with slug") {
        val q = UserRepoSql.selectOnePublic(user.slug)
        check(q, s"SELECT $fields FROM $table WHERE u.status=? AND u.slug=? $orderBy")
      }
      it("should build selectPagePublic") {
        val q = UserRepoSql.selectPagePublic(params)
        check(q, s"SELECT $fields FROM $table WHERE u.status=? $orderBy OFFSET 0 LIMIT 20")
      }
      it("should build selectPage") {
        val q = UserRepoSql.selectPage(NonEmptyList.of(user.id), params)
        check(q, s"SELECT $fields FROM $table WHERE u.id IN (?)  $orderBy OFFSET 0 LIMIT 20")
      }
      it("should build selectAll with ids") {
        val q = UserRepoSql.selectAll(NonEmptyList.of(user.id, user.id))
        check(q, s"SELECT $fields FROM $table WHERE u.id IN (?, ?)  $orderBy")
      }
    }
  }
}

object UserRepoSqlSpec {
  val loginsTable = "logins lg"
  val loginsFields: String = mapFields("provider_id, provider_key, user_id", "lg." + _)

  val credentialsTable = "credentials cd"
  val credentialsFields: String = mapFields("provider_id, provider_key, hasher, password, salt", "cd." + _)

  val table = "users u"
  val fields: String = mapFields("id, slug, first_name, last_name, email, email_validated, avatar, avatar_source, status, bio, company, location, twitter, linkedin, phone, website, created, updated", "u." + _)
  val orderBy = "ORDER BY u.first_name IS NULL, u.first_name"

  val tableWithLogin = s"$table INNER JOIN $loginsTable ON u.id=lg.user_id"
}
