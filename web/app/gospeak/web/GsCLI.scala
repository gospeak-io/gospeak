package gospeak.web

import java.util.UUID

import cats.effect.{ContextShift, IO}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.util.transactor.Transactor
import gospeak.libs.scala.FileUtils
import gospeak.libs.sql.generator.Generator
import gospeak.libs.sql.generator.reader.H2Reader
import gospeak.libs.sql.generator.writer.{ScalaWriter, Writer}
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
 * A CLI to perform common tasks
 */
object GsCLI {
  def main(args: Array[String]): Unit = {
    // TODO
  }

  def generateSchemas(): Try[Unit] = {
    val dbDriver = "org.h2.Driver"
    val dbUrl = s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val xa: doobie.Transactor[IO] = Transactor.fromDriverManager[IO](dbDriver, dbUrl, "", "")
    val flyway = {
      val config = new HikariConfig()
      config.setDriverClassName(dbDriver)
      config.setJdbcUrl(dbUrl)
      Flyway.configure()
        .dataSource(new HikariDataSource(config))
        .locations("classpath:sql")
        .load()
    }
    val reader = new H2Reader(
      schema = Some("PUBLIC"),
      excludes = Some(".*flyway.*"))
    val writer = new ScalaWriter(
      directory = FileUtils.adaptLocalPath("infra/src/main/scala"),
      packageName = "gospeak.infra.services.storage.sql.database",
      identifierStrategy = Writer.IdentifierStrategy.upperCase)
    for {
      _ <- Try(flyway.migrate())
      _ <- Try(Generator.generate(xa, reader, writer).unsafeRunSync())
    } yield ()
  }
}
