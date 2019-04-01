package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.{Group, Talk}
import fr.gospeak.infra.testingutils.RepoSpec

class TalkRepoSqlSpec extends RepoSpec {
  describe("TalkRepoSql") {
    it("should create and retrieve") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      talkRepo.list(user.id, page).unsafeRunSync().items shouldBe Seq()
      talkRepo.find(user.id, talkData.slug).unsafeRunSync() shouldBe None
      val talk = talkRepo.create(user.id, talkData, now).unsafeRunSync()
      talkRepo.list(user.id, page).unsafeRunSync().items shouldBe Seq(talk)
      talkRepo.find(user.id, talkData.slug).unsafeRunSync() shouldBe Some(talk)
    }
    it("should not retrieve not owned talks") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      val user2 = userRepo.create(userSlug2, firstName, lastName, email2, avatar, now).unsafeRunSync()
      val talk = talkRepo.create(user2.id, talkData, now).unsafeRunSync()
      talkRepo.list(user.id, page).unsafeRunSync().items shouldBe Seq()
      talkRepo.find(user.id, talkData.slug).unsafeRunSync() shouldBe None
    }
    it("should fail on duplicate slug on same user") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      val user2 = userRepo.create(userSlug2, firstName, lastName, email2, avatar, now).unsafeRunSync()
      talkRepo.create(user.id, talkData, now).unsafeRunSync()
      talkRepo.create(user2.id, talkData, now).unsafeRunSync()
      an[Exception] should be thrownBy talkRepo.create(user.id, talkData, now).unsafeRunSync()
    }
    it("should update talk data") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      talkRepo.create(user.id, talkData, now).unsafeRunSync()
      talkRepo.find(user.id, talkData.slug).unsafeRunSync().map(_.data) shouldBe Some(talkData)
      talkRepo.update(user.id, talkData.slug)(talkData2, now).unsafeRunSync()
      talkRepo.find(user.id, talkData.slug).unsafeRunSync() shouldBe None
      talkRepo.find(user.id, talkData2.slug).unsafeRunSync().map(_.data) shouldBe Some(talkData2)
    }
    it("should fail to change slug for an existing one") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      talkRepo.create(user.id, talkData, now).unsafeRunSync()
      talkRepo.create(user.id, talkData2, now).unsafeRunSync()
      an[Exception] should be thrownBy talkRepo.update(user.id, talkData.slug)(talkData.copy(slug = talkData2.slug), now).unsafeRunSync()
    }
    it("should update the status") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      talkRepo.create(user.id, talkData, now).unsafeRunSync()
      talkRepo.find(user.id, talkData.slug).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Draft)
      talkRepo.updateStatus(user.id, talkData.slug)(Talk.Status.Public).unsafeRunSync()
      talkRepo.find(user.id, talkData.slug).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Public)
    }
  }
}
