package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.{BasicCtx, OrgaCtx}
import gospeak.core.services.storage.VenueRepo
import gospeak.infra.services.storage.sql.VenueRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Done, Markdown, Page}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}

class VenueRepoSql(protected[sql] val xa: doobie.Transactor[IO],
                   partnerRepo: PartnerRepoSql,
                   contactRepo: ContactRepoSql) extends GenericRepo with VenueRepo {
  override def duplicate(id: Venue.Id)(implicit ctx: OrgaCtx): IO[(Partner, Venue, Option[Contact])] = for {
    venueElt <- selectOneFull(id).runUnique(xa)
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

  override def create(partner: Partner.Id, data: Venue.Data)(implicit ctx: OrgaCtx): IO[Venue] = insert(Venue(ctx.group.id, partner, data, ctx.info)).run(xa)

  override def edit(venue: Venue.Id, data: Venue.Data)(implicit ctx: OrgaCtx): IO[Done] = update(ctx.group.id, venue)(data, ctx.user.id, ctx.now).run(xa)

  override def remove(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Done] = delete(ctx.group.id, venue).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Full]] = selectPageFull(params).run(xa)

  override def listAllFull()(implicit ctx: OrgaCtx): IO[List[Venue.Full]] = selectAllFull(ctx.group.id).runList(xa)

  override def listCommon(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Common]] = selectPageCommon(params).run(xa)

  override def listPublic(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Public]] = selectPagePublic(params).run(xa)

  override def findPublic(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Option[Venue.Public]] = selectOnePublic(ctx.group.id, venue).runOption(xa)

  override def listFull(group: Group.Id): IO[List[Venue.Full]] = selectAllFull(group).runList(xa)

  override def listAllFull(partner: Partner.Id): IO[List[Venue.Full]] = selectAllFull(partner).runList(xa)

  override def listFull(group: Group.Id, venues: List[Venue.Id]): IO[List[Venue.Full]] = runNel[Venue.Id, Venue.Full](selectAllFull(group, _), venues)

  override def listAllFull(group: Group.Id, venues: List[Venue.Id]): IO[List[Venue.Full]] = runNel[Venue.Id, Venue.Full](selectAllFull(group, _), venues)

  override def findFull(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Option[Venue.Full]] = selectOneFull(ctx.group.id, venue).runOption(xa)

  override def listAll(contact: Contact.Id)(implicit ctx: OrgaCtx): IO[List[Venue]] = selectAll(ctx.group.id, contact).runList(xa)
}

object VenueRepoSql {
  private val _ = venueIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.venues
  private val tableSelect = table.dropFields(_.name.startsWith("address_"))
  private val tableWithPartner = tableSelect
    .join(Tables.partners, _.partner_id -> _.id).get
  private val tableFull = tableWithPartner
    .joinOpt(Tables.contacts, _.contact_id -> _.id).get

  private def publicTableFull(group: Group.Id): Table = tableSelect
    .join(Tables.partners, _.partner_id -> _.id).get
    .join(Tables.groups, fr0"g.id != $group", _.group_id("pa") -> _.id).get.dropFields(_.name.startsWith("location_"))
    .join(Tables.events, fr0"e.venue=v.id AND e.published IS NOT NULL", _.id("g") -> _.group_id).get
    .aggregate("MAX(v.id)", "id")
    .aggregate("COALESCE(COUNT(e.id), 0)", "events")
    .copy(fields = List(Field("slug", "pa"), Field("name", "pa"), Field("logo", "pa"), Field("address", "v")))
    .setSorts(Table.Sort("name", "pa"))

  private[sql] def insert(e: Venue): Query.Insert[Venue] = {
    val values = fr0"${e.id}, ${e.partner}, ${e.contact}, ${e.address}, ${e.address.id}, ${e.address.geo.lat}, ${e.address.geo.lng}, ${e.address.locality}, ${e.address.country}, ${e.notes}, ${e.roomSize}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.venue)}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Venue](e, _ => values)
  }

  private[sql] def update(group: Group.Id, venue: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"contact_id=${data.contact}, address=${data.address}, address_id=${data.address.id}, address_lat=${data.address.geo.lat}, address_lng=${data.address.geo.lng}, address_locality=${data.address.locality}, address_country=${data.address.country}, notes=${data.notes}, room_size=${data.roomSize}, meetupGroup=${data.refs.meetup.map(_.group)}, meetupVenue=${data.refs.meetup.map(_.venue)}, updated_at=$now, updated_by=$by"
    table.update(fields).where(where(group, venue))
  }

  private[sql] def delete(group: Group.Id, venue: Venue.Id): Query.Delete =
    table.delete.where(where(group, venue))

  private[sql] def selectOneFull(id: Venue.Id): Query.Select[Venue.Full] =
    tableFull.select[Venue.Full].where(fr0"v.id=$id")

  private[sql] def selectOneFull(group: Group.Id, id: Venue.Id): Query.Select[Venue.Full] =
    tableFull.select[Venue.Full].where(fr0"pa.group_id=$group AND v.id=$id")

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Venue.Full] =
    tableFull.selectPage[Venue.Full](params, adapt(ctx)).where(fr0"pa.group_id=${ctx.group.id}")

  private[sql] def selectAllFull(group: Group.Id): Query.Select[Venue.Full] =
    tableFull.select[Venue.Full].where(fr0"pa.group_id=$group")

  private[sql] def selectAllFull(partner: Partner.Id): Query.Select[Venue.Full] =
    tableFull.select[Venue.Full].where(fr0"v.partner_id=$partner")

  private[sql] def selectAllFull(group: Group.Id, ids: NonEmptyList[Venue.Id]): Query.Select[Venue.Full] =
    tableFull.select[Venue.Full].where(fr0"pa.group_id=$group AND " ++ Fragments.in(fr"v.id", ids))

  private[sql] def selectAll(group: Group.Id, contact: Contact.Id): Query.Select[Venue] =
    tableSelect.select[Venue].where(fr0"v.contact_id=$contact")

  private[sql] def selectPagePublic(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Venue.Public] =
    publicTableFull(ctx.group.id).selectPage[Venue.Public](params, adapt(ctx))

  private[sql] def selectOnePublic(group: Group.Id, id: Venue.Id): Query.Select[Venue.Public] =
    publicTableFull(group).select[Venue.Public].where(fr0"v.id=$id")

  private[sql] def selectPageCommon(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Venue.Common] = {
    val g = tableWithPartner.select[Venue.Full]
      .fields(Field("false", "", "public"), Field("slug", "pa"), Field("name", "pa"), Field("logo", "pa"), Field("address", "v"), Field("id", "v"), Field("0", "", "events"))
      .where(fr0"pa.group_id=${ctx.group.id}")
    val p = publicTableFull(ctx.group.id).select[Venue.Public].fields(List(Field("true", "", "public")) ++ publicTableFull(ctx.group.id).fields)

    Query.SelectPage[Venue.Common](
      table = fr0"((" ++ g.fr ++ fr0") UNION (" ++ p.fr ++ fr0")) v",
      prefix = "v",
      fields = List("id", "slug", "name", "logo", "address", "events", "public").map(Field(_, "v")),
      aggFields = List(),
      customFields = List(),
      whereOpt = None,
      havingOpt = None,
      params = params,
      sorts = Table.Sorts("name", Field("public", "v"), Field("name", "v"), Field("-events", "v")),
      searchFields = List(Field("name", "v"), Field("address", "v")),
      filters = List(),
      ctx = adapt(ctx))
  }

  private def where(group: Group.Id, id: Venue.Id): Fragment =
    fr0"v.id=(" ++ tableFull.select[Venue.Id].fields(Field("id", "v")).where(fr0"pa.group_id=$group AND v.id=$id").fr ++ fr0")"

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
