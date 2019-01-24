package fr.gospeak.infra.services.storage.sql.tables.testingutils

import cats.effect.IO
import doobie.scalatest.IOChecker
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.infra.services.storage.sql.GospeakDbSql
import fr.gospeak.infra.testingutils.Values
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}

trait TableSpec extends FunSpec with Matchers with IOChecker with BeforeAndAfterAll {
  protected val db: GospeakDbSql = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  protected val userId: User.Id = User.Id.generate()
  protected val groupId: Group.Id = Group.Id.generate()
  protected val eventId: Event.Id = Event.Id.generate()
  protected val cfpId: Cfp.Id = Cfp.Id.generate()
  protected val talkId: Talk.Id = Talk.Id.generate()
  protected val proposalId: Proposal.Id = Proposal.Id.generate()
  protected val params = Page.Params()

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()
}
