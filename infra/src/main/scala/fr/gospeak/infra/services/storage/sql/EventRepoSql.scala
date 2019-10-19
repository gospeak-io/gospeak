package fr.gospeak.infra.services.storage.sql

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.storage.EventRepo
import fr.gospeak.infra.services.storage.sql.EventRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page, Tag}

class EventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with EventRepo {
  override def create(group: Group.Id, data: Event.Data, by: User.Id, now: Instant): IO[Event] =
    insert(Event.create(group, data, Info(by, now))).run(xa)

  override def edit(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != event) {
      find(group, data.slug).flatMap {
        case None => update(group, event)(data, by, now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have an event with slug ${data.slug}"))
      }
    } else {
      update(group, event)(data, by, now).run(xa)
    }
  }

  override def attachCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): IO[Done] =
    updateCfp(group, event)(cfp, by, now).run(xa)

  override def editTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): IO[Done] =
    updateTalks(group, event)(talks, by, now).run(xa)

  override def publish(group: Group.Id, event: Event.Slug, by: User.Id, now: Instant): IO[Done] =
    updatePublished(group, event)(by, now).run(xa)

  override def find(group: Group.Id, event: Event.Slug): IO[Option[Event]] = selectOne(group, event).runOption(xa)

  override def findPublished(group: Group.Id, event: Event.Slug): IO[Option[Event.Full]] = selectOnePublished(group, event).runOption(xa)

  override def list(group: Group.Id, params: Page.Params): IO[Page[Event]] = selectPage(group, params).run(xa)

  override def list(group: Group.Id, venue: Venue.Id): IO[Seq[Event]] = selectAll(group, venue).runList(xa)

  override def list(group: Group.Id, partner: Partner.Id): IO[Seq[(Event, Venue)]] = selectAll(group, partner).runList(xa)

  override def listPublished(group: Group.Id, params: Page.Params): IO[Page[Event.Full]] = selectPagePublished(group, params).run(xa)

  override def list(ids: Seq[Event.Id]): IO[Seq[Event]] = runNel(selectAll, ids)

  override def listAfter(group: Group.Id, now: Instant, params: Page.Params): IO[Page[Event]] = selectPageAfter(group, now.truncatedTo(ChronoUnit.DAYS), params).run(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object EventRepoSql {
  private val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.events
  private val tableWithVenue = table
    .joinOpt(Tables.venues, _.field("venue"), _.field("id")).get.dropFields(_.name.startsWith("address_"))
  private val tableFull = tableWithVenue
    .joinOpt(Tables.partners, _.field("partner_id", "v"), _.field("id")).get
    .joinOpt(Tables.contacts, _.field("contact_id", "v"), _.field("id")).get
  private val rsvpTable = Tables.eventRsvps
  private val rsvpTableWithUser = rsvpTable
    .join(Tables.users, _.field("user_id"), _.field("id")).flatMap(_.dropField(_.field("user_id"))).get

  private[sql] def insert(e: Event): Insert[Event] = {
    val values = fr0"${e.id}, ${e.group}, ${e.cfp}, ${e.slug}, ${e.name}, ${e.start}, ${e.maxAttendee}, ${e.description}, ${e.venue}, ${e.talks}, ${e.tags}, ${e.published}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.event)}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    table.insert(e, _ => values)
  }

  private[sql] def update(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"e.cfp_id=${data.cfp}, e.slug=${data.slug}, e.name=${data.name}, e.start=${data.start}, e.max_attendee=${data.maxAttendee}, e.description=${data.description}, e.venue=${data.venue}, e.tags=${data.tags}, e.meetupGroup=${data.refs.meetup.map(_.group)}, e.meetupEvent=${data.refs.meetup.map(_.event)}, e.updated=$now, e.updated_by=$by"
    table.update(fields, where(group, event))
  }

  private[sql] def updateCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): Update =
    table.update(fr0"e.cfp_id=$cfp, e.updated=$now, e.updated_by=$by", where(group, event))

  private[sql] def updateTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): Update =
    table.update(fr0"e.talks=$talks, e.updated=$now, e.updated_by=$by", where(group, event))

  private[sql] def updatePublished(group: Group.Id, event: Event.Slug)(by: User.Id, now: Instant): Update =
    table.update(fr0"e.published=$now, e.updated=$now, e.updated_by=$by", where(group, event))

  private[sql] def selectOne(group: Group.Id, event: Event.Slug): Select[Event] =
    table.select[Event](where(group, event))

  private[sql] def selectOnePublished(group: Group.Id, event: Event.Slug): Select[Event.Full] =
    tableFull.select[Event.Full](fr0"WHERE e.group_id=$group AND e.slug=$event AND e.published IS NOT NULL")

  private[sql] def selectPage(group: Group.Id, params: Page.Params): SelectPage[Event] =
    table.selectPage[Event](params, fr0"WHERE e.group_id=$group")

  private[sql] def selectPagePublished(group: Group.Id, params: Page.Params): SelectPage[Event.Full] =
    tableFull.selectPage[Event.Full](params, fr0"WHERE e.group_id=$group AND e.published IS NOT NULL")

  private[sql] def selectAll(ids: NonEmptyList[Event.Id]): Select[Event] =
    table.select[Event](fr"WHERE" ++ Fragments.in(fr"e.id", ids))

  private[sql] def selectAll(group: Group.Id, venue: Venue.Id): Select[Event] =
    table.select[Event](fr"WHERE e.group_id=$group AND e.venue=$venue")

  private[sql] def selectAll(group: Group.Id, partner: Partner.Id): Select[(Event, Venue)] =
    tableWithVenue.select[(Event, Venue)](fr"WHERE e.group_id=$group AND v.partner_id=$partner")

  private[sql] def selectPageAfter(group: Group.Id, now: Instant, params: Page.Params): SelectPage[Event] =
    table.selectPage[Event](params, fr0"WHERE e.group_id=$group AND e.start > $now")

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "e")))

  private def where(group: Group.Id, event: Event.Slug): Fragment = fr0"WHERE e.group_id=$group AND e.slug=$event"

  private[sql] def insertRsvp(e: Event.Rsvp): Insert[Event.Rsvp] =
    rsvpTable.insert[Event.Rsvp](e, _ => fr0"${e.event}, ${e.user.id}, ${e.answer}, ${e.answeredAt}")

  private[sql] def selectPageRsvps(event: Event.Id, params: Page.Params): SelectPage[Event.Rsvp] =
    rsvpTableWithUser.selectPage[Event.Rsvp](params, fr0"WHERE er.event_id=$event")

  private[sql] def selectOneRsvp(event: Event.Id, user: User.Id): Select[Event.Rsvp] =
    rsvpTableWithUser.select[Event.Rsvp](fr0"WHERE er.event_id=$event AND er.user_id=$user")
}
