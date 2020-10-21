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
import gospeak.infra.testingutils.{BaseSpec, Values}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain.{Page, Tag, Url}
import gospeak.libs.sql.dsl.{Query, Table}
import org.scalatest.BeforeAndAfterEach

class RepoSpec extends BaseSpec with IOChecker with BeforeAndAfterEach with RandomDataGenerator {
  protected val db: GsRepoSql = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  protected val userRepo: UserRepoSql = db.user
  protected val talkRepo: TalkRepoSql = db.talk
  protected val groupRepo: GroupRepoSql = db.group
  protected val groupSettingsRepo: GroupSettingsRepoSql = db.groupSettings
  protected val cfpRepo: CfpRepoSql = db.cfp
  protected val partnerRepo: PartnerRepoSql = db.partner
  protected val contactRepo: ContactRepoSql = db.contact
  protected val venueRepo: VenueRepoSql = db.venue
  protected val sponsorPackRepo: SponsorPackRepoSql = db.sponsorPack
  protected val sponsorRepo: SponsorRepoSql = db.sponsor
  protected val eventRepo: EventRepoSql = db.event
  protected val proposalRepo: ProposalRepoSql = db.proposal
  protected val commentRepo: CommentRepoSql = db.comment
  protected val externalEventRepo: ExternalEventRepoSql = db.externalEvent
  protected val externalCfpRepo: ExternalCfpRepoSql = db.externalCfp
  protected val externalProposalRepo: ExternalProposalRepoSql = db.externalProposal
  protected val userRequestRepo: UserRequestRepoSql = db.userRequest
  protected val videoRepo: VideoRepoSql = db.video

  protected val Seq(credentials, credentials2) = random[User.Credentials](2)
  protected val user: User = random[User]
  protected val talk: Talk = random[Talk]
  protected val group: Group = random[Group]
  protected val member: Group.Member = random[Group.Member]
  protected val Seq(groupSettings, groupSettings2) = random[Group.Settings](2)
  protected val cfp: Cfp = random[Cfp]
  protected val partner: Partner = random[Partner]
  protected val contact: Contact = random[Contact]
  protected val venue: Venue = random[Venue]
  protected val sponsorPack: SponsorPack = random[SponsorPack]
  protected val sponsor: Sponsor = random[Sponsor]
  protected val event: Event = random[Event].copy(cfp = None)
  protected val rsvp: Event.Rsvp = random[Event.Rsvp]
  protected val proposal: Proposal = random[Proposal]
  protected val rating: Proposal.Rating = random[Proposal.Rating]
  protected val comment: Comment = random[Comment]
  protected val externalEvent: ExternalEvent = random[ExternalEvent]
  protected val externalCfp: ExternalCfp = random[ExternalCfp]
  protected val externalProposal: ExternalProposal = random[ExternalProposal]
  protected val accountValidationRequest: AccountValidationRequest = random[AccountValidationRequest]
  protected val passwordResetRequest: PasswordResetRequest = random[PasswordResetRequest]
  protected val userAskToJoinAGroupRequest: UserAskToJoinAGroupRequest = random[UserAskToJoinAGroupRequest]
  protected val userRequest: UserRequest = accountValidationRequest
  protected val video: Video = random[Video]

  protected val Seq(userData1, userData2, userData3) = random[User.Data](10).distinctBy(_.slug).distinctBy(_.email).take(3)
  protected val Seq(talkData1, talkData2, talkData3) = random[Talk.Data](3)
  protected val Seq(groupData1, groupData2) = random[Group.Data](2)
  protected val Seq(cfpData1, cfpData2) = random[Cfp.Data](2)
  protected val Seq(partnerData1, partnerData2) = random[Partner.Data](2)
  protected val Seq(contactData1, contactData2) = random[Contact.Data](2)
  protected val Seq(venueData1, venueData2) = random[Venue.Data](2)
  protected val Seq(sponsorPackData1, sponsorPackData2) = random[SponsorPack.Data](2)
  protected val Seq(sponsorData1, sponsorData2) = random[Sponsor.Data](2)
  protected val Seq(eventData1, eventData2) = random[Event.Data](2).map(_.copy(cfp = None))
  protected val Seq(proposalData1, proposalData2) = random[Proposal.Data](2)
  protected val Seq(commentData1, commentData2, commentData3) = random[Comment.Data](3)
  protected val Seq(externalEventData1, externalEventData2) = random[ExternalEvent.Data](2)
  protected val Seq(externalCfpData1, externalCfpData2) = random[ExternalCfp.Data](2)
  protected val Seq(externalProposalData1, externalProposalData2) = random[ExternalProposal.Data](2)
  protected val Seq(videoData1, videoData2) = random[Video.Data](2).map(v => v.copy(lang = v.lang.take(2)))

  private val n: Instant = Instant.now()
  protected val now: Instant = n.minusNanos(n.getNano)
  protected val nowLDT: LocalDateTime = TimeUtils.toLocalDateTime(now)
  protected val Seq(urlSlides, urlSlides2) = random[Url.Slides](2)
  protected val Seq(urlVideo, urlVideo2) = random[Url.Video](2)
  protected val urlVideos: Url.Videos = random[Url.Videos]
  protected val speakers: NonEmptyList[User.Id] = random[User.Id](3).toNelUnsafe
  protected val tag: Tag = random[Tag]
  protected val params: Page.Params = Page.Params.defaults

  protected val adminCtx: FakeAdminCtx = FakeAdminCtx(now, user)
  protected implicit val orgaCtx: FakeOrgaCtx = FakeOrgaCtx(now, user, group)
  protected implicit val userCtx: UserCtx = FakeUserCtx(now, user)
  protected implicit val userAwareCtx: FakeUserAwareCtx = FakeUserAwareCtx(now, Some(user))

  override def beforeEach(): Unit = db.migrate().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  protected def createUser(credentials: User.Credentials = credentials,
                           userData: User.Data = userData1): IO[(User, FakeUserCtx)] = for {
    user <- userRepo.create(userData, now, None)
    _ <- userRepo.createCredentials(credentials)
    _ <- userRepo.createLoginRef(credentials.login, user.id)
    ctx = FakeCtx(now, user)
  } yield (user, ctx)

  protected def createAdmin(credentials: User.Credentials = credentials,
                            userData: User.Data = userData1): IO[(User, FakeAdminCtx)] =
    createUser(credentials, userData).map { case (user, _) => (user, FakeAdminCtx(now, user)) }

  protected def createOrga(credentials: User.Credentials = credentials,
                           userData: User.Data = userData1,
                           groupData: Group.Data = groupData1): IO[(User, Group, FakeOrgaCtx)] = for {
    (user, _) <- createUser(credentials, userData)
    group <- groupRepo.create(groupData)(FakeCtx(now, user))
    ctx = FakeCtx(now, user, group)
  } yield (user, group, ctx)

  protected def createCfpAndTalk(credentials: User.Credentials = credentials,
                                 userData: User.Data = userData1,
                                 groupData: Group.Data = groupData1,
                                 cfpData: Cfp.Data = cfpData1,
                                 talkData: Talk.Data = talkData1): IO[(User, Group, Cfp, Talk, FakeOrgaCtx)] = for {
    (user, group, ctx) <- createOrga(credentials, userData, groupData)
    cfp <- cfpRepo.create(cfpData)(ctx)
    talk <- talkRepo.create(talkData)(ctx)
  } yield (user, group, cfp, talk, ctx)

  protected def createPartnerAndVenue(partnerData: Partner.Data = partnerData1,
                                      contactData: Contact.Data = contactData1,
                                      venueData: Venue.Data = venueData1)(implicit ctx: OrgaCtx): IO[(Partner, Venue, Option[Contact])] = for {
    partner <- partnerRepo.create(partnerData)(ctx)
    contact <- venueData.contact.map(_ => contactRepo.create(contactData.copy(partner = partner.id))(ctx)).sequence
    venue <- venueRepo.create(partner.id, venueData.copy(contact = contact.map(_.id)))(ctx)
  } yield (partner, venue, contact)

  protected def createProposal(credentials: User.Credentials = credentials,
                               userData: User.Data = userData1,
                               groupData: Group.Data = groupData1,
                               cfpData: Cfp.Data = cfpData1,
                               talkData: Talk.Data = talkData1,
                               partnerData: Partner.Data = partnerData1,
                               contactData: Contact.Data = contactData1,
                               venueData: Venue.Data = venueData1,
                               eventData: Event.Data = eventData1,
                               proposalData: Proposal.Data = proposalData1): IO[(User, Group, Cfp, Partner, Venue, Option[Contact], Event, Talk, Proposal, FakeOrgaCtx)] = for {
    (user, group, cfp, talk, ctx) <- createCfpAndTalk(credentials, userData, groupData, cfpData, talkData)
    (partner, venue, contact) <- createPartnerAndVenue(partnerData, contactData, venueData)(ctx)
    event <- eventRepo.create(eventData.copy(cfp = Some(cfp.id), venue = Some(venue.id)))(ctx)
    proposal <- proposalRepo.create(talk.id, cfp.id, proposalData, talk.speakers)(ctx)
    _ <- eventRepo.editTalks(event.slug, event.add(proposal.id).talks)(ctx)
    _ <- proposalRepo.accept(cfp.slug, proposal.id, event.id)
    eventWithProposal = event.copy(talks = List(proposal.id))
    proposalWithEvent = proposal.copy(event = Some(event.id), status = Proposal.Status.Accepted)
  } yield (user, group, cfp, partner, venue, contact, eventWithProposal, talk, proposalWithEvent, ctx)

  protected def mapFields(fields: String, f: String => String): String = RepoSpec.mapFields(fields, f)

  protected def check[T <: Table.SqlTable](query: Query.Insert[T], sql: String): Unit = {
    query.fr.update.sql shouldBe sql
    check(query.fr.update)
  }

  protected def check[T <: Table.SqlTable](query: Query.Update[T], sql: String): Unit = {
    query.fr.update.sql shouldBe sql
    check(query.fr.update)
  }

  protected def check[T <: Table.SqlTable](query: Query.Delete[T], sql: String): Unit = {
    query.fr.update.sql shouldBe sql
    check(query.fr.update)
  }

  protected def check[A](query: Query.Select.All[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): List[A] = {
    query.fr.query.sql shouldBe sql
    check(query.query)
    query.run(db.xa).unsafeRunSync()
  }

  protected def check[A](query: Query.Select.Paginated[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): Page[A] = {
    query.fr.query.sql shouldBe sql
    check(query.query)
    query.run(db.xa).unsafeRunSync()
  }

  protected def check[A](query: Query.Select.Optional[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): Option[A] = {
    query.fr.query.sql shouldBe sql
    check(query.query)
    query.run(db.xa).unsafeRunSync()
  }

  protected def check[A](query: Query.Select.One[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): Unit = {
    query.fr.query.sql shouldBe sql
    check(query.query)
    // query.run(db.xa).unsafeRunSync() // can't require that data exist
  }

  protected def check[A](query: Query.Select.Exists[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): Boolean = {
    query.fr.query.sql shouldBe sql
    check(query.query)
    query.run(db.xa).unsafeRunSync()
  }

  // same as `check[A](query: Query.Select.All[A], sql: String)` but avoid type check (not null types become nullable on unions...)
  protected def unsafeCheck[A](query: Query.Select.All[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): List[A] = {
    query.fr.query.sql shouldBe sql
    query.run(db.xa).unsafeRunSync()
  }

  protected def unsafeCheck[A](query: Query.Select.Paginated[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): Page[A] = {
    query.fr.query.sql shouldBe sql
    query.run(db.xa).unsafeRunSync()
  }

  protected def unsafeCheck[A](query: Query.Select.Optional[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): Option[A] = {
    query.fr.query.sql shouldBe sql
    query.run(db.xa).unsafeRunSync()
  }

  protected def unsafeCheck[A](query: Query.Select.One[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): Unit = {
    query.fr.query.sql shouldBe sql
    // query.run(db.xa).unsafeRunSync() // can't require that data exist
  }

  protected def unsafeCheck[A](query: Query.Select.Exists[A], sql: String)(implicit a: Analyzable[doobie.Query0[A]]): Boolean = {
    query.fr.query.sql shouldBe sql
    query.run(db.xa).unsafeRunSync()
  }
}

object RepoSpec {
  val socialFields: String = List("facebook", "instagram", "twitter", "linkedIn", "youtube", "meetup", "eventbrite", "slack", "discord", "github").map("social_" + _).mkString(", ")

  def mapFields(fields: String, f: String => String): String = fields.split(", ").map(f).mkString(", ")
}
