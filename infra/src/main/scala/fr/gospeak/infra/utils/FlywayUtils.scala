package fr.gospeak.infra.utils

import fr.gospeak.infra.services.storage.sql.{DbSqlConf, H2, PostgreSQL}
import org.flywaydb.core.Flyway

object FlywayUtils {
  def build(conf: DbSqlConf): Flyway = {
    val flyway = new Flyway()
    flyway.setLocations("classpath:sql")
    val (url, user, pass) = conf match {
      case c: H2 => (c.url, "", "")
      case c: PostgreSQL => (c.url, c.user, c.pass.decode)
    }
    flyway.setDataSource(url, user, pass)
    flyway
  }
}
