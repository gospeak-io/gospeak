package fr.gospeak.web

import com.softwaremill.macwire.wire
import fr.gospeak.web.auth.AuthCtrl
import fr.gospeak.web.cfps.CfpCtrl
import fr.gospeak.web.groups.GroupCtrl
import fr.gospeak.web.speakers.SpeakerCtrl
import fr.gospeak.web.user.UserCtrl
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

  lazy val homeCtrl = wire[HomeCtrl]
  lazy val cfpCtrl = wire[CfpCtrl]
  lazy val groupCtrl = wire[GroupCtrl]
  lazy val speakerCtrl = wire[SpeakerCtrl]
  lazy val authCtrl = wire[AuthCtrl]
  lazy val userCtrl = wire[UserCtrl]
  lazy val userGroupCtrl = wire[user.groups.GroupCtrl]
  lazy val userGroupEventCtrl = wire[user.groups.events.EventCtrl]
  lazy val userGroupProposalCtrl = wire[user.groups.proposals.ProposalCtrl]
  lazy val userTalkCtrl = wire[user.talks.TalkCtrl]
  lazy val userTalkProposalCtrl = wire[user.talks.proposal.ProposalCtrl]

  override lazy val router: Router = {
    val prefix = "/"
    wire[Routes]
  }

  def onStart(): Unit = {
    println("Starting application")
  }

  onStart()
}
