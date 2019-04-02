package fr.gospeak.infra.utils

import fr.gospeak.infra.services.storage.sql.DatabaseConf
import org.flywaydb.core.Flyway

object FlywayUtils {
  def build(conf: DatabaseConf): Flyway = {
    val flyway = new Flyway()
    flyway.setLocations("classpath:sql")
    val (url, user, pass) = conf match {
      case c: DatabaseConf.H2 => (c.url, "", "")
      case c: DatabaseConf.PostgreSQL => (c.url, c.user, c.pass.decode)
    }
    flyway.setDataSource(url, user, pass)
    flyway
  }
}
