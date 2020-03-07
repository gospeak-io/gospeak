package gospeak.core.domain.messages

import gospeak.core.domain.utils.{Constants, SocialAccounts}
import gospeak.core.domain.{Partner, Venue}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

final case class MsgVenue(id: Venue.Id)

object MsgVenue {

  final case class Embed(name: Partner.Name,
                         logo: Logo,
                         address: GMapPlace,
                         description: Option[Markdown],
                         links: SocialAccounts)

  object Embed {
    def unknown(id: Venue.Id): Embed = Embed(
      name = Partner.Name("Unknown"),
      logo = Logo(Url.from(Constants.Placeholders.unknownPartner).get),
      address = GMapPlace(
        id = "",
        name = "Unknown",
        streetNo = None,
        street = None,
        postalCode = None,
        locality = None,
        country = "Unknown",
        formatted = "Unknown",
        input = "Unknown",
        geo = Geo(0, 0),
        url = "https://www.google.com/maps",
        website = None,
        phone = None,
        utcOffset = 0),
      description = None,
      links = SocialAccounts.fromUrls())
  }

}
