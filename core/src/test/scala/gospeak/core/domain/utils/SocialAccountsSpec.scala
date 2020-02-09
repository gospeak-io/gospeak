package gospeak.core.domain.utils

import gospeak.core.domain.utils.SocialAccounts.SocialAccount.MeetupAccount
import gospeak.libs.scala.domain.Url
import org.scalatest.{FunSpec, Matchers}
import gospeak.libs.scala.Extensions._

class SocialAccountsSpec extends FunSpec with Matchers {
  describe("SocialAccounts") {
    describe("SocialAccount") {
      it("should ignore query params when build handle") {
        val url = "https://www.meetup.com/fr-FR/members/68834382/?op=&memberId=68834382"
        MeetupAccount(Url.from(url).get).handle shouldBe "68834382"
      }
    }
  }
}
