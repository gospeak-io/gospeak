package gospeak.infra.services.storage.sql.utils

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import gospeak.core.services.storage.DbConf
import org.flywaydb.core.Flyway

object FlywayUtils {
  def build(conf: DbConf): Flyway = {
    val config = new HikariConfig()
    conf match {
      case c: DbConf.H2 =>
        config.setDriverClassName("org.h2.Driver")
        config.setJdbcUrl(c.url)
      case c: DbConf.PostgreSQL =>
        config.setDriverClassName("org.postgresql.Driver")
        config.setJdbcUrl(c.url)
        config.setUsername(c.user)
        config.setPassword(c.pass.decode)
    }
    Flyway.configure()
      .dataSource(new HikariDataSource(config))
      .locations("classpath:migrations")
      .load()
  }
}
