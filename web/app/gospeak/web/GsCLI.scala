package gospeak.web

import java.util.UUID

import gospeak.core.services.storage.DbConf
import gospeak.infra.services.storage.sql.utils.DbConnection
import gospeak.libs.scala.FileUtils
import gospeak.libs.sql.generator.Generator
import gospeak.libs.sql.generator.reader.H2Reader
import gospeak.libs.sql.generator.writer.ScalaWriter.DatabaseConfig
import gospeak.libs.sql.generator.writer.{ScalaWriter, Writer}

import scala.util.Try

/**
 * A CLI to perform common tasks
 */
object GsCLI {
  def main(args: Array[String]): Unit = {
    // TODO
  }

  def generateSchemas(): Try[Unit] = {
    val dbConf = DbConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
    val (xa, flyway) = DbConnection.create(dbConf)
    val reader = new H2Reader(
      schema = Some("PUBLIC"),
      excludes = Some(".*flyway.*"))
    val writer = new ScalaWriter(
      directory = FileUtils.adaptLocalPath("infra/src/main/scala"),
      packageName = "gospeak.infra.services.storage.sql.database",
      identifierStrategy = Writer.IdentifierStrategy.upperCase,
      config = DatabaseConfig())
    for {
      _ <- Try(flyway.migrate())
      _ <- Try(Generator.generate(xa, reader, writer).unsafeRunSync())
    } yield ()
  }
}
