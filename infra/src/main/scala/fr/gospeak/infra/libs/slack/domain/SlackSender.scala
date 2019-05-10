package fr.gospeak.infra.libs.slack.domain

sealed trait SlackSender {
  def toOpts: Map[String, String]
}

object SlackSender {

  final case class Bot(username: String, icon: Option[String]) extends SlackSender {
    def toOpts: Map[String, String] = Map(
      "as_user" -> "false",
      "username" -> username,
      "icon_url" -> icon.getOrElse("")
    ).filter(_._2.nonEmpty)
  }

}
