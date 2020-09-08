package gospeak.infra.services.storage.sql.utils

import java.time.LocalDate
import java.util.UUID

import cats.effect.{ContextShift, IO}
import doobie.syntax.connectionio._
import doobie.syntax.string._
import doobie.util.transactor.Transactor
import gospeak.core.testingutils.Generators._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.testingutils.BaseSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.ExecutionContext

class DoobieMappingsSpec extends BaseSpec with ScalaCheckPropertyChecks {
  protected val dbDriver = "org.h2.Driver"
  protected val dbUrl = s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  protected val xa: doobie.Transactor[IO] = Transactor.fromDriverManager[IO](dbDriver, dbUrl, "", "")

  describe("DoobieMappings") {
    it("should persist LocalDate using DATE field") {
      sql"CREATE TABLE tmp (value DATE)".update.run.transact(xa).unsafeRunSync() shouldBe 0
      forAll { d: LocalDate =>
        println(d)
        sql"INSERT INTO tmp (value) VALUES ($d)".update.run.transact(xa).unsafeRunSync() shouldBe 1
        sql"SELECT value FROM tmp".query[LocalDate].unique.transact(xa).unsafeRunSync() shouldBe d
        sql"DELETE FROM tmp".update.run.transact(xa).unsafeRunSync() shouldBe 1
      }
      sql"DROP TABLE tmp".update.run.transact(xa).unsafeRunSync() shouldBe 0
    }
  }
}
