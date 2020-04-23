package gospeak.core.services.twitter

import java.time.LocalDateTime

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import gospeak.core.domain.messages.{Message, MsgExternalCfp, MsgExternalEvent, MsgUser}
import gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.core.domain.utils.{Constants, SocialAccounts}
import gospeak.core.domain.{Event, User}
import gospeak.core.testingutils.BaseSpec
import gospeak.core.testingutils.Generators._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

class TwittsSpec extends BaseSpec with RandomDataGenerator {
  protected val place: GMapPlace = random[GMapPlace]
  private val event = MsgExternalEvent(
    name = Event.Name("Devoxx"),
    start = None,
    location = None,
    twitterAccount = None,
    twitterHashtag = None,
    tags = Seq(),
    publicLink = "https://gospeak.io/events/ext/1a887b22-ebcf-41eb-a3ab-fd7ca1a53689")
  private val cfp = MsgExternalCfp(
    begin = None,
    close = None,
    publicLink = "https://gospeak.io/cfps/ext/f02d5dc9-a93b-4861-a8ff-66962187d850")
  private val user = MsgUser.Embed(
    slug = User.Slug.from("loicknuchel").get,
    name = User.Name("Loïc Knuchel"),
    avatar = Avatar(Url.from("https://avatars0.githubusercontent.com/u/653009").get),
    title = None,
    company = None,
    website = None,
    links = SocialAccounts.fromStrings(twitter = Some("https://twitter.com/loicknuchel")).get,
    public = true)
  private val ldt = LocalDateTime.of(2020, 2, 10, 19, 0)
  private val now = ldt.toInstant(Constants.defaultZoneId)

  describe("Twitts") {
    it("should format externalCfpCreated when cfp is mostly empty") {
      val msg = Message.ExternalCfpCreated(
        event = event.copy(
          start = None,
          location = None,
          twitterAccount = None,
          twitterHashtag = None,
          tags = Seq()),
        cfp = cfp.copy(
          close = None),
        by = user.copy(
          links = SocialAccounts.fromUrls()),
        at = now)
      Tweets.externalCfpCreated(msg) shouldBe
        s"""!!Speaker announcement!!
           |Devoxx is happening
           |Submit your proposals at https://gospeak.io/cfps/ext/f02d5dc9-a93b-4861-a8ff-66962187d850
           |#speaking
           |Thanks Loïc Knuchel for the post!
           |""".stripMargin.trim
    }
    it("should format externalCfpCreated when cfp is full") {
      val msg = Message.ExternalCfpCreated(
        event = event.copy(
          start = Some(ldt),
          location = Some(place.copy(country = "France", locality = Some("Paris"))),
          twitterAccount = Some(TwitterAccount(Url.Twitter.from("https://twitter.com/devoxx").get)),
          twitterHashtag = Some(TwitterHashtag.from("#Devoxx").get),
          tags = Seq(Tag("tech"), Tag("big data"))),
        cfp = cfp.copy(
          close = Some(ldt)),
        by = user.copy(
          links = SocialAccounts.fromUrls(twitter = Some(Url.Twitter.from("https://twitter.com/jack").get))),
        at = now)
      Tweets.externalCfpCreated(msg) shouldBe
        s"""!!Speaker announcement!!
           |@devoxx is happening on February 10 in Paris, France
           |Submit your proposals before February 10 at https://gospeak.io/cfps/ext/f02d5dc9-a93b-4861-a8ff-66962187d850
           |#speaking #Devoxx #tech #bigdata
           |Thanks @jack for the post!
           |""".stripMargin.trim
    }
  }
}
