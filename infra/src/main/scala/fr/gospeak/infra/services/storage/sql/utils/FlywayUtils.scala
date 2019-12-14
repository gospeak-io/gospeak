package fr.gospeak.infra.services.storage.sql.utils

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import fr.gospeak.core.services.storage.DatabaseConf
import org.flywaydb.core.Flyway

object FlywayUtils {
  def build(conf: DatabaseConf): Flyway = {
    val config = new HikariConfig()
    conf match {
      case c: DatabaseConf.H2 =>
        config.setDriverClassName("org.h2.Driver")
        config.setJdbcUrl(c.url)
      case c: DatabaseConf.PostgreSQL =>
        config.setDriverClassName("org.postgresql.Driver")
        config.setJdbcUrl(c.url)
        config.setUsername(c.user)
        config.setPassword(c.pass.decode)
    }
    Flyway.configure()
      .dataSource(new HikariDataSource(config))
      .locations("classpath:sql")
      .load()
  }
}
