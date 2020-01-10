package fr.gospeak.core

import fr.gospeak.core.GospeakConf.EventConf
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.libs.scalautils.Crypto.AesSecretKey
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain.{EnumBuilder, StringEnum}

final case class ApplicationConf(env: ApplicationConf.Env,
                                 baseUrl: String,
                                 aesKey: AesSecretKey)

object ApplicationConf {

  sealed trait Env extends StringEnum {
    def value: String = toString

    def isLocal: Boolean = false

    def isDev: Boolean = false

    def isStaging: Boolean = false

    def isProd: Boolean = false
  }

  object Env extends EnumBuilder[Env]("ApplicationConf.Env") {

    final case object Local extends Env {
      override def isLocal: Boolean = true
    }

    final case object Dev extends Env {
      override def isDev: Boolean = true
    }

    final case object Staging extends Env {
      override def isStaging: Boolean = true
    }

    final case object Prod extends Env {
      override def isProd: Boolean = true
    }

    val all: Seq[Env] = Seq(Local, Dev, Staging, Prod)
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
