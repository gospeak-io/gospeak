package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.testingutils.RepoSpec

class UserRepoSqlSpec extends RepoSpec {
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
  }
}
