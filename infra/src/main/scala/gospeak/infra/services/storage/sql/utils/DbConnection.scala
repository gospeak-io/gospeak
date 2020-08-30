package gospeak.infra.services.storage.sql.utils

import cats.effect.{ContextShift, IO}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.util.transactor.Transactor
import gospeak.core.services.storage.DbConf
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

object DbConnection {
  def create(conf: DbConf): (doobie.Transactor[IO], Flyway) = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val xa: doobie.Transactor[IO] = conf match {
      case c: DbConf.H2 => Transactor.fromDriverManager[IO](c.driver, c.url, "", "")
      case c: DbConf.PostgreSQL => Transactor.fromDriverManager[IO](c.driver, c.url, c.user, c.pass.decode)
    }

    val config = new HikariConfig()
    conf match {
      case c: DbConf.H2 =>
        config.setDriverClassName(c.driver)
        config.setJdbcUrl(c.url)
      case c: DbConf.PostgreSQL =>
        config.setDriverClassName(c.driver)
        config.setJdbcUrl(c.url)
        config.setUsername(c.user)
        config.setPassword(c.pass.decode)
    }
    val flyway = Flyway.configure()
      .dataSource(new HikariDataSource(config))
      .locations("classpath:migrations")
      .load()

    (xa, flyway)
  }
}
