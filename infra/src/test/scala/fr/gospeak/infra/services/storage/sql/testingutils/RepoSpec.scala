package fr.gospeak.infra.services.storage.sql.testingutils

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import doobie.scalatest.IOChecker
import fr.gospeak.core.domain._
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.infra.services.storage.sql._
import fr.gospeak.infra.testingutils.Values
import fr.gospeak.libs.scalautils.domain._
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}

class RepoSpec extends FunSpec with Matchers with IOChecker with BeforeAndAfterEach with RandomDataGenerator {
  protected val db: GospeakDbSql = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  protected val userRepo: UserRepoSql = db.user
  protected val userRequestRepo: UserRequestRepoSql = db.userRequest
  protected val groupRepo: GroupRepoSql = db.group
  protected val cfpRepo: CfpRepoSql = db.cfp
  protected val eventRepo: EventRepoSql = db.event
  protected val talkRepo: TalkRepoSql = db.talk
  protected val proposalRepo: ProposalRepoSql = db.proposal

  protected val now: Instant = random[Instant]
  protected val user: User = random[User]
  protected val group: Group = random[Group]
  protected val cfp: Cfp = random[Cfp]
  protected val event: Event = random[Event].copy(cfp = None)
  protected val talk: Talk = random[Talk]
  protected val proposal: Proposal = random[Proposal]
  protected val params = Page.Params()
  protected val slides: Slides = random[Slides]
  protected val video: Video = random[Video]

  protected val Seq(userData1, userData2, userData3) = random[User.Data](3)
  protected val Seq(groupData1, groupData2) = random[Group.Data](2)
  protected val cfpData1: Cfp.Data = random[Cfp.Data]
  protected val eventData1: Event.Data = random[Event.Data].copy(cfp = None)
  protected val Seq(talkData1, talkData2) = random[Talk.Data](2)
  protected val proposalData1: Proposal.Data = random[Proposal.Data]
  protected val speakers: NonEmptyList[User.Id] = NonEmptyList.fromListUnsafe(random[User.Id](3).toList)
  protected val page = Page.Params()

  override def beforeEach(): Unit = db.migrate().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  protected def createUserAndGroup(): IO[(User, Group)] = for {
    user <- userRepo.create(userData1, now)
    group <- groupRepo.create(groupData1, user.id, now)
  } yield (user, group)

  protected def createUserGroupCfpAndTalk(): IO[(User, Group, Cfp, Talk)] = for {
    (user, group) <- createUserAndGroup()
    cfp <- cfpRepo.create(group.id, cfpData1, user.id, now)
    talk <- talkRepo.create(user.id, talkData1, now)
  } yield (user, group, cfp, talk)
}
