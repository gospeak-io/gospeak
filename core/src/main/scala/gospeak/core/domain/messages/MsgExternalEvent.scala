package gospeak.core.domain.messages

import java.time.LocalDateTime

import gospeak.core.domain.Event
import gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.libs.scala.domain.{GMapPlace, Tag, TwitterHashtag}

final case class MsgExternalEvent(name: Event.Name,
                                  start: Option[LocalDateTime],
                                  location: Option[GMapPlace],
                                  twitterAccount: Option[TwitterAccount],
                                  twitterHashtag: Option[TwitterHashtag],
                                  tags: List[Tag],
                                  publicLink: String)
