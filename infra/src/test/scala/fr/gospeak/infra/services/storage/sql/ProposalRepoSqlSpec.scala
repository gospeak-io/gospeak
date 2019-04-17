package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.{Cfp, Proposal, Talk}
import fr.gospeak.infra.services.storage.sql.ProposalRepoSql._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class ProposalRepoSqlSpec extends RepoSpec {
  describe("ProposalRepoSql") {
    it("should create and retrieve a proposal for a group and talk") {
      val (user, _, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
      proposalRepo.list(talk.id, page).unsafeRunSync().items shouldBe Seq()
      proposalRepo.list(cfp.id, page).unsafeRunSync().items shouldBe Seq()
      val proposal = proposalRepo.create(talk.id, cfp.id, proposalData1, speakers, user.id, now).unsafeRunSync()
      proposalRepo.list(talk.id, page).unsafeRunSync().items shouldBe Seq(cfp -> proposal)
      proposalRepo.list(cfp.id, page).unsafeRunSync().items shouldBe Seq(proposal)
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(proposal)
    }
    it("should fail to create a proposal when talk does not exists") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      val group = groupRepo.create(groupData1, user.id, now).unsafeRunSync()
      val cfp = cfpRepo.create(group.id, cfpData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(Talk.Id.generate(), cfp.id, proposalData1, speakers, user.id, now).unsafeRunSync()
    }
    it("should fail to create a proposal when cfp does not exists") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      val talk = talkRepo.create(user.id, talkData1, now).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, Cfp.Id.generate(), proposalData1, speakers, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate cfp and talk") {
      val (user, _, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
      proposalRepo.create(talk.id, cfp.id, proposalData1, speakers, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, cfp.id, proposalData1, speakers, user.id, now).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = insert(proposal)
        q.sql shouldBe "INSERT INTO proposals (id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(user.id, talk.slug, cfp.slug)(proposal.data, now)
        q.sql shouldBe "UPDATE proposals SET title=?, duration=?, description=?, slides=?, video=?, updated=?, updated_by=? WHERE id=(SELECT p.id FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN talks t ON p.talk_id=t.id WHERE c.slug=? AND t.slug=? AND t.speakers LIKE ?)"
        check(q)
      }
      it("should build updateStatus") {
        val q = updateStatus(proposal.id)(proposal.status, None)
        q.sql shouldBe "UPDATE proposals SET status=?, event_id=? WHERE id=?"
        check(q)
      }
      it("should build updateSlides by id") {
        val q = updateSlides(proposal.id)(slides, now, user.id)
        q.sql shouldBe "UPDATE proposals SET slides=?, updated=?, updated_by=? WHERE id=?"
        check(q)
      }
      it("should build updateSlides by speaker, talk and cfp") {
        val q = updateSlides(user.id, talk.slug, cfp.slug)(slides, now, user.id)
        q.sql shouldBe "UPDATE proposals SET slides=?, updated=?, updated_by=? WHERE id=(SELECT p.id FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN talks t ON p.talk_id=t.id WHERE c.slug=? AND t.slug=? AND t.speakers LIKE ?)"
        check(q)
      }
      it("should build updateVideo by id") {
        val q = updateVideo(proposal.id)(video, now, user.id)
        q.sql shouldBe "UPDATE proposals SET video=?, updated=?, updated_by=? WHERE id=?"
        check(q)
      }
      it("should build updateVideo by speaker, talk and cfp") {
        val q = updateVideo(user.id, talk.slug, cfp.slug)(video, now, user.id)
        q.sql shouldBe "UPDATE proposals SET video=?, updated=?, updated_by=? WHERE id=(SELECT p.id FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN talks t ON p.talk_id=t.id WHERE c.slug=? AND t.slug=? AND t.speakers LIKE ?)"
        check(q)
      }
      it("should build selectOne for proposal id") {
        val q = selectOne(proposal.id)
        q.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE id=?"
        check(q)
      }
      it("should build selectOne for speaker, talk and cfp") {
        val q = selectOne(user.id, talk.slug, cfp.slug)
        q.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE id=(SELECT p.id FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN talks t ON p.talk_id=t.id WHERE c.slug=? AND t.slug=? AND t.speakers LIKE ?)"
        check(q)
      }
      it("should build selectPage for a talk") {
        val (s, c) = selectPage(talk.id, params)
        s.sql shouldBe
          "SELECT c.id, c.group_id, c.slug, c.name, c.start, c.end, c.description, c.created, c.created_by, c.updated, c.updated_by, " +
            "p.id, p.talk_id, p.cfp_id, p.event_id, p.title, p.duration, p.status, p.description, p.speakers, p.slides, p.video, p.created, p.created_by, p.updated, p.updated_by " +
            "FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id WHERE p.talk_id=? ORDER BY p.created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id WHERE p.talk_id=? "
        check(s)
        check(c)
      }
      it("should build selectPage for a group") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe
          "SELECT p.id, p.talk_id, p.cfp_id, p.event_id, p.title, p.duration, p.status, p.description, p.speakers, p.slides, p.video, p.created, p.created_by, p.updated, p.updated_by " +
            "FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id WHERE c.group_id=? ORDER BY p.created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id WHERE c.group_id=? "
        check(s)
        check(c)
      }
      it("should build selectPage for a group and speaker") {
        val (s, c) = selectPage(group.id, user.id, params)
        s.sql shouldBe
          "SELECT c.id, c.group_id, c.slug, c.name, c.start, c.end, c.description, c.created, c.created_by, c.updated, c.updated_by, " +
            "p.id, p.talk_id, p.cfp_id, p.event_id, p.title, p.duration, p.status, p.description, p.speakers, p.slides, p.video, p.created, p.created_by, p.updated, p.updated_by " +
            "FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id WHERE c.group_id=? AND p.speakers LIKE ? ORDER BY p.created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM proposals p INNER JOIN cfps c ON p.cfp_id=c.id WHERE c.group_id=? AND p.speakers LIKE ? "
        check(s)
        check(c)
      }
      it("should build selectPage for a cfp") {
        val (s, c) = selectPage(cfp.id, params)
        s.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE cfp_id=? ORDER BY created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM proposals WHERE cfp_id=? "
        check(s)
        check(c)
      }
      it("should build selectPage for a cfp and status") {
        val (s, c) = selectPage(cfp.id, Proposal.Status.Pending, params)
        s.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE cfp_id=? AND status=? ORDER BY created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM proposals WHERE cfp_id=? AND status=? "
        check(s)
        check(c)
      }
      it("should build selectAll") {
        val q = selectAll(NonEmptyList.of(proposal.id))
        q.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE id IN (?) "
        check(q)
      }
    }
  }
}
