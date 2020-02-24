package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.services.storage.VenueRepo
import gospeak.infra.services.storage.sql.VenueRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils._
import gospeak.infra.services.storage.sql.utils.{DoobieUtils, GenericRepo}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Done, Markdown, Page}

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

  override def listAllFull()(implicit ctx: OrgaCtx): IO[Seq[Venue.Full]] = selectAllFull(ctx.group.id).runList(xa)

  override def listCommon(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Common]] = selectPageCommon(params).run(xa)

  override def listPublic(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Public]] = selectPagePublic(params).run(xa)

  override def findPublic(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Option[Venue.Public]] = selectOnePublic(ctx.group.id, venue).runOption(xa)

  override def listFull(group: Group.Id): IO[Seq[Venue.Full]] = selectAllFull(group).runList(xa)

  override def listAllFull(partner: Partner.Id): IO[Seq[Venue.Full]] = selectAllFull(partner).runList(xa)

  override def listFull(group: Group.Id, venues: Seq[Venue.Id]): IO[Seq[Venue.Full]] = runNel[Venue.Id, Venue.Full](selectAllFull(group, _), venues)

  override def listAllFull(venues: Seq[Venue.Id])(implicit ctx: OrgaCtx): IO[Seq[Venue.Full]] = runNel[Venue.Id, Venue.Full](selectAllFull(ctx.group.id, _), venues)

  override def findFull(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Option[Venue.Full]] = selectOneFull(ctx.group.id, venue).runOption(xa)

  override def listAll(contact: Contact.Id)(implicit ctx: OrgaCtx): IO[Seq[Venue]] = selectAll(ctx.group.id, contact).runList(xa)
}

object VenueRepoSql {
  private val _ = venueIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.venues
  private val tableSelect = table.dropFields(_.name.startsWith("address_"))
  private val tableWithPartner = tableSelect
    .join(Tables.partners, _.partner_id -> _.id).get
  private val tableFull = tableWithPartner
    .joinOpt(Tables.contacts, _.contact_id -> _.id).get

  private def publicTableFull(group: Group.Id): DoobieUtils.Table = tableSelect
    .join(Tables.partners, _.partner_id -> _.id).get
    .join(Tables.groups, fr0"g.id != $group", _.group_id("pa") -> _.id).get.dropFields(_.name.startsWith("location_"))
    .join(Tables.events, fr0"e.venue=v.id AND e.published IS NOT NULL", _.id("g") -> _.group_id).get
    .aggregate("MAX(v.id)", "id")
    .aggregate("COALESCE(COUNT(e.id), 0)", "events")
    .copy(fields = Seq(Field("slug", "pa"), Field("name", "pa"), Field("logo", "pa"), Field("address", "v")))
    .copy(sorts = Sorts(Seq(Field("name", "pa")), Map()))

  private[sql] def insert(e: Venue): Insert[Venue] = {
    val values = fr0"${e.id}, ${e.partner}, ${e.contact}, ${e.address}, ${e.address.id}, ${e.address.geo.lat}, ${e.address.geo.lng}, ${e.address.locality}, ${e.address.country}, ${e.notes}, ${e.roomSize}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.venue)}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Venue](e, _ => values)
  }

  private[sql] def update(group: Group.Id, venue: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"contact_id=${data.contact}, address=${data.address}, address_id=${data.address.id}, address_lat=${data.address.geo.lat}, address_lng=${data.address.geo.lng}, address_locality=${data.address.locality}, address_country=${data.address.country}, notes=${data.notes}, room_size=${data.roomSize}, meetupGroup=${data.refs.meetup.map(_.group)}, meetupVenue=${data.refs.meetup.map(_.venue)}, updated_at=$now, updated_by=$by"
    table.update(fields, where(group, venue))
  }

  private[sql] def delete(group: Group.Id, venue: Venue.Id): Delete =
    table.delete(where(group, venue))

  private[sql] def selectOneFull(id: Venue.Id): Select[Venue.Full] =
    tableFull.select[Venue.Full](fr0"WHERE v.id=$id")

  private[sql] def selectOneFull(group: Group.Id, id: Venue.Id): Select[Venue.Full] =
    tableFull.select[Venue.Full](fr0"WHERE pa.group_id=$group AND v.id=$id")

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): SelectPage[Venue.Full, OrgaCtx] =
    tableFull.selectPage[Venue.Full, OrgaCtx](params, fr0"WHERE pa.group_id=${ctx.group.id}")

  private[sql] def selectAllFull(group: Group.Id): Select[Venue.Full] =
    tableFull.select[Venue.Full](fr0"WHERE pa.group_id=$group")

  private[sql] def selectAllFull(partner: Partner.Id): Select[Venue.Full] =
    tableFull.select[Venue.Full](fr0"WHERE v.partner_id=$partner")

  private[sql] def selectAllFull(group: Group.Id, ids: NonEmptyList[Venue.Id]): Select[Venue.Full] =
    tableFull.select[Venue.Full](fr0"WHERE pa.group_id=$group AND " ++ Fragments.in(fr"v.id", ids))

  private[sql] def selectAll(group: Group.Id, contact: Contact.Id): Select[Venue] =
    tableSelect.select[Venue](fr0"WHERE v.contact_id=$contact")

  private[sql] def selectPagePublic(params: Page.Params)(implicit ctx: OrgaCtx): SelectPage[Venue.Public, OrgaCtx] =
    publicTableFull(ctx.group.id).selectPage[Venue.Public, OrgaCtx](params)

  private[sql] def selectOnePublic(group: Group.Id, id: Venue.Id): Select[Venue.Public] =
    publicTableFull(group).select[Venue.Public](fr0"WHERE v.id=$id")

  private[sql] def selectPageCommon(params: Page.Params)(implicit ctx: OrgaCtx): SelectPage[Venue.Common, OrgaCtx] = {
    val g = tableWithPartner.select[Venue.Full](
      fields = Seq(Field("false", "", "public"), Field("slug", "pa"), Field("name", "pa"), Field("logo", "pa"), Field("address", "v"), Field("id", "v"), Field("0", "", "events")),
      where = fr0"WHERE pa.group_id=${ctx.group.id}",
      sort = Seq())
    val p = publicTableFull(ctx.group.id).select[Venue.Public](
      fields = Seq(Field("true", "", "public")) ++ publicTableFull(ctx.group.id).fields,
      sort = Seq())

    SelectPage[Venue.Common, OrgaCtx](
      table = fr0"((" ++ g.fr ++ fr0") UNION (" ++ p.fr ++ fr0")) v",
      prefix = "v",
      fields = Seq("id", "slug", "name", "logo", "address", "events", "public").map(Field(_, "v")),
      aggFields = Seq(),
      customFields = Seq(),
      whereOpt = None,
      havingOpt = None,
      params = params,
      sorts = Sorts(Seq(Field("public", "v"), Field("name", "v"), Field("-events", "v")), Map()),
      searchFields = Seq(Field("name", "v"), Field("address", "v")),
      filters = Seq(),
      ctx = ctx)
  }

  private def where(group: Group.Id, id: Venue.Id): Fragment =
    fr0"WHERE v.id=(" ++ tableFull.select(Seq(Field("id", "v")), fr0"WHERE pa.group_id=$group AND v.id=$id", Seq()).fr ++ fr0")"
}
