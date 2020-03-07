package gospeak.core.domain.messages

import cats.data.NonEmptyList
import gospeak.core.domain.Group
import gospeak.core.domain.utils.SocialAccounts
import gospeak.libs.scala.domain._

final case class MsgGroup(slug: Group.Slug,
                          name: Group.Name,
                          logo: Option[Logo],
                          banner: Option[Banner],
                          contact: Option[EmailAddress],
                          website: Option[Url],
                          description: Markdown,
                          links: SocialAccounts,
                          tags: Seq[Tag],
                          orgas: NonEmptyList[MsgUser.Embed],
                          sponsors: Seq[MsgSponsor.Embed],
                          publicLink: String,
                          orgaLink: String)
