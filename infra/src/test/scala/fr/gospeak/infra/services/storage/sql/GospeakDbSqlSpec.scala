package fr.gospeak.infra.services.storage.sql

import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.infra.testingutils.Values
import fr.gospeak.libs.scalautils.domain.{Done, Email, Markdown, Page}
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}

import scala.concurrent.duration._

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
      val slug = Group.Slug.from("group").get
      it("should create and retrieve a group") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getGroup(user.id, slug).unsafeRunSync() shouldBe None
        val group = db.createGroup(slug, Group.Name("name"), "desc", user.id).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq(group)
        db.getGroup(user.id, slug).unsafeRunSync() shouldBe Some(group)
      }
      it("should not retrieve not owned groups") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val user2 = db.createUser("A", "A", Email("aaa@aaa.com")).unsafeRunSync()
        db.createGroup(slug, Group.Name("name"), "desc", user2.id).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getGroup(user.id, slug).unsafeRunSync() shouldBe None
      }
      it("should fail on duplicate slug") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.createGroup(slug, Group.Name("name"), "desc", user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createGroup(slug, Group.Name("name"), "desc", user.id).unsafeRunSync()
      }
    }
    describe("Event") {
      val slug = Event.Slug.from("slug").get
      it("should create and retrieve an event for a group") {
        val (user, group) = createEltsForEvents().unsafeRunSync()
        db.getEvents(group.id, page).unsafeRunSync().items shouldBe Seq()
        db.getEvent(group.id, slug).unsafeRunSync() shouldBe None
        val event = db.createEvent(group.id, slug, Event.Name("name"), user.id).unsafeRunSync()
        db.getEvents(group.id, page).unsafeRunSync().items shouldBe Seq(event)
        db.getEvent(group.id, slug).unsafeRunSync() shouldBe Some(event)
      }
      it("should fail to create an event when the group does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        an[Exception] should be thrownBy db.createEvent(Group.Id.generate(), slug, Event.Name("name"), user.id).unsafeRunSync()
      }
      it("should fail on duplicate slug for the same group") {
        val (user, group) = createEltsForEvents().unsafeRunSync()
        db.createEvent(group.id, slug, Event.Name("name"), user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createEvent(group.id, slug, Event.Name("name"), user.id).unsafeRunSync()
      }

      def createEltsForEvents(): IO[(User, Group)] = {
        for {
          user <- db.createUser(firstName, lastName, email)
          group <- db.createGroup(Group.Slug.from("slug").get, Group.Name("name"), "desc", user.id)
        } yield (user, group)
      }
    }
    describe("Cfp") {
      val slug = Cfp.Slug.from("slug").get
      it("should create and retrieve a cfp for a group") {
        val (user, group) = createEltsForCfp().unsafeRunSync()
        db.getCfps(page).unsafeRunSync().items shouldBe Seq()
        db.getCfp(slug).unsafeRunSync() shouldBe None
        db.getCfp(group.id).unsafeRunSync() shouldBe None
        val cfp = db.createCfp(slug, Cfp.Name("name"), "desc", group.id, user.id).unsafeRunSync()
        db.getCfps(page).unsafeRunSync().items shouldBe Seq(cfp)
        db.getCfp(slug).unsafeRunSync() shouldBe Some(cfp)
        db.getCfp(cfp.id).unsafeRunSync() shouldBe Some(cfp)
        db.getCfp(group.id).unsafeRunSync() shouldBe Some(cfp)
      }
      it("should fail to create a cfp when the group does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        an[Exception] should be thrownBy db.createCfp(slug, Cfp.Name("name"), "desc", Group.Id.generate(), user.id).unsafeRunSync()
      }
      it("should fail to create two cfp for a group") {
        val (user, group) = createEltsForCfp().unsafeRunSync()
        db.createCfp(slug, Cfp.Name("name"), "desc", group.id, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createCfp(slug, Cfp.Name("name"), "desc", group.id, user.id).unsafeRunSync()
      }
      it("should fail on duplicate slug") {
        val (user, group1) = createEltsForCfp().unsafeRunSync()
        val group2 = db.createGroup(Group.Slug.from("slug2").get, Group.Name("name"), "desc", user.id).unsafeRunSync()
        db.createCfp(slug, Cfp.Name("name"), "desc", group1.id, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createCfp(slug, Cfp.Name("name"), "desc", group2.id, user.id).unsafeRunSync()
      }

      def createEltsForCfp(): IO[(User, Group)] = {
        for {
          user <- db.createUser(firstName, lastName, email)
          group <- db.createGroup(Group.Slug.from("slug").get, Group.Name("name"), "desc", user.id)
        } yield (user, group)
      }
    }
    describe("Talk") {
      val slug = Talk.Slug.from("slug").get
      val data = Talk.Data(slug, Talk.Title("title"), Duration(10, MINUTES), Markdown("desc"))
      it("should create and retrieve") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getTalk(user.id, slug).unsafeRunSync() shouldBe None
        val talk = db.createTalk(data, user.id).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq(talk)
        db.getTalk(user.id, slug).unsafeRunSync() shouldBe Some(talk)
      }
      it("should not retrieve not owned talks") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val user2 = db.createUser("A", "A", Email("aaa@aaa.com")).unsafeRunSync()
        val talk = db.createTalk(data, user2.id).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getTalk(user.id, slug).unsafeRunSync() shouldBe None
      }
      it("should fail on duplicate slug on same user") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val user2 = db.createUser("A", "A", Email("aaa@aaa.com")).unsafeRunSync()
        db.createTalk(data, user.id).unsafeRunSync()
        db.createTalk(data, user2.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createTalk(data, user.id).unsafeRunSync()
      }
      it("should update talk data") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.createTalk(data, user.id).unsafeRunSync()
        db.getTalk(user.id, slug).unsafeRunSync().map(_.data) shouldBe Some(data)
        val slug2 = Talk.Slug.from("slug2").get
        val data2 = Talk.Data(slug2, Talk.Title("title 2"), Duration(15, MINUTES), Markdown("desc 2"))
        db.updateTalk(user.id, slug)(data2).unsafeRunSync()
        db.getTalk(user.id, slug).unsafeRunSync() shouldBe None
        db.getTalk(user.id, slug2).unsafeRunSync().map(_.data) shouldBe Some(data2)
      }
      it("should fail to change slug for an existing one") {
        val slug2 = Talk.Slug.from("slug2").get
        val data2 = Talk.Data(slug2, Talk.Title("title 2"), Duration(15, MINUTES), Markdown("desc 2"))
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.createTalk(data, user.id).unsafeRunSync()
        db.createTalk(data2, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.updateTalk(user.id, slug)(data.copy(slug = slug2)).unsafeRunSync()
      }
      it("should update the status") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.createTalk(data, user.id).unsafeRunSync()
        db.getTalk(user.id, slug).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Draft)
        db.updateTalkStatus(user.id, slug)(Talk.Status.Public).unsafeRunSync()
        db.getTalk(user.id, slug).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Public)
      }
    }
    describe("Proposal") {
      val desc = Markdown("desc")
      it("should create and retrieve a proposal for a group and talk") {
        val (user, _, cfp, talk) = createEltsForProposal().unsafeRunSync()
        db.getProposals(talk.id, page).unsafeRunSync().items shouldBe Seq()
        db.getProposals(cfp.id, page).unsafeRunSync().items shouldBe Seq()
        val proposal = db.createProposal(talk.id, cfp.id, Talk.Title("title"), desc, user.id).unsafeRunSync()
        db.getProposals(talk.id, page).unsafeRunSync().items shouldBe Seq(cfp -> proposal)
        db.getProposals(cfp.id, page).unsafeRunSync().items shouldBe Seq(proposal)
        db.getProposal(proposal.id).unsafeRunSync() shouldBe Some(proposal)
      }
      it("should fail to create a proposal when talk does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val group = db.createGroup(Group.Slug.from("slug").get, Group.Name("name"), "desc", user.id).unsafeRunSync()
        val cfp = db.createCfp(Cfp.Slug.from("slug").get, Cfp.Name("name"), "desc", group.id, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(Talk.Id.generate(), cfp.id, Talk.Title("title"), desc, user.id).unsafeRunSync()
      }
      it("should fail to create a proposal when cfp does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val talk = db.createTalk(Talk.Data(Talk.Slug.from("slug").get, Talk.Title("title"), Duration(10, MINUTES), desc), user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(talk.id, Cfp.Id.generate(), Talk.Title("title"), desc, user.id).unsafeRunSync()
      }
      it("should fail on duplicate cfp and talk") {
        val (user, _, cfp, talk) = createEltsForProposal().unsafeRunSync()
        db.createProposal(talk.id, cfp.id, Talk.Title("title"), desc, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(talk.id, cfp.id, Talk.Title("title"), desc, user.id).unsafeRunSync()
      }

      def createEltsForProposal(): IO[(User, Group, Cfp, Talk)] = {
        for {
          user <- db.createUser(firstName, lastName, email)
          group <- db.createGroup(Group.Slug.from("slug").get, Group.Name("name"), "desc", user.id)
          cfp <- db.createCfp(Cfp.Slug.from("slug").get, Cfp.Name("name"), "desc", group.id, user.id)
          talk <- db.createTalk(Talk.Data(Talk.Slug.from("slug").get, Talk.Title("title"), Duration(10, MINUTES), desc), user.id)
        } yield (user, group, cfp, talk)
      }
    }
    describe("insertMockData") {
      it("should not fail") {
        db.insertMockData().unsafeRunSync() shouldBe Done
      }
    }
  }
}
