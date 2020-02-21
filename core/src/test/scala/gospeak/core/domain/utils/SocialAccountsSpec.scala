package gospeak.core.domain.utils

import gospeak.core.domain.utils.SocialAccounts.SocialAccount.MeetupAccount
import gospeak.libs.scala.domain.Url
import gospeak.libs.scala.Extensions._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SocialAccountsSpec extends AnyFunSpec with Matchers {
  describe("SocialAccounts") {
    describe("SocialAccount") {
      it("should ignore query params when build handle") {
        val url = "https://www.meetup.com/fr-FR/members/68834382/?op=&memberId=68834382"
        MeetupAccount(Url.from(url).get).handle shouldBe "68834382"
      }
    }
  }
}
