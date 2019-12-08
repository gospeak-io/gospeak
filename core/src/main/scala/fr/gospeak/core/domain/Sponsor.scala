package fr.gospeak.core.domain

import java.time.{Instant, LocalDate}

import fr.gospeak.core.domain.utils.{Constants, Info}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{DataClass, IId, Price, UuidIdBuilder}

case class Sponsor(id: Sponsor.Id,
                   group: Group.Id,
                   partner: Partner.Id,
                   pack: SponsorPack.Id,
                   contact: Option[Contact.Id],
                   start: LocalDate,
                   finish: LocalDate,
                   paid: Option[LocalDate],
                   price: Price,
                   info: Info) {
  def data: Sponsor.Data = Sponsor.Data(this)

  def isCurrent(now: Instant): Boolean =
    start.atStartOfDay().toInstant(Constants.defaultZoneId).isBefore(now) &&
      finish.atStartOfDay().toInstant(Constants.defaultZoneId).isAfter(now)
}

object Sponsor {
  def apply(group: Group.Id, data: Data, info: Info): Sponsor =
    new Sponsor(Id.generate(), group, data.partner, data.pack, data.contact, data.start, data.finish, data.paid, data.price, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Sponsor.Id", new Id(_))

  final case class Full(sponsor: Sponsor, pack: SponsorPack, partner: Partner, contact: Option[Contact]) {
    def isCurrent(now: Instant): Boolean = sponsor.isCurrent(now)

    def id: Id = sponsor.id

    def start: LocalDate = sponsor.start

    def finish: LocalDate = sponsor.finish

    def price: Price = sponsor.price

    def paid: Option[LocalDate] = sponsor.paid
  }

  final case class Data(partner: Partner.Id,
                        pack: SponsorPack.Id,
                        contact: Option[Contact.Id],
                        start: LocalDate,
                        finish: LocalDate,
                        paid: Option[LocalDate],
                        price: Price)

  object Data {
    def apply(s: Sponsor): Data = new Data(s.partner, s.pack, s.contact, s.start, s.finish, s.paid, s.price)
  }

}
