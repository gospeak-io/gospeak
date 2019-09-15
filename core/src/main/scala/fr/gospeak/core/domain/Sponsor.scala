package fr.gospeak.core.domain

import java.time.{Instant, LocalDate, ZoneOffset}

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain.{DataClass, IId, Price, UuidIdBuilder}

case class Sponsor(id: Sponsor.Id,
                   group: Group.Id,
                   partner: Partner.Id,
                   pack: SponsorPack.Id,
                   // contact: Option[Contact.Id],
                   start: LocalDate,
                   finish: LocalDate,
                   paid: Option[LocalDate],
                   price: Price,
                   info: Info) {
  def data: Sponsor.Data = Sponsor.Data(this)

  def isCurrent(now: Instant): Boolean =
    start.atStartOfDay().toInstant(ZoneOffset.UTC).isBefore(now) && finish.atStartOfDay().toInstant(ZoneOffset.UTC).isAfter(now)
}

object Sponsor {
  def apply(group: Group.Id, data: Data, info: Info): Sponsor =
    new Sponsor(Id.generate(), group, data.partner, data.pack, data.start, data.finish, data.paid, data.price, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Sponsor.Id", new Id(_))

  final case class Full(sponsor: Sponsor, sponsorPack: SponsorPack, partner: Partner)

  final case class Data(partner: Partner.Id,
                        pack: SponsorPack.Id,
                        start: LocalDate,
                        finish: LocalDate,
                        paid: Option[LocalDate],
                        price: Price)

  object Data {
    def apply(s: Sponsor): Data = new Data(s.partner, s.pack, s.start, s.finish, s.paid, s.price)
  }

}
