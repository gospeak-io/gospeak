package gospeak.core

import cats.data.NonEmptyList
import gospeak.core.GsConf.{EventConf, ProposalConf}
import gospeak.core.domain.Group
import gospeak.core.domain.messages.Message
import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.domain.{EmailAddress, EnumBuilder, Mustache, StringEnum}

final case class ApplicationConf(env: ApplicationConf.Env,
                                 baseUrl: String,
                                 aesKey: AesSecretKey,
                                 admins: NonEmptyList[EmailAddress])

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

final case class GsConf(event: EventConf, proposal: ProposalConf) {
  def defaultGroupSettings: Group.Settings = Group.Settings(
    accounts = Group.Settings.Accounts(
      meetup = None,
      slack = None),
    // twitter = None,
    // youtube = None),
    event = Group.Settings.Event(
      description = event.description,
      templates = Map()),
    proposal = Group.Settings.Proposal(
      tweet = proposal.tweet),
    actions = Map())
}

object GsConf {

  final case class EventConf(description: Mustache.Markdown[Message.EventInfo])

  final case class ProposalConf(tweet: Mustache.Text[Message.ProposalInfo])

}
