package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import fr.gospeak.core.domain._
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.infra.testingutils.Values
import fr.gospeak.libs.scalautils.domain._
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}

class GospeakDbSqlSpec extends FunSpec with Matchers with BeforeAndAfterEach with RandomDataGenerator {
  private val db = Values.db
  private val now = random[Instant]
  private val Seq(userSlug, userSlug2, userSlug3) = random[User.Slug](3)
  private val Seq(email, email2, email3) = random[EmailAddress](3)
  private val firstName = "John"
  private val lastName = "Doe"
  private val avatar = Avatar(Url.from("https://secure.gravatar.com/avatar/f755e6e8914df5cbaa74d30dd7de1ae2?size=100&default=wavatar").right.get, Avatar.Source.Gravatar)
  private val Seq(groupData, groupData2) = random[Group.Data](2)
  private val eventData = random[Event.Data]
  private val cfpData = random[Cfp.Data]
  private val Seq(talkData, talkData2) = random[Talk.Data](2)
  private val proposalData = random[Proposal.Data]
  private val speakers = NonEmptyList.fromListUnsafe(random[User.Id](3).toList)
  private val page = Page.Params()

  override def beforeEach(): Unit = db.createTables().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  describe("GospeakDbSql") {
    describe("User") {
      it("should create and retrieve a user") {
        db.getUser(email).unsafeRunSync() shouldBe None
        db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        db.getUser(email).unsafeRunSync().map(_.email) shouldBe Some(email)
      }
      it("should fail on duplicate slug") {
        db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createUser(userSlug, firstName, lastName, email2, avatar, now).unsafeRunSync()
      }
      it("should fail on duplicate email") {
        db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createUser(userSlug2, firstName, lastName, email, avatar, now).unsafeRunSync()
      }
      it("should select users by ids") {
        val user1 = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        val user2 = db.createUser(userSlug2, firstName, lastName, email2, avatar, now).unsafeRunSync()
        db.createUser(userSlug3, firstName, lastName, email3, avatar, now).unsafeRunSync()
        db.getUsers(Seq(user1.id, user2.id)).unsafeRunSync() should contain theSameElementsAs Seq(user1, user2)
      }
    }
    describe("Group") {
      it("should create and retrieve a group") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getGroup(user.id, groupData.slug).unsafeRunSync() shouldBe None
        val group = db.createGroup(groupData, user.id, now).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq(group)
        db.getGroup(user.id, groupData.slug).unsafeRunSync() shouldBe Some(group)
      }
      it("should not retrieve not owned groups") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        val user2 = db.createUser(userSlug2, firstName, lastName, email2, avatar, now).unsafeRunSync()
        db.createGroup(groupData, user2.id, now).unsafeRunSync()
        db.getGroups(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getGroup(user.id, groupData.slug).unsafeRunSync() shouldBe None
      }
      it("should fail on duplicate slug") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        db.createGroup(groupData, user.id, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createGroup(groupData, user.id, now).unsafeRunSync()
      }
    }
    describe("Event") {
      it("should create and retrieve an event for a group") {
        val (user, group) = createUserAndGroup().unsafeRunSync()
        db.getEvents(group.id, page).unsafeRunSync().items shouldBe Seq()
        db.getEvent(group.id, eventData.slug).unsafeRunSync() shouldBe None
        val event = db.createEvent(group.id, eventData, user.id, now).unsafeRunSync()
        db.getEvents(group.id, page).unsafeRunSync().items shouldBe Seq(event)
        db.getEvent(group.id, eventData.slug).unsafeRunSync() shouldBe Some(event)
      }
      it("should fail to create an event when the group does not exists") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createEvent(Group.Id.generate(), eventData, user.id, now).unsafeRunSync()
      }
      it("should fail on duplicate slug for the same group") {
        val (user, group) = createUserAndGroup().unsafeRunSync()
        db.createEvent(group.id, eventData, user.id, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createEvent(group.id, eventData, user.id, now).unsafeRunSync()
      }
    }
    describe("Cfp") {
      it("should create and retrieve a cfp for a group") {
        val (user, group) = createUserAndGroup().unsafeRunSync()
        val talkId = Talk.Id.generate()
        db.getCfpAvailables(talkId, page).unsafeRunSync().items shouldBe Seq()
        db.getCfp(cfpData.slug).unsafeRunSync() shouldBe None
        db.getCfp(group.id).unsafeRunSync() shouldBe None
        val cfp = db.createCfp(group.id, cfpData, user.id, now).unsafeRunSync()
        db.getCfpAvailables(talkId, page).unsafeRunSync().items shouldBe Seq(cfp)
        db.getCfp(cfpData.slug).unsafeRunSync() shouldBe Some(cfp)
        db.getCfp(cfp.id).unsafeRunSync() shouldBe Some(cfp)
        db.getCfp(group.id).unsafeRunSync() shouldBe Some(cfp)
      }
      it("should fail to create a cfp when the group does not exists") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createCfp(Group.Id.generate(), cfpData, user.id, now).unsafeRunSync()
      }
      it("should fail to create two cfp for a group") {
        val (user, group) = createUserAndGroup().unsafeRunSync()
        db.createCfp(group.id, cfpData, user.id, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createCfp(group.id, cfpData, user.id, now).unsafeRunSync()
      }
      it("should fail on duplicate slug") {
        val (user, group1) = createUserAndGroup().unsafeRunSync()
        val group2 = db.createGroup(groupData2, user.id, now).unsafeRunSync()
        db.createCfp(group1.id, cfpData, user.id, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createCfp(group2.id, cfpData, user.id, now).unsafeRunSync()
      }
    }
    describe("Talk") {
      it("should create and retrieve") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getTalk(user.id, talkData.slug).unsafeRunSync() shouldBe None
        val talk = db.createTalk(user.id, talkData, now).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq(talk)
        db.getTalk(user.id, talkData.slug).unsafeRunSync() shouldBe Some(talk)
      }
      it("should not retrieve not owned talks") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        val user2 = db.createUser(userSlug2, firstName, lastName, email2, avatar, now).unsafeRunSync()
        val talk = db.createTalk(user2.id, talkData, now).unsafeRunSync()
        db.getTalks(user.id, page).unsafeRunSync().items shouldBe Seq()
        db.getTalk(user.id, talkData.slug).unsafeRunSync() shouldBe None
      }
      it("should fail on duplicate slug on same user") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        val user2 = db.createUser(userSlug2, firstName, lastName, email2, avatar, now).unsafeRunSync()
        db.createTalk(user.id, talkData, now).unsafeRunSync()
        db.createTalk(user2.id, talkData, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createTalk(user.id, talkData, now).unsafeRunSync()
      }
      it("should update talk data") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        db.createTalk(user.id, talkData, now).unsafeRunSync()
        db.getTalk(user.id, talkData.slug).unsafeRunSync().map(_.data) shouldBe Some(talkData)
        db.updateTalk(user.id, talkData.slug)(talkData2, now).unsafeRunSync()
        db.getTalk(user.id, talkData.slug).unsafeRunSync() shouldBe None
        db.getTalk(user.id, talkData2.slug).unsafeRunSync().map(_.data) shouldBe Some(talkData2)
      }
      it("should fail to change slug for an existing one") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        db.createTalk(user.id, talkData, now).unsafeRunSync()
        db.createTalk(user.id, talkData2, now).unsafeRunSync()
        an[Exception] should be thrownBy db.updateTalk(user.id, talkData.slug)(talkData.copy(slug = talkData2.slug), now).unsafeRunSync()
      }
      it("should update the status") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        db.createTalk(user.id, talkData, now).unsafeRunSync()
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
        val proposal = db.createProposal(talk.id, cfp.id, proposalData, speakers, user.id, now).unsafeRunSync()
        db.getProposals(talk.id, page).unsafeRunSync().items shouldBe Seq(cfp -> proposal)
        db.getProposals(cfp.id, page).unsafeRunSync().items shouldBe Seq(proposal)
        db.getProposal(proposal.id).unsafeRunSync() shouldBe Some(proposal)
      }
      it("should fail to create a proposal when talk does not exists") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        val group = db.createGroup(groupData, user.id, now).unsafeRunSync()
        val cfp = db.createCfp(group.id, cfpData, user.id, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(Talk.Id.generate(), cfp.id, proposalData, speakers, user.id, now).unsafeRunSync()
      }
      it("should fail to create a proposal when cfp does not exists") {
        val user = db.createUser(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
        val talk = db.createTalk(user.id, talkData, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(talk.id, Cfp.Id.generate(), proposalData, speakers, user.id, now).unsafeRunSync()
      }
      it("should fail on duplicate cfp and talk") {
        val (user, _, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
        db.createProposal(talk.id, cfp.id, proposalData, speakers, user.id, now).unsafeRunSync()
        an[Exception] should be thrownBy db.createProposal(talk.id, cfp.id, proposalData, speakers, user.id, now).unsafeRunSync()
      }

    }
    describe("insertMockData") {
      it("should not fail") {
        db.insertMockData().unsafeRunSync() shouldBe Done
      }
    }
  }

  def createUserAndGroup(): IO[(User, Group)] = for {
    user <- db.createUser(userSlug, firstName, lastName, email, avatar, now)
    group <- db.createGroup(groupData, user.id, now)
  } yield (user, group)

  def createUserGroupCfpAndTalk(): IO[(User, Group, Cfp, Talk)] = for {
    (user, group) <- createUserAndGroup()
    cfp <- db.createCfp(group.id, cfpData, user.id, now)
    talk <- db.createTalk(user.id, talkData, now)
  } yield (user, group, cfp, talk)
}
