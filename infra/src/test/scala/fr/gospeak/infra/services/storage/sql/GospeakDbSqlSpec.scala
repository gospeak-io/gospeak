package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.utils.{Email, Page}
import fr.gospeak.core.domain._
import fr.gospeak.infra.testingutils.Values
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}

class GospeakDbSqlSpec extends FunSpec with Matchers with BeforeAndAfterEach {
  private val db = Values.db
  private val firstName = "John"
  private val lastName = "Doe"
  private val email = Email("john@doe.com")
  private val page = Page.Params()

  override def beforeEach(): Unit = db.createTables().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  describe("GospeakDbSql") {
    describe("User") {
      it("should create and retrieve a user") {
        db.getUser(email).unsafeRunSync() shouldBe None
        db.createUser(firstName, lastName, email).unsafeRunSync()
        db.getUser(email).unsafeRunSync().map(_.email) shouldBe Some(email)
      }
      it("should fail on duplicate email") {
        db.createUser(firstName, lastName, email).unsafeRunSync()
        an[Exception] should be thrownBy db.createUser(firstName, lastName, email).unsafeRunSync()
      }
    }
    describe("Group") {
      val slug = Group.Slug("group")
      it("should create and retrieve a group") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getGroupId(slug).unsafeRunSync() shouldBe None
        val group = db.createGroup(slug, Group.Name("name"), "desc", user.id).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq(group)
        db.getGroup(group.id, user.id).unsafeRunSync() shouldBe Some(group)
        db.getGroupId(slug).unsafeRunSync() shouldBe Some(group.id)
      }
      it("should not retrieve not owned groups") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val user2 = db.createUser("A", "A", Email("aaa@aaa.com")).unsafeRunSync()
        val group = db.createGroup(slug, Group.Name("name"), "desc", user2.id).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getGroup(group.id, user.id).unsafeRunSync() shouldBe None
        db.getGroupId(slug).unsafeRunSync() shouldBe Some(group.id)
      }
      it("should fail on duplicate slug") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.createGroup(slug, Group.Name("name"), "desc", user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createGroup(slug, Group.Name("name"), "desc", user.id).unsafeRunSync()
      }
    }
    describe("Event") {
      val slug = Event.Slug("slug")
      it("should create and retrieve an event for a group") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val group = db.createGroup(Group.Slug("slug"), Group.Name("name"), "desc", user.id).unsafeRunSync()
        db.getEvents(group.id, page).unsafeRunSync().items shouldBe Seq()
        db.getEventId(group.id, slug).unsafeRunSync() shouldBe None
        val event = db.createEvent(group.id, slug, Event.Name("name"), user.id).unsafeRunSync()
        db.getEvents(group.id, page).unsafeRunSync().items shouldBe Seq(event)
        db.getEvent(event.id).unsafeRunSync() shouldBe Some(event)
        db.getEventId(group.id, slug).unsafeRunSync() shouldBe Some(event.id)
      }
      it("should fail to create an event when the group does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        an[Exception] should be thrownBy db.createEvent(Group.Id.generate(), slug, Event.Name("name"), user.id).unsafeRunSync()
      }
      it("should fail on duplicate slug for the same group") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val group = db.createGroup(Group.Slug("slug"), Group.Name("name"), "desc", user.id).unsafeRunSync()
        db.createEvent(group.id, slug, Event.Name("name"), user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createEvent(group.id, slug, Event.Name("name"), user.id).unsafeRunSync()
      }
    }
    describe("Talk") {
      val slug = Talk.Slug("slug")
      it("should create and retrieve") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getTalkId(user.id, slug).unsafeRunSync() shouldBe None
        val talk = db.createTalk(slug, Talk.Title("title"), "desc", user.id).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq(talk)
        db.getTalk(talk.id, user.id).unsafeRunSync() shouldBe Some(talk)
        db.getTalkId(user.id, slug).unsafeRunSync() shouldBe Some(talk.id)
      }
      it("should not retrieve not owned talks") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val user2 = db.createUser("A", "A", Email("aaa@aaa.com")).unsafeRunSync()
        val talk = db.createTalk(slug, Talk.Title("title"), "desc", user2.id).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getTalk(talk.id, user.id).unsafeRunSync() shouldBe None
        db.getTalkId(user.id, slug).unsafeRunSync() shouldBe None
      }
      it("should fail on duplicate slug on same user") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val user2 = db.createUser("A", "A", Email("aaa@aaa.com")).unsafeRunSync()
        db.createTalk(slug, Talk.Title("title"), "desc", user.id).unsafeRunSync()
        db.createTalk(slug, Talk.Title("title"), "desc", user2.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createTalk(slug, Talk.Title("title"), "desc", user.id).unsafeRunSync()
      }
    }
    describe("Proposal") {
      it("should create and retrieve a proposal for a group and talk") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val talk = db.createTalk(Talk.Slug("slug"), Talk.Title("title"), "desc", user.id).unsafeRunSync()
        val group = db.createGroup(Group.Slug("slug"), Group.Name("name"), "desc", user.id).unsafeRunSync()
        db.getProposals(talk.id, page).unsafeRunSync().items shouldBe Seq()
        db.getProposals(group.id, page).unsafeRunSync().items shouldBe Seq()
        val proposal = db.createProposal(talk.id, group.id, Proposal.Title("title"), "desc", user.id).unsafeRunSync()
        db.getProposals(talk.id, page).unsafeRunSync().items shouldBe Seq(group -> proposal)
        db.getProposals(group.id, page).unsafeRunSync().items shouldBe Seq(proposal)
        db.getProposal(proposal.id).unsafeRunSync() shouldBe Some(proposal)
      }
      it("should fail to create a proposal when talk does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val group = db.createGroup(Group.Slug("slug"), Group.Name("name"), "desc", user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(Talk.Id.generate(), group.id, Proposal.Title("title"), "desc", user.id).unsafeRunSync()
      }
      it("should fail to create a proposal when group does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val talk = db.createTalk(Talk.Slug("slug"), Talk.Title("title"), "desc", user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(talk.id, Group.Id.generate(), Proposal.Title("title"), "desc", user.id).unsafeRunSync()
      }
      it("should fail on duplicate group and talk") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val talk = db.createTalk(Talk.Slug("slug"), Talk.Title("title"), "desc", user.id).unsafeRunSync()
        val group = db.createGroup(Group.Slug("slug"), Group.Name("name"), "desc", user.id).unsafeRunSync()
        db.createProposal(talk.id, group.id, Proposal.Title("title"), "desc", user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(talk.id, group.id, Proposal.Title("title"), "desc", user.id).unsafeRunSync()
      }
    }
  }
}
