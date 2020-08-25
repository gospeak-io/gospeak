package gospeak.libs.sql.testingutils

import java.util.UUID

import cats.effect.{ContextShift, IO}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.util.transactor.Transactor
import gospeak.libs.testingutils.BaseSpec
import org.flywaydb.core.Flyway
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.ExecutionContext

class SqlSpec extends BaseSpec with BeforeAndAfterEach {
  protected val dbDriver = "org.h2.Driver"
  protected val dbUrl = s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  protected val xa: doobie.Transactor[IO] = Transactor.fromDriverManager[IO](dbDriver, dbUrl, "", "")
  private val flyway = {
    val config = new HikariConfig()
    config.setDriverClassName(dbDriver)
    config.setJdbcUrl(dbUrl)
    Flyway.configure()
      .dataSource(new HikariDataSource(config))
      .locations("classpath:sql")
      .load()
  }

  override def beforeEach(): Unit = flyway.migrate()

  override def afterEach(): Unit = flyway.clean()
}
