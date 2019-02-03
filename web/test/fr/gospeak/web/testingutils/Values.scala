package fr.gospeak.web.testingutils

import java.util.UUID

import fr.gospeak.infra.services.storage.sql.{GospeakDbSql, H2}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers

object Values {
  val cc: ControllerComponents = Helpers.stubControllerComponents()

  def dbConf = H2("org.h2.Driver", s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")

  def db = new GospeakDbSql(dbConf)
}
