package fr.gospeak.infra.services.storage.sql.tables.testingutils

import java.time.Instant

import cats.effect.IO
import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import doobie.scalatest.IOChecker
import fr.gospeak.core.domain._
import fr.gospeak.infra.services.storage.sql.GospeakDbSql
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.infra.testingutils.Values
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}

trait TableSpec extends FunSpec with Matchers with IOChecker with BeforeAndAfterAll with RandomDataGenerator {
  protected val db: GospeakDbSql = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  protected val now: Instant = random[Instant]
  protected val user: User = random[User]
  protected val group: Group = random[Group]
  protected val event: Event = random[Event]
  protected val cfp: Cfp = random[Cfp]
  protected val talk: Talk = random[Talk]
  protected val proposal: Proposal = random[Proposal]
  protected val params = Page.Params()
  protected val slides: Slides = random[Slides]
  protected val video: Video = random[Video]

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()
}
