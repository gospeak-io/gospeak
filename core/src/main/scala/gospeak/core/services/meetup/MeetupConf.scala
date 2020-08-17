package gospeak.core.services.meetup

import gospeak.libs.scala.domain.Secret

sealed trait MeetupConf {
  def isEnabled: Boolean
}

object MeetupConf {

  final case class Disabled() extends MeetupConf {
    override def isEnabled: Boolean = false
  }

  final case class Enabled(key: String, secret: Secret) extends MeetupConf {
    override def isEnabled: Boolean = true
  }

}
