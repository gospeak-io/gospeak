package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.Group
import fr.gospeak.infra.testingutils.RepoSpec

class EventRepoSqlSpec extends RepoSpec {
  describe("EventRepoSql") {
    it("should create and retrieve an event for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      eventRepo.list(group.id, page).unsafeRunSync().items shouldBe Seq()
      eventRepo.find(group.id, eventData.slug).unsafeRunSync() shouldBe None
      val event = eventRepo.create(group.id, eventData, user.id, now).unsafeRunSync()
      eventRepo.list(group.id, page).unsafeRunSync().items shouldBe Seq(event)
      eventRepo.find(group.id, eventData.slug).unsafeRunSync() shouldBe Some(event)
    }
    it("should fail to create an event when the group does not exists") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      an[Exception] should be thrownBy eventRepo.create(Group.Id.generate(), eventData, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate slug for the same group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      eventRepo.create(group.id, eventData, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy eventRepo.create(group.id, eventData, user.id, now).unsafeRunSync()
    }
  }
}
