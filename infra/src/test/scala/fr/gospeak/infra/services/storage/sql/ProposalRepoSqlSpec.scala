package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.{Cfp, Talk}
import fr.gospeak.infra.testingutils.RepoSpec

class ProposalRepoSqlSpec extends RepoSpec {
  describe("ProposalRepoSql") {
    it("should create and retrieve a proposal for a group and talk") {
      val (user, _, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
      proposalRepo.list(talk.id, page).unsafeRunSync().items shouldBe Seq()
      proposalRepo.list(cfp.id, page).unsafeRunSync().items shouldBe Seq()
      val proposal = proposalRepo.create(talk.id, cfp.id, proposalData, speakers, user.id, now).unsafeRunSync()
      proposalRepo.list(talk.id, page).unsafeRunSync().items shouldBe Seq(cfp -> proposal)
      proposalRepo.list(cfp.id, page).unsafeRunSync().items shouldBe Seq(proposal)
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(proposal)
    }
    it("should fail to create a proposal when talk does not exists") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      val group = groupRepo.create(groupData, user.id, now).unsafeRunSync()
      val cfp = cfpRepo.create(group.id, cfpData, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(Talk.Id.generate(), cfp.id, proposalData, speakers, user.id, now).unsafeRunSync()
    }
    it("should fail to create a proposal when cfp does not exists") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      val talk = talkRepo.create(user.id, talkData, now).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, Cfp.Id.generate(), proposalData, speakers, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate cfp and talk") {
      val (user, _, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
      proposalRepo.create(talk.id, cfp.id, proposalData, speakers, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, cfp.id, proposalData, speakers, user.id, now).unsafeRunSync()
    }
  }
}
