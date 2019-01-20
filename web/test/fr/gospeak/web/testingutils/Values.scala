package fr.gospeak.web.testingutils

import fr.gospeak.infra.services.storage.sql.{GospeakDbSql, H2}
import fr.gospeak.web.domain.{HeaderInfo, NavLink}
import fr.gospeak.web.routes
import play.api.mvc.ControllerComponents
import play.api.test.Helpers

object Values {
  val cc: ControllerComponents = Helpers.stubControllerComponents()
  val h = HeaderInfo(NavLink("Gospeak", routes.HomeCtrl.index()), Seq(), Seq())
  val dbConf = H2("org.h2.Driver", "jdbc:h2:mem:gospeak_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  val db = new GospeakDbSql(dbConf)
}
