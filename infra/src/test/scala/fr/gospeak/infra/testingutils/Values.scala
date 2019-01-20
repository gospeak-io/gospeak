package fr.gospeak.infra.testingutils

import fr.gospeak.infra.services.storage.sql.{GospeakDbSql, H2}

object Values {
  val dbConf = H2("org.h2.Driver", "jdbc:h2:mem:gospeak_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  val db = new GospeakDbSql(dbConf)
}
