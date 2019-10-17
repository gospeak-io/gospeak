package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Group, Partner, User, Venue}
import fr.gospeak.core.services.storage.VenueRepo
import fr.gospeak.infra.services.storage.sql.ContactRepoSql.{fields => contactFields, table => contactTable}
import fr.gospeak.infra.services.storage.sql.PartnerRepoSql.{fields => partnerFields, table => partnerTable}
import fr.gospeak.infra.services.storage.sql.VenueRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.{Done, Page}

class VenueRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with VenueRepo {
  override def create(group: Group.Id, data: Venue.Data, by: User.Id, now: Instant): IO[Venue] =
    run(insert, Venue(group, data, Info(by, now)))

  override def edit(group: Group.Id, id: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): IO[Done] =
    run(update(group, id)(data, by, now))

  override def listFull(group: Group.Id, params: Page.Params): IO[Page[Venue.Full]] = run(selectPageFull(group, params).page)

  override def listFull(partner: Partner.Id): IO[Seq[Venue.Full]] = run(selectAllFull(partner).to[List])

  override def listFull(group: Group.Id): IO[Seq[Venue.Full]] = run(selectAllFull(group).to[List])

  override def listFull(group: Group.Id, ids: Seq[Venue.Id]): IO[Seq[Venue.Full]] = runIn[Venue.Id, Venue.Full](selectAllFull(group, _))(ids)

  override def findFull(group: Group.Id, id: Venue.Id): IO[Option[Venue.Full]] = run(selectOneFull(group, id).option)
}

object VenueRepoSql {
  private val _ = venueIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "venues"
  private val writeFields = Seq("id", "partner_id", "contact_id", "address", "address_lat", "address_lng", "address_country", "description", "room_size", "meetupGroup", "meetupVenue", "created", "created_by", "updated", "updated_by")
  private[sql] val fields = writeFields.filterNot(_.startsWith("address_"))
  private val tableFr: Fragment = Fragment.const0(table)
  private val writeFieldsFr: Fragment = Fragment.const0(writeFields.mkString(", "))
  private val searchFields = Seq("id", "address", "description")
  private val defaultSort = Page.OrderBy("created")

  private val tableFullFr = Fragment.const0(s"$table v INNER JOIN $partnerTable p ON v.partner_id=p.id LEFT OUTER JOIN $contactTable c ON v.contact_id=c.id")
  private val fieldsFullFr = Fragment.const0((fields.map("v." + _) ++ partnerFields.map("p." + _) ++ contactFields.map("c." + _)).mkString(", "))

  private[sql] def insert(e: Venue): doobie.Update0 = {
    val values = fr0"${e.id}, ${e.partner}, ${e.contact}, ${e.address}, ${e.address.geo.lat}, ${e.address.geo.lng}, ${e.address.country}, ${e.description}, ${e.roomSize}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.venue)}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    buildInsert(tableFr, writeFieldsFr, values).update
  }

  private[sql] def update(group: Group.Id, id: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"contact_id=${data.contact}, address=${data.address}, address_lat=${data.address.geo.lat}, address_lng=${data.address.geo.lng}, address_country=${data.address.country}, description=${data.description}, room_size=${data.roomSize}, meetupGroup=${data.refs.meetup.map(_.group)}, meetupVenue=${data.refs.meetup.map(_.venue)}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, id)).update
  }

  private[sql] def selectOneFull(group: Group.Id, id: Venue.Id): doobie.Query0[Venue.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr0"WHERE p.group_id=$group AND v.id=$id").query[Venue.Full]

  private[sql] def selectPageFull(group: Group.Id, params: Page.Params): Paginated[Venue.Full] =
    Paginated[Venue.Full](tableFullFr, fieldsFullFr, fr0"WHERE p.group_id=$group", params, defaultSort, searchFields, "v")

  private[sql] def selectAllFull(group: Group.Id): doobie.Query0[Venue.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr"WHERE p.group_id=$group").query[Venue.Full]

  private[sql] def selectAllFull(partner: Partner.Id): doobie.Query0[Venue.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr"WHERE v.partner_id=$partner").query[Venue.Full]

  private[sql] def selectAllFull(group: Group.Id, ids: NonEmptyList[Venue.Id]): doobie.Query0[Venue.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr"WHERE p.group_id=$group AND " ++ Fragments.in(fr"v.id", ids)).query[Venue.Full]

  private def where(group: Group.Id, id: Venue.Id): Fragment =
    fr0"WHERE id=(SELECT v.id FROM " ++ tableFullFr ++ fr0" WHERE p.group_id=$group AND v.id=$id" ++ fr0")"
}
