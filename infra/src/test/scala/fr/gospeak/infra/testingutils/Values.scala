package fr.gospeak.infra.testingutils

import java.util.UUID

import fr.gospeak.infra.services.storage.sql.{DatabaseConf, GospeakDbSql}

object Values {
  def dbConf = DatabaseConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")

  def db = new GospeakDbSql(dbConf)
}
