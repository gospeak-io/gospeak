package gospeak.core.domain.messages

import gospeak.core.domain.Partner
import gospeak.libs.scala.domain.Logo

final case class MsgPartner(id: Partner.Id,
                            name: Partner.Name,
                            slug: Partner.Slug,
                            logo: Logo)

object MsgPartner {

  final case class Embed(name: Partner.Name,
                         slug: Partner.Slug,
                         logo: Logo)

}
