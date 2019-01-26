package fr.gospeak.web

import com.softwaremill.macwire.wire
import fr.gospeak.infra.services.storage.sql.{DbSqlConf, GospeakDbSql, H2}
import fr.gospeak.web.auth.AuthCtrl
import fr.gospeak.web.cfps.CfpCtrl
import fr.gospeak.web.groups.GroupCtrl
import fr.gospeak.web.speakers.SpeakerCtrl
import fr.gospeak.web.user.UserCtrl
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import play.filters.HttpFiltersComponents
import router.Routes

import scala.concurrent.Await
import scala.concurrent.duration.Duration

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

  lazy val dbConf: DbSqlConf = H2("org.h2.Driver", "jdbc:h2:mem:gospeak_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  lazy val db = wire[GospeakDbSql]

  lazy val homeCtrl = wire[HomeCtrl]
  lazy val cfpCtrl = wire[CfpCtrl]
  lazy val groupCtrl = wire[GroupCtrl]
  lazy val speakerCtrl = wire[SpeakerCtrl]
  lazy val authCtrl = wire[AuthCtrl]
  lazy val userCtrl = wire[UserCtrl]
  lazy val userGroupCtrl = wire[user.groups.GroupCtrl]
  lazy val userGroupEventCtrl = wire[user.groups.events.EventCtrl]
  lazy val userGroupProposalCtrl = wire[user.groups.proposals.ProposalCtrl]
  lazy val userGroupSettingsCtrl = wire[user.groups.settings.SettingsCtrl]
  lazy val userTalkCtrl = wire[user.talks.TalkCtrl]
  lazy val userTalkCfpCtrl = wire[user.talks.cfps.CfpCtrl]
  lazy val userTalkProposalCtrl = wire[user.talks.proposals.ProposalCtrl]

  override lazy val router: Router = {
    val prefix = "/"
    wire[Routes]
  }

  def onStart(): Unit = {
    println("Starting application")
    db.dropTables().unsafeRunSync()
    db.createTables().unsafeRunSync()
    db.insertMockData().unsafeRunSync()
  }

  onStart()
}
