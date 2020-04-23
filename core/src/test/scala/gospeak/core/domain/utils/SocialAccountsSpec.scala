package gospeak.core.domain.utils

import gospeak.core.domain.utils.SocialAccounts.SocialAccount.MeetupAccount
import gospeak.core.testingutils.BaseSpec
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Url

class SocialAccountsSpec extends BaseSpec {
  describe("SocialAccounts") {
    describe("SocialAccount") {
      it("should ignore query params when build handle") {
        val url = "https://www.meetup.com/fr-FR/members/68834382/?op=&memberId=68834382"
        MeetupAccount(Url.Meetup.from(url).get).handle shouldBe "68834382"
      }
    }
  }
}
