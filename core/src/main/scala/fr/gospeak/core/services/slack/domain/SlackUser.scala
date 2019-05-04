package fr.gospeak.core.services.slack.domain

sealed trait SlackUser

object SlackUser {

  final case class Bot(name: String, avatar: Option[String]) extends SlackUser

}
