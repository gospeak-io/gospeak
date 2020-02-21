package gospeak.infra.services.storage.sql.testingutils

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import doobie.scalatest.IOChecker
import doobie.util.testing.Analyzable
import gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest, UserAskToJoinAGroupRequest}
import gospeak.core.domain._
import gospeak.core.domain.utils._
import gospeak.core.testingutils.Generators._
import gospeak.infra.services.storage.sql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Delete, Insert, Select, SelectPage, Update}
import gospeak.infra.testingutils.Values
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Page, Slides, Video}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class RepoSpec extends AnyFunSpec with Matchers with IOChecker with BeforeAndAfterEach with RandomDataGenerator {
  protected val db: GsRepoSql = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  protected val userRepo: UserRepoSql = db.user
  protected val userRequestRepo: UserRequestRepoSql = db.userRequest
  protected val talkRepo: TalkRepoSql = db.talk
  protected val groupRepo: GroupRepoSql = db.group
  protected val cfpRepo: CfpRepoSql = db.cfp
  protected val partnerRepo: PartnerRepoSql = db.partner
  protected val venueRepo: VenueRepoSql = db.venue
  protected val eventRepo: EventRepoSql = db.event
  protected val proposalRepo: ProposalRepoSql = db.proposal
  protected val contactRepo: ContactRepoSql = db.contact
  protected val now: Instant = random[Instant]
  protected val ldt: LocalDateTime = random[LocalDateTime]
  protected val user: User = random[User]
  protected val group: Group = random[Group]
  protected val groupSettings: Group.Settings = random[Group.Settings]
  protected val cfp: Cfp = random[Cfp]
  protected val event: Event = random[Event].copy(cfp = None)
  protected val talk: Talk = random[Talk]
  protected val proposal: Proposal = random[Proposal]
  protected val rating: Proposal.Rating = random[Proposal.Rating]
  protected val partner: Partner = random[Partner]
  protected val venue: Venue = random[Venue]
  protected val sponsorPack: SponsorPack = random[SponsorPack]
  protected val sponsor: Sponsor = random[Sponsor]
  protected val slides: Slides = random[Slides]
  protected val video: Video = random[Video]
  protected val contact: Contact = random[Contact]
  protected val comment: Comment = random[Comment]
  protected val externalEvent: ExternalEvent = random[ExternalEvent]
  protected val externalCfp: ExternalCfp = random[ExternalCfp]
  protected val externalProposal: ExternalProposal = random[ExternalProposal]
  protected val accountValidationRequest: AccountValidationRequest = random[AccountValidationRequest]
  protected val passwordResetRequest: PasswordResetRequest = random[PasswordResetRequest]
  protected val userAskToJoinAGroupRequest: UserAskToJoinAGroupRequest = random[UserAskToJoinAGroupRequest]
  protected val userRequest: UserRequest = accountValidationRequest
  protected val member: Group.Member = random[Group.Member]
  protected val rsvp: Event.Rsvp = random[Event.Rsvp]

  protected val Seq(userData1, userData2, userData3) = random[User.Data](10).distinctBy(_.email).take(3)
  protected val Seq(groupData1, groupData2) = random[Group.Data](2)
  protected val Seq(contactData1, contactData2) = random[Contact.Data](2)
  protected val Seq(partnerData1, partnerData2) = random[Partner.Data](2)
  protected val Seq(venueData1, venueData2) = random[Venue.Data](2)
  protected val cfpData1: Cfp.Data = random[Cfp.Data]
  protected val eventData1: Event.Data = random[Event.Data].copy(cfp = None)
  protected val Seq(talkData1, talkData2) = random[Talk.Data](2)
  protected val proposalData1: Proposal.Data = random[Proposal.Data]
  protected val speakers: NonEmptyList[User.Id] = NonEmptyList.fromListUnsafe(random[User.Id](3).toList)
  protected val params: Page.Params = Page.Params()

  protected implicit val orgaCtx: OrgaCtx = FakeOrgaCtx(now, user, group)
  protected implicit val userCtx: UserCtx = FakeUserCtx(now, user)
  protected implicit val userAwareCtx: UserAwareCtx = FakeUserAwareCtx(now, Some(user))

  override def beforeEach(): Unit = db.migrate().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  protected def createUserAndGroup(): IO[(User, Group, OrgaCtx)] = for {
    user <- userRepo.create(userData1, now, None)
    group <- groupRepo.create(groupData1)(FakeCtx(now, user))
    ctx = FakeCtx(now, user, group)
  } yield (user, group, ctx)

  protected def createPartnerAndVenue(user: User, group: Group)(implicit ctx: OrgaCtx): IO[(Partner, Venue)] = for {
    partner <- partnerRepo.create(partnerData1)(ctx)
    contact <- venueData1.contact.map(_ => contactRepo.create(contactData1.copy(partner = partner.id))(ctx)).sequence
    venue <- venueRepo.create(partner.id, venueData1.copy(contact = contact.map(_.id)))(ctx)
  } yield (partner, venue)

  protected def createUserGroupCfpAndTalk(): IO[(User, Group, Cfp, Talk)] = for {
    (user, group, ctx) <- createUserAndGroup()
    cfp <- cfpRepo.create(cfpData1)(ctx)
    talk <- talkRepo.create(talkData1)(ctx)
  } yield (user, group, cfp, talk)

  protected def mapFields(fields: String, f: String => String): String = RepoSpec.mapFields(fields, f)

  protected def check[A](q: Insert[A], req: String): Unit = {
    q.fr.update.sql shouldBe req
    check(q.fr.update)
  }

  protected def check(q: Update, req: String): Unit = {
    q.fr.update.sql shouldBe req
    check(q.fr.update)
  }

  protected def check(q: Delete, req: String): Unit = {
    q.fr.update.sql shouldBe req
    check(q.fr.update)
  }

  protected def check[A](q: Select[A], req: String)(implicit a: Analyzable[doobie.Query0[A]]): Unit = {
    q.fr.query.sql shouldBe req
    check(q.query)
  }

  protected def check[A, C <: BasicCtx](q: SelectPage[A, C], req: String, checkCount: Boolean = true)(implicit a: Analyzable[doobie.Query0[A]]): Unit = {
    q.fr.query.sql shouldBe req
    check(q.query)
    if (checkCount) {
      // checkCount is here to avoid test errors with Timestamp type when in sub-query
      // ex: SELECT count(*) FROM (SELECT e.id FROM events e WHERE e.group_id=? AND e.start > ?) as cnt
      // => ✕ P02 Timestamp  →  VARCHAR (VARCHAR)
      //    Timestamp is not coercible to VARCHAR (VARCHAR) according to the
      //    JDBC specification. Expected schema type was TIMESTAMP.
      // => but e.start IS a TIMESTAMP type, it's just not recognized by doobie type-checker :(
      check(q.countQuery)
    }
  }
}

object RepoSpec {
  def mapFields(fields: String, f: String => String): String =
    fields.split(", ").map(f).mkString(", ")
}
