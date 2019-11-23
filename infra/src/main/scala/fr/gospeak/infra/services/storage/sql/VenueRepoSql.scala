package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Contact, Group, Partner, User, Venue}
import fr.gospeak.core.services.storage.VenueRepo
import fr.gospeak.infra.services.storage.sql.VenueRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Delete, Field, Insert, Select, SelectPage, Update}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Done, Page}

class VenueRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with VenueRepo {
  override def create(group: Group.Id, data: Venue.Data, by: User.Id, now: Instant): IO[Venue] = insert(Venue(group, data, Info(by, now))).run(xa)

  override def edit(group: Group.Id, venue: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): IO[Done] = update(group, venue)(data, by, now).run(xa)

  override def remove(group: Group.Id, venue: Venue.Id)(by: User.Id, now: Instant): IO[Done] = delete(group, venue).run(xa)

  override def listFull(group: Group.Id, params: Page.Params): IO[Page[Venue.Full]] = selectPageFull(group, params).run(xa)

  override def listFull(group: Group.Id): IO[Seq[Venue.Full]] = selectAllFull(group).runList(xa)

  override def listFull(partner: Partner.Id): IO[Seq[Venue.Full]] = selectAllFull(partner).runList(xa)

  override def listFull(group: Group.Id, venues: Seq[Venue.Id]): IO[Seq[Venue.Full]] = runNel[Venue.Id, Venue.Full](selectAllFull(group, _), venues)

  override def findFull(group: Group.Id, venue: Venue.Id): IO[Option[Venue.Full]] = selectOneFull(group, venue).runOption(xa)

  override def listAll(group: Group.Id, contact: Contact.Id): IO[Seq[Venue]] = selectAll(group, contact).runList(xa)
}

object VenueRepoSql {
  private val _ = venueIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.venues
  private val tableSelect = table.dropFields(_.name.startsWith("address_"))
  private val tableFull = tableSelect
    .join(Tables.partners, _.partner_id -> _.id).get
    .joinOpt(Tables.contacts, _.contact_id -> _.id).get

  private[sql] def insert(e: Venue): Insert[Venue] = {
    val values = fr0"${e.id}, ${e.partner}, ${e.contact}, ${e.address}, ${e.address.geo.lat}, ${e.address.geo.lng}, ${e.address.country}, ${e.description}, ${e.roomSize}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.venue)}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Venue](e, _ => values)
  }

  private[sql] def update(group: Group.Id, venue: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"contact_id=${data.contact}, address=${data.address}, address_lat=${data.address.geo.lat}, address_lng=${data.address.geo.lng}, address_country=${data.address.country}, description=${data.description}, room_size=${data.roomSize}, meetupGroup=${data.refs.meetup.map(_.group)}, meetupVenue=${data.refs.meetup.map(_.venue)}, updated_at=$now, updated_by=$by"
    table.update(fields, where(group, venue))
  }

  private[sql] def delete(group: Group.Id, venue: Venue.Id): Delete =
    table.delete(where(group, venue))

  private[sql] def selectOneFull(group: Group.Id, id: Venue.Id): Select[Venue.Full] =
    tableFull.select[Venue.Full](fr0"WHERE pa.group_id=$group AND v.id=$id")

  private[sql] def selectPageFull(group: Group.Id, params: Page.Params): SelectPage[Venue.Full] =
    tableFull.selectPage[Venue.Full](params, fr0"WHERE pa.group_id=$group")

  private[sql] def selectAllFull(group: Group.Id): Select[Venue.Full] =
    tableFull.select[Venue.Full](fr0"WHERE pa.group_id=$group")

  private[sql] def selectAllFull(partner: Partner.Id): Select[Venue.Full] =
    tableFull.select[Venue.Full](fr0"WHERE v.partner_id=$partner")

  private[sql] def selectAllFull(group: Group.Id, ids: NonEmptyList[Venue.Id]): Select[Venue.Full] =
    tableFull.select[Venue.Full](fr0"WHERE pa.group_id=$group AND " ++ Fragments.in(fr"v.id", ids))

  private[sql] def selectAll(group: Group.Id, contact: Contact.Id): Select[Venue] =
    tableSelect.select[Venue](fr0"WHERE v.contact_id=$contact")

  private def where(group: Group.Id, id: Venue.Id): Fragment =
    fr0"WHERE v.id=(" ++ tableFull.select(Seq(Field("id", "v")), fr0"WHERE pa.group_id=$group AND v.id=$id", Seq()).fr ++ fr0")"
}
