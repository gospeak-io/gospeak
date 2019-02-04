package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import fr.gospeak.core.domain._
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.infra.testingutils.Values
import fr.gospeak.libs.scalautils.domain.{Done, Email, Markdown, Page}
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}

class GospeakDbSqlSpec extends FunSpec with Matchers with BeforeAndAfterEach with RandomDataGenerator {
  private val db = Values.db
  private val firstName = "John"
  private val lastName = "Doe"
  private val Seq(email, email2) = random[Email](2)
  private val desc = Markdown("desc")
  private val Seq(groupSlug, groupSlug2) = random[Group.Slug](2)
  private val eventSlug = random[Event.Slug]
  private val cfpSlug = random[Cfp.Slug]
  private val Seq(talkData, talkData2) = random[Talk.Data](2)
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
      it("should create and retrieve a group") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getGroup(user.id, groupSlug).unsafeRunSync() shouldBe None
        val group = db.createGroup(groupSlug, Group.Name("name"), desc, user.id).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq(group)
        db.getGroup(user.id, groupSlug).unsafeRunSync() shouldBe Some(group)
      }
      it("should not retrieve not owned groups") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val user2 = db.createUser("A", "A", email2).unsafeRunSync()
        db.createGroup(groupSlug, Group.Name("name"), desc, user2.id).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getGroup(user.id, groupSlug).unsafeRunSync() shouldBe None
      }
      it("should fail on duplicate slug") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.createGroup(groupSlug, Group.Name("name"), desc, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createGroup(groupSlug, Group.Name("name"), desc, user.id).unsafeRunSync()
      }
    }
    describe("Event") {
      it("should create and retrieve an event for a group") {
        val (user, group) = createUserAndGroup().unsafeRunSync()
        db.getEvents(group.id, page).unsafeRunSync().items shouldBe Seq()
        db.getEvent(group.id, eventSlug).unsafeRunSync() shouldBe None
        val event = db.createEvent(group.id, eventSlug, Event.Name("name"), Instant.now(), user.id).unsafeRunSync()
        db.getEvents(group.id, page).unsafeRunSync().items shouldBe Seq(event)
        db.getEvent(group.id, eventSlug).unsafeRunSync() shouldBe Some(event)
      }
      it("should fail to create an event when the group does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        an[Exception] should be thrownBy db.createEvent(Group.Id.generate(), eventSlug, Event.Name("name"), Instant.now(), user.id).unsafeRunSync()
      }
      it("should fail on duplicate slug for the same group") {
        val (user, group) = createUserAndGroup().unsafeRunSync()
        db.createEvent(group.id, eventSlug, Event.Name("name"), Instant.now(), user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createEvent(group.id, eventSlug, Event.Name("name"), Instant.now(), user.id).unsafeRunSync()
      }
    }
    describe("Cfp") {
      it("should create and retrieve a cfp for a group") {
        val (user, group) = createUserAndGroup().unsafeRunSync()
        val talkId = Talk.Id.generate()
        db.getCfpAvailables(talkId, page).unsafeRunSync().items shouldBe Seq()
        db.getCfp(cfpSlug).unsafeRunSync() shouldBe None
        db.getCfp(group.id).unsafeRunSync() shouldBe None
        val cfp = db.createCfp(cfpSlug, Cfp.Name("name"), desc, group.id, user.id).unsafeRunSync()
        db.getCfpAvailables(talkId, page).unsafeRunSync().items shouldBe Seq(cfp)
        db.getCfp(cfpSlug).unsafeRunSync() shouldBe Some(cfp)
        db.getCfp(cfp.id).unsafeRunSync() shouldBe Some(cfp)
        db.getCfp(group.id).unsafeRunSync() shouldBe Some(cfp)
      }
      it("should fail to create a cfp when the group does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        an[Exception] should be thrownBy db.createCfp(cfpSlug, Cfp.Name("name"), desc, Group.Id.generate(), user.id).unsafeRunSync()
      }
      it("should fail to create two cfp for a group") {
        val (user, group) = createUserAndGroup().unsafeRunSync()
        db.createCfp(cfpSlug, Cfp.Name("name"), desc, group.id, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createCfp(cfpSlug, Cfp.Name("name"), desc, group.id, user.id).unsafeRunSync()
      }
      it("should fail on duplicate slug") {
        val (user, group1) = createUserAndGroup().unsafeRunSync()
        val group2 = db.createGroup(groupSlug2, Group.Name("name"), desc, user.id).unsafeRunSync()
        db.createCfp(cfpSlug, Cfp.Name("name"), desc, group1.id, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createCfp(cfpSlug, Cfp.Name("name"), desc, group2.id, user.id).unsafeRunSync()
      }
    }
    describe("Talk") {
      it("should create and retrieve") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getTalk(user.id, talkData.slug).unsafeRunSync() shouldBe None
        val talk = db.createTalk(talkData, user.id).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq(talk)
        db.getTalk(user.id, talkData.slug).unsafeRunSync() shouldBe Some(talk)
      }
      it("should not retrieve not owned talks") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val user2 = db.createUser("A", "A", email2).unsafeRunSync()
        val talk = db.createTalk(talkData, user2.id).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getTalk(user.id, talkData.slug).unsafeRunSync() shouldBe None
      }
      it("should fail on duplicate slug on same user") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val user2 = db.createUser("A", "A", email2).unsafeRunSync()
        db.createTalk(talkData, user.id).unsafeRunSync()
        db.createTalk(talkData, user2.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createTalk(talkData, user.id).unsafeRunSync()
      }
      it("should update talk data") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.createTalk(talkData, user.id).unsafeRunSync()
        db.getTalk(user.id, talkData.slug).unsafeRunSync().map(_.data) shouldBe Some(talkData)
        db.updateTalk(user.id, talkData.slug)(talkData2).unsafeRunSync()
        db.getTalk(user.id, talkData.slug).unsafeRunSync() shouldBe None
        db.getTalk(user.id, talkData2.slug).unsafeRunSync().map(_.data) shouldBe Some(talkData2)
      }
      it("should fail to change slug for an existing one") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.createTalk(talkData, user.id).unsafeRunSync()
        db.createTalk(talkData2, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.updateTalk(user.id, talkData.slug)(talkData.copy(slug = talkData2.slug)).unsafeRunSync()
      }
      it("should update the status") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        db.createTalk(talkData, user.id).unsafeRunSync()
        db.getTalk(user.id, talkData.slug).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Draft)
        db.updateTalkStatus(user.id, talkData.slug)(Talk.Status.Public).unsafeRunSync()
        db.getTalk(user.id, talkData.slug).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Public)
      }
    }
    describe("Proposal") {
      it("should create and retrieve a proposal for a group and talk") {
        val (user, _, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
        db.getProposals(talk.id, page).unsafeRunSync().items shouldBe Seq()
        db.getProposals(cfp.id, page).unsafeRunSync().items shouldBe Seq()
        val proposal = db.createProposal(talk.id, cfp.id, Talk.Title("title"), desc, user.id).unsafeRunSync()
        db.getProposals(talk.id, page).unsafeRunSync().items shouldBe Seq(cfp -> proposal)
        db.getProposals(cfp.id, page).unsafeRunSync().items shouldBe Seq(proposal)
        db.getProposal(proposal.id).unsafeRunSync() shouldBe Some(proposal)
      }
      it("should fail to create a proposal when talk does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val group = db.createGroup(groupSlug, Group.Name("name"), desc, user.id).unsafeRunSync()
        val cfp = db.createCfp(cfpSlug, Cfp.Name("name"), desc, group.id, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(Talk.Id.generate(), cfp.id, Talk.Title("title"), desc, user.id).unsafeRunSync()
      }
      it("should fail to create a proposal when cfp does not exists") {
        val user = db.createUser(firstName, lastName, email).unsafeRunSync()
        val talk = db.createTalk(talkData, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(talk.id, Cfp.Id.generate(), Talk.Title("title"), desc, user.id).unsafeRunSync()
      }
      it("should fail on duplicate cfp and talk") {
        val (user, _, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
        db.createProposal(talk.id, cfp.id, Talk.Title("title"), desc, user.id).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(talk.id, cfp.id, Talk.Title("title"), desc, user.id).unsafeRunSync()
      }

    }
    describe("insertMockData") {
      it("should not fail") {
        db.insertMockData().unsafeRunSync() shouldBe Done
      }
    }
  }

  def createUserAndGroup(): IO[(User, Group)] = for {
    user <- db.createUser(firstName, lastName, email)
    group <- db.createGroup(groupSlug, Group.Name("name"), desc, user.id)
  } yield (user, group)

  def createUserGroupCfpAndTalk(): IO[(User, Group, Cfp, Talk)] = for {
    (user, group) <- createUserAndGroup()
    cfp <- db.createCfp(cfpSlug, Cfp.Name("name"), desc, group.id, user.id)
    talk <- db.createTalk(talkData, user.id)
  } yield (user, group, cfp, talk)
}
