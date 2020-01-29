package gospeak.core.services.twitter

import java.time.LocalDateTime

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.core.domain.{ExternalCfp, User}
import gospeak.core.testingutils.Generators._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{GMapPlace, Tag, TwitterHashtag, Url}
import org.scalatest.{FunSpec, Matchers}

class TwittsSpec extends FunSpec with Matchers with RandomDataGenerator {
  private val ldt = LocalDateTime.of(2020, 2, 10, 19, 0)
  protected val user: User = random[User]
  protected val cfpExt: ExternalCfp = random[ExternalCfp]
  protected val place: GMapPlace = random[GMapPlace]
  private val url = "https://gospeak.io/cfps/"

  describe("Twitts") {
    it("should format externalCfpCreated when cfp is mostly empty") {
      val cfp = cfpExt.copy(
        name = ExternalCfp.Name("Devoxx"),
        tags = Seq(),
        close = None,
        event = cfpExt.event.copy(start = None, location = None, twitterAccount = None, twitterHashtag = None))
      val usr = user.copy(firstName = "Loïc", lastName = "Knuchel", social = user.social.copy(twitter = None))
      Tweets.externalCfpCreated(cfp, url, usr) shouldBe
        s"""!!Speaker announcement!!
           |Devoxx is happening
           |Submit your proposals at $url
           |#speaking
           |Thanks Loïc Knuchel for the post!
           |""".stripMargin.trim
    }
    it("should format externalCfpCreated when cfp is full") {
      val cfp = cfpExt.copy(
        tags = Seq(Tag("tech"), Tag("big data")),
        close = Some(ldt),
        event = cfpExt.event.copy(
          start = Some(ldt),
          location = Some(place.copy(country = "France", locality = Some("Paris"))),
          twitterAccount = Some(TwitterAccount(Url.from("https://twitter.com/devoxx").get)),
          twitterHashtag = Some(TwitterHashtag.from("#Devoxx").get)))
      val usr = user.copy(social = user.social.copy(twitter = Some(TwitterAccount(Url.from("https://twitter.com/jack").get))))
      Tweets.externalCfpCreated(cfp, url, usr) shouldBe
        s"""!!Speaker announcement!!
           |@devoxx is happening on February 10 in Paris, France
           |Submit your proposals before February 10 at $url
           |#speaking #Devoxx #tech #bigdata
           |Thanks @jack for the post!
           |""".stripMargin.trim
    }
  }
}
