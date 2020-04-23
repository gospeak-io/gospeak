package gospeak.libs.slack

import gospeak.libs.slack.domain.{SlackSender, SlackToken}
import gospeak.libs.testingutils.BaseSpec

class SlackClientSpec extends BaseSpec {
  private val token = SlackToken("...")
  private val sender = SlackSender.Bot("Gospeak test", None)
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
      users.map(_.foreach(println))
    }
    /* it("should create channel") {
      val channel = client.createChannel(token, SlackChannel.Name("test1")).unsafeRunSync()
      println(s"channel: $channel")
    }
    it("should post message") {
      val msg = client.postMessage(token, sender, SlackChannel.Name("test1"), SlackContent.Markdown("test")).unsafeRunSync()
      println(s"msg: $msg")
    } */
  }
}
