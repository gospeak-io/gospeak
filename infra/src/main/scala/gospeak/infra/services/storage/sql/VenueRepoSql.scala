package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.string._
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.services.storage.VenueRepo
import gospeak.infra.services.storage.sql.VenueRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.{EVENTS, GROUPS, PARTNERS, VENUES}
import gospeak.infra.services.storage.sql.database.tables.VENUES
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Markdown, Page}
import gospeak.libs.sql.dsl._

class VenueRepoSql(protected[sql] val xa: doobie.Transactor[IO],
                   partnerRepo: PartnerRepoSql,
                   contactRepo: ContactRepoSql) extends GenericRepo with VenueRepo {
  override def duplicate(id: Venue.Id)(implicit ctx: OrgaCtx): IO[(Partner, Venue, Option[Contact])] = for {
    venueElt <- selectOneFull(id).run(xa)
    partner <- partnerRepo.create(Partner.Data(
      slug = venueElt.partner.slug,
      name = venueElt.partner.name,
      notes = Markdown(""), // private to group
      description = venueElt.partner.description,
      logo = venueElt.partner.logo,
      social = venueElt.partner.social))
    contact <- venueElt.contact.map { contact =>
      contactRepo.create(Contact.Data(
        partner = partner.id,
        firstName = contact.firstName,
        lastName = contact.lastName,
        email = contact.email,
        notes = Markdown(""))) // private to group
    }.sequence
    venue <- create(partner.id, Venue.Data(
      contact = contact.map(_.id),
      address = venueElt.address,
      notes = Markdown(""), // private to group
      roomSize = venueElt.roomSize,
      refs = Venue.ExtRefs()))
  } yield (partner, venue, contact)

  override def create(partner: Partner.Id, data: Venue.Data)(implicit ctx: OrgaCtx): IO[Venue] = {
    val venue = Venue(ctx.group.id, partner, data, ctx.info)
    insert(venue).run(xa).map(_ => venue)
  }

  override def edit(venue: Venue.Id, data: Venue.Data)(implicit ctx: OrgaCtx): IO[Unit] = update(ctx.group.id, venue)(data, ctx.user.id, ctx.now).run(xa)

  override def remove(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Unit] = delete(ctx.group.id, venue).run(xa)

  override def findFull(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Option[Venue.Full]] = selectOneFull(ctx.group.id, venue).run(xa)

  override def findPublic(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Option[Venue.Public]] = selectOnePublic(ctx.group.id, venue).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Full]] = selectPageFull(params).run(xa)

  override def listPublic(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Public]] = selectPagePublic(params).run(xa)

  override def listCommon(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Common]] = selectPageCommon(params).run(xa)

  override def listAllFull()(implicit ctx: OrgaCtx): IO[List[Venue.Full]] = selectAllFull(ctx.group.id).run(xa)

  override def listAllFull(group: Group.Id): IO[List[Venue.Full]] = selectAllFull(group).run(xa)

  override def listAllFull(group: Group.Id, venues: List[Venue.Id]): IO[List[Venue.Full]] = runNel[Venue.Id, Venue.Full](selectAllFull(group, _), venues)

  override def listAllFull(partner: Partner.Id): IO[List[Venue.Full]] = selectAllFull(partner).run(xa)

  override def listAll(contact: Contact.Id)(implicit ctx: OrgaCtx): IO[List[Venue]] = selectAll(ctx.group.id, contact).run(xa)
}

object VenueRepoSql {
  private val VENUES_SELECT = VENUES.dropFields(_.name.startsWith("address_"))
  private val VENUES_WITH_PARTNER = VENUES_SELECT.joinOn(VENUES.PARTNER_ID)
  private val VENUES_FULL = VENUES_WITH_PARTNER.joinOn(VENUES.CONTACT_ID)

  private def VENUES_PUBLIC(group: Group.Id): Table.JoinTable = VENUES_WITH_PARTNER
    .join(GROUPS).on(PARTNERS.GROUP_ID.is(GROUPS.ID) and GROUPS.ID.isNot(group))
    .join(EVENTS).on(GROUPS.ID.is(EVENTS.GROUP_ID) and EVENTS.VENUE.is(VENUES.ID) and EVENTS.PUBLISHED.notNull)
    .fields(List(AggField("MAX(v.id)", "id"), PARTNERS.SLUG, PARTNERS.NAME, PARTNERS.LOGO, VENUES.ADDRESS, AggField("COALESCE(COUNT(e.id), 0)", "events")))
    .sorts(Table.Sort(PARTNERS.NAME.asc))

  private[sql] def insert(e: Venue): Query.Insert[VENUES] =
  // VENUES.insert.values(e.id, e.partner, e.contact, e.address, e.address.id, e.address.geo.lat, e.address.geo.lng, e.address.locality, e.address.country, e.notes, e.roomSize, e.refs.meetup.map(_.group), e.refs.meetup.map(_.venue), e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    VENUES.insert.values(fr0"${e.id}, ${e.partner}, ${e.contact}, ${e.address}, ${e.address.id}, ${e.address.geo.lat}, ${e.address.geo.lng}, ${e.address.locality}, ${e.address.country}, ${e.notes}, ${e.roomSize}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.venue)}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}")

  private[sql] def update(group: Group.Id, venue: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): Query.Update[VENUES] =
    VENUES.update.set(_.CONTACT_ID, data.contact).set(_.ADDRESS, data.address).set(_.ADDRESS_ID, data.address.id).set(_.ADDRESS_LAT, data.address.geo.lat).set(_.ADDRESS_LNG, data.address.geo.lng).set(_.ADDRESS_LOCALITY, data.address.locality).set(_.ADDRESS_COUNTRY, data.address.country).set(_.NOTES, data.notes).set(_.ROOM_SIZE, data.roomSize).set(_.MEETUPGROUP, data.refs.meetup.map(_.group)).set(_.MEETUPVENUE, data.refs.meetup.map(_.venue)).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(group, venue))

  private[sql] def delete(group: Group.Id, venue: Venue.Id): Query.Delete[VENUES] =
    VENUES.delete.where(where(group, venue))

  private[sql] def selectOneFull(id: Venue.Id): Query.Select.One[Venue.Full] =
    VENUES_FULL.select.where(VENUES.ID is id).one[Venue.Full]

  private[sql] def selectOneFull(group: Group.Id, id: Venue.Id): Query.Select.Optional[Venue.Full] =
    VENUES_FULL.select.where(PARTNERS.GROUP_ID.is(group) and VENUES.ID.is(id)).option[Venue.Full]

  private[sql] def selectOnePublic(group: Group.Id, id: Venue.Id): Query.Select.Optional[Venue.Public] =
    VENUES_PUBLIC(group).select.where(VENUES.ID is id).option[Venue.Public]

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Venue.Full] =
    VENUES_FULL.select.where(PARTNERS.GROUP_ID is ctx.group.id).page[Venue.Full](params, ctx.toDb)

  private[sql] def selectPagePublic(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Venue.Public] =
    VENUES_PUBLIC(ctx.group.id).select.page[Venue.Public](params, ctx.toDb)

  private[sql] def selectPageCommon(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Venue.Common] = {
    val internalVenues = VENUES_WITH_PARTNER.fields(VENUES.ID, PARTNERS.SLUG, PARTNERS.NAME, PARTNERS.LOGO, VENUES.ADDRESS, TableField("0").as("events"), TableField("false").as("public")).select.where(PARTNERS.GROUP_ID is ctx.group.id)
    val publicVenues = VENUES_PUBLIC(ctx.group.id).addFields(TableField("true").as("public")).select
    val commonVenues = internalVenues.union(publicVenues, alias = Some("v"), sorts = List(("name", "name", List("public", "name", "-events"))), search = List("name", "address"))
    commonVenues.select.page[Venue.Common](params, ctx.toDb)
  }

  private[sql] def selectAllFull(group: Group.Id): Query.Select.All[Venue.Full] =
    VENUES_FULL.select.where(PARTNERS.GROUP_ID is group).all[Venue.Full]

  private[sql] def selectAllFull(group: Group.Id, ids: NonEmptyList[Venue.Id]): Query.Select.All[Venue.Full] =
    VENUES_FULL.select.where(PARTNERS.GROUP_ID.is(group) and VENUES.ID.in(ids)).all[Venue.Full]

  private[sql] def selectAllFull(partner: Partner.Id): Query.Select.All[Venue.Full] =
    VENUES_FULL.select.where(VENUES.PARTNER_ID is partner).all[Venue.Full]

  private[sql] def selectAll(group: Group.Id, contact: Contact.Id): Query.Select.All[Venue] =
    VENUES_SELECT.select.where(VENUES.CONTACT_ID is contact).all[Venue]

  private def where(group: Group.Id, id: Venue.Id): VENUES => Cond = v => v.ID.is(VENUES_FULL.select.fields(VENUES.ID).where(PARTNERS.GROUP_ID.is(group) and VENUES.ID.is(id)).all[Venue.Id])
}
