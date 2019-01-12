package fr.gospeak.web

import com.softwaremill.macwire.wire
import fr.gospeak.web.controllers._
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import play.filters.HttpFiltersComponents
import router.Routes

class GospeakApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    new GospeakComponents(context).application
  }
}

class GospeakComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with _root_.controllers.AssetsComponents {

  lazy val homeCtrl: HomeCtrl = wire[HomeCtrl]
  lazy val authedHomeCtrl: authed.HomeCtrl = wire[authed.HomeCtrl]
  lazy val authedGroupCtrl: authed.GroupCtrl = wire[authed.GroupCtrl]

  override lazy val router: Router = {
    val prefix = "/"
    wire[Routes]
  }

  def onStart(): Unit = {
    println("Starting application")
  }

  onStart()
}
