package gospeak.core.domain.messages

import gospeak.core.domain.{Partner, SponsorPack}
import gospeak.libs.scala.domain.Logo

final case class MsgSponsor(name: Partner.Name,
                            logo: Logo,
                            pack: SponsorPack.Name)

object MsgSponsor {

  final case class Embed(name: Partner.Name,
                         logo: Logo,
                         pack: SponsorPack.Name)

}
