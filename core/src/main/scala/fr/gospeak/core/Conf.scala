package fr.gospeak.core

import fr.gospeak.core.GospeakConf.EventConf
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.libs.scalautils.Crypto.AesSecretKey
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl

final case class ApplicationConf(env: ApplicationConf.Env,
                                 aesKey: AesSecretKey)

object ApplicationConf {

  sealed trait Env {
    def isLocal: Boolean = false

    def isDev: Boolean = false

    def isProd: Boolean = false
  }

  final case object Local extends Env {
    override def isLocal: Boolean = true
  }

  final case object Dev extends Env {
    override def isDev: Boolean = true
  }

  final case object Prod extends Env {
    override def isProd: Boolean = true
  }

}

final case class GospeakConf(event: EventConf) {
  def defaultGroupSettings: Group.Settings = Group.Settings(
    accounts = Group.Settings.Accounts(
      meetup = None,
      slack = None),
    // twitter = None,
    // youtube = None),
    event = Group.Settings.Event(
      description = event.description,
      templates = Map()),
    actions = Map())
}

object GospeakConf {

  final case class EventConf(description: MustacheMarkdownTmpl[TemplateData.EventInfo])

}
