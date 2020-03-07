package gospeak.core.domain.messages

import gospeak.core.domain.User
import gospeak.core.domain.utils.{Constants, SocialAccounts}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Avatar, Url}

final case class MsgUser(slug: User.Slug)

object MsgUser {

  final case class Embed(slug: User.Slug,
                         name: User.Name,
                         avatar: Avatar,
                         title: Option[String],
                         company: Option[String],
                         website: Option[Url],
                         links: SocialAccounts,
                         public: Boolean)

  object Embed {
    def unknown(id: User.Id): Embed = Embed(
      slug = User.Slug.from("unknown").get,
      name = User.Name("Unknown"),
      avatar = Avatar(Url.from(Constants.Placeholders.unknownUser).get),
      title = None,
      company = None,
      website = None,
      links = SocialAccounts.fromUrls(),
      public = true)
  }

}
