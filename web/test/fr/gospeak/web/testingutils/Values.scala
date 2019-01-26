package fr.gospeak.web.testingutils

import java.util.UUID

import fr.gospeak.infra.services.storage.sql.{GospeakDbSql, H2}
import fr.gospeak.web.auth.AuthService
import fr.gospeak.web.domain.{HeaderInfo, NavLink}
import fr.gospeak.web.routes
import play.api.mvc.ControllerComponents
import play.api.test.Helpers

object Values {
  val cc: ControllerComponents = Helpers.stubControllerComponents()
  val h = HeaderInfo(NavLink("Gospeak", routes.HomeCtrl.index()), Seq(), Seq())

  def dbConf = H2("org.h2.Driver", s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")

  def db = new GospeakDbSql(dbConf)
}
