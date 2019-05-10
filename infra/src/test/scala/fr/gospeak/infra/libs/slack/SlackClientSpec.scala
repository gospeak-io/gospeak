package fr.gospeak.infra.libs.slack

import fr.gospeak.infra.libs.slack.domain.SlackToken
import org.scalatest.{FunSpec, Matchers}

class SlackClientSpec extends FunSpec with Matchers {
  private val token = SlackToken("...")
  private val client = new SlackClient

  ignore("SlackClient") {
    it("should get info") {
      val info = client.info(token).unsafeRunSync()
      println(s"info: $info")
    }
    it("should list channels") {
      val channels = client.listChannels(token).unsafeRunSync()
      println(s"channels: $channels")
    }
    it("should list users") {
      val users = client.listUsers(token).unsafeRunSync()
      println(s"users: $users")
      users.foreach(println)
    }
  }
}
