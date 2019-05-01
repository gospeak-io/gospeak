package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Group, Partner, User, Venue}
import fr.gospeak.core.services.VenueRepo
import fr.gospeak.infra.services.storage.sql.VenueRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{Done, Page}

class VenueRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with VenueRepo {
  override def create(group: Group.Id, data: Venue.Data, by: User.Id, now: Instant): IO[Venue] =
    run(insert, Venue(group, data, Info(by, now)))

  override def edit(group: Group.Id, id: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): IO[Done] =
    run(update(group, id)(data, by, now))

  override def list(group: Group.Id, params: Page.Params): IO[Page[(Partner, Venue)]] = run(Queries.selectPage(selectPage(group, _), params))

  override def list(partner: Partner.Id): IO[Seq[Venue]] = run(selectAll(partner).to[List])

  override def list(group: Group.Id): IO[Seq[(Partner, Venue)]] = run(selectAll(group).to[List])

  override def list(group: Group.Id, ids: Seq[Venue.Id]): IO[Seq[(Partner, Venue)]] = runIn[Venue.Id, (Partner, Venue)](selectAll(group, _))(ids)

  override def find(group: Group.Id, id: Venue.Id): IO[Option[(Partner, Venue)]] = run(selectOne(group, id).option)
}

object VenueRepoSql {
  private val _ = venueIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "venues"
  private val writeFields = Seq("id", "partner_id", "address", "address_lat", "address_lng", "address_country", "description", "room_size", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val writeFieldsFr: Fragment = Fragment.const0(writeFields.mkString(", "))
  private val searchFields = Seq("id", "address", "description")
  private val defaultSort = Page.OrderBy("created")

  private[sql] val fields = writeFields.filterNot(_.startsWith("address_"))
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val partnerAndVenueTables = Fragment.const0(s"$table v INNER JOIN ${PartnerRepoSql.table} p ON v.partner_id=p.id")
  private val partnerAndVenueFields = Fragment.const0((PartnerRepoSql.fields.map("p." + _) ++ fields.map("v." + _)).mkString(", "))

  private def values(e: Venue): Fragment =
    fr0"${e.id}, ${e.partner}, ${e.address}, ${e.address.lat}, ${e.address.lng}, ${e.address.country}, ${e.description}, ${e.roomSize}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Venue): doobie.Update0 =
    buildInsert(tableFr, writeFieldsFr, values(elt)).update

  private[sql] def update(group: Group.Id, id: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"address=${data.address}, address_lat=${data.address.lat}, address_lng=${data.address.lng}, address_country=${data.address.country}, description=${data.description}, room_size=${data.roomSize}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, id)).update
  }

  private[sql] def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[(Partner, Venue)], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE p.group_id=$group"), Some("v"))
    (buildSelect(partnerAndVenueTables, partnerAndVenueFields, page.all).query[(Partner, Venue)], buildSelect(partnerAndVenueTables, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectAll(group: Group.Id): doobie.Query0[(Partner, Venue)] =
    buildSelect(partnerAndVenueTables, partnerAndVenueFields, fr"WHERE p.group_id=$group").query[(Partner, Venue)]

  private[sql] def selectAll(partner: Partner.Id): doobie.Query0[Venue] =
    buildSelect(tableFr, fieldsFr, fr"WHERE partner_id=$partner").query[Venue]

  private[sql] def selectAll(group: Group.Id, ids: NonEmptyList[Venue.Id]): doobie.Query0[(Partner, Venue)] =
    buildSelect(partnerAndVenueTables, partnerAndVenueFields, fr"WHERE p.group_id=$group AND " ++ Fragments.in(fr"v.id", ids)).query[(Partner, Venue)]

  private[sql] def selectOne(group: Group.Id, id: Venue.Id): doobie.Query0[(Partner, Venue)] =
    buildSelect(partnerAndVenueTables, partnerAndVenueFields, fr0"WHERE p.group_id=$group AND v.id=$id").query[(Partner, Venue)]

  private def where(group: Group.Id, id: Venue.Id): Fragment =
    fr0"WHERE id=(SELECT v.id FROM " ++ partnerAndVenueTables ++ fr0" WHERE p.group_id=$group AND v.id=$id" ++ fr0")"
}


