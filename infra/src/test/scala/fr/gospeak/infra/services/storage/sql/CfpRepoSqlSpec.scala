package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.{Group, Talk}
import fr.gospeak.infra.testingutils.RepoSpec

class CfpRepoSqlSpec extends RepoSpec {
  describe("CfpRepoSql") {
    it("should create and retrieve a cfp for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val talkId = Talk.Id.generate()
      cfpRepo.listAvailables(talkId, page).unsafeRunSync().items shouldBe Seq()
      cfpRepo.find(cfpData.slug).unsafeRunSync() shouldBe None
      cfpRepo.find(group.id).unsafeRunSync() shouldBe None
      val cfp = cfpRepo.create(group.id, cfpData, user.id, now).unsafeRunSync()
      cfpRepo.listAvailables(talkId, page).unsafeRunSync().items shouldBe Seq(cfp)
      cfpRepo.find(cfpData.slug).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.find(cfp.id).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.find(group.id).unsafeRunSync() shouldBe Some(cfp)
    }
    it("should fail to create a cfp when the group does not exists") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(Group.Id.generate(), cfpData, user.id, now).unsafeRunSync()
    }
    it("should fail to create two cfp for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      cfpRepo.create(group.id, cfpData, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(group.id, cfpData, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate slug") {
      val (user, group1) = createUserAndGroup().unsafeRunSync()
      val group2 = groupRepo.create(groupData2, user.id, now).unsafeRunSync()
      cfpRepo.create(group1.id, cfpData, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(group2.id, cfpData, user.id, now).unsafeRunSync()
    }
  }
}
