package fr.gospeak.web.testingutils

import fr.gospeak.infra.services.GospeakDbInMemory
import fr.gospeak.web.domain.{HeaderInfo, NavLink}
import fr.gospeak.web.routes
import play.api.mvc.ControllerComponents
import play.api.test.Helpers

object Values {
  val cc: ControllerComponents = Helpers.stubControllerComponents()
  val h = HeaderInfo(NavLink("Gospeak", routes.HomeCtrl.index()), Seq(), Seq())
  val db = new GospeakDbInMemory
}
