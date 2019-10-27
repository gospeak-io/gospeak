package fr.gospeak.infra.services.storage.sql

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.Event.Rsvp.Answer
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.storage.EventRepo
import fr.gospeak.infra.services.storage.sql.EventRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
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

  override def listIncoming(params: Page.Params)(user: User.Id, now: Instant): IO[Page[(Event.Full, Option[Event.Rsvp])]] = selectPageIncoming(user, now, params).run(xa)

  override def countYesRsvp(event: Event.Id): IO[Long] = countRsvp(event, Event.Rsvp.Answer.Yes).runOption(xa).map(_.getOrElse(0))

  override def findRsvp(event: Event.Id, user: User.Id): IO[Option[Event.Rsvp]] = selectOneRsvp(event, user).runOption(xa)

  override def findFirstWait(event: Event.Id): IO[Option[Event.Rsvp]] = selectFirstRsvp(event, Answer.Wait).runOption(xa)

  override def createRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Done] =
    insertRsvp(Event.Rsvp(event, answer, now, user)).run(xa).map(_ => Done)

  override def editRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Done] = updateRsvp(event, user.id, answer, now).run(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object EventRepoSql {
  private val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.events
  private val tableWithVenue = table
    .joinOpt(Tables.venues, _.field("venue") -> _.field("id")).get.dropFields(_.name.startsWith("address_"))
  private val tableFull = tableWithVenue
    .joinOpt(Tables.partners, _.field("partner_id", "v") -> _.field("id")).get
    .joinOpt(Tables.contacts, _.field("contact_id", "v") -> _.field("id")).get
    .join(Tables.groups, _.field("group_id", "e") -> _.field("id")).get
  private val rsvpTable = Tables.eventRsvps
  private val rsvpTableWithUser = rsvpTable
    .join(Tables.users, _.field("user_id") -> _.field("id")).flatMap(_.dropField(_.field("user_id"))).get
  private val tableFullWithMemberAndRsvp = tableFull
    .join(Tables.groupMembers, _.field("id", "g") -> _.field("group_id")).get
    .joinOpt(rsvpTable,
      _.field("id", "e") -> _.field("event_id"),
      _.field("user_id", "gm") -> _.field("user_id", "er")).get
    .joinOpt(Tables.users, _.field("user_id", "er") -> _.field("id")).get
    .dropField(_.field("user_id", "er")).get
    .dropFields(_.prefix == "gm")

  private[sql] def insert(e: Event): Insert[Event] = {
    val values = fr0"${e.id}, ${e.group}, ${e.cfp}, ${e.slug}, ${e.name}, ${e.start}, ${e.maxAttendee}, ${e.description}, ${e.venue}, ${e.talks}, ${e.tags}, ${e.published}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.event)}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    table.insert(e, _ => values)
  }

  private[sql] def update(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"cfp_id=${data.cfp}, slug=${data.slug}, name=${data.name}, start=${data.start}, max_attendee=${data.maxAttendee}, description=${data.description}, venue=${data.venue}, tags=${data.tags}, meetupGroup=${data.refs.meetup.map(_.group)}, meetupEvent=${data.refs.meetup.map(_.event)}, updated=$now, updated_by=$by"
    table.update(fields, where(group, event))
  }

  private[sql] def updateCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): Update =
    table.update(fr0"cfp_id=$cfp, updated=$now, updated_by=$by", where(group, event))

  private[sql] def updateTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): Update =
    table.update(fr0"talks=$talks, updated=$now, updated_by=$by", where(group, event))

  private[sql] def updatePublished(group: Group.Id, event: Event.Slug)(by: User.Id, now: Instant): Update =
    table.update(fr0"published=$now, updated=$now, updated_by=$by", where(group, event))

  private[sql] def selectOne(group: Group.Id, event: Event.Slug): Select[Event] =
    table.select[Event](where(group, event))

  private[sql] def selectOnePublished(group: Group.Id, event: Event.Slug): Select[Event.Full] =
    tableFull.select[Event.Full](fr0"WHERE e.group_id=$group AND e.slug=$event AND e.published IS NOT NULL")

  private[sql] def selectPage(group: Group.Id, params: Page.Params): SelectPage[Event] =
    table.selectPage[Event](params, fr0"WHERE e.group_id=$group")

  private[sql] def selectPagePublished(group: Group.Id, params: Page.Params): SelectPage[Event.Full] =
    tableFull.selectPage[Event.Full](params, fr0"WHERE e.group_id=$group AND e.published IS NOT NULL")

  private[sql] def selectAll(ids: NonEmptyList[Event.Id]): Select[Event] =
    table.select[Event](fr0"WHERE " ++ Fragments.in(fr"e.id", ids))

  private[sql] def selectAll(group: Group.Id, venue: Venue.Id): Select[Event] =
    table.select[Event](fr0"WHERE e.group_id=$group AND e.venue=$venue")

  private[sql] def selectAll(group: Group.Id, partner: Partner.Id): Select[(Event, Venue)] =
    tableWithVenue.select[(Event, Venue)](fr0"WHERE e.group_id=$group AND v.partner_id=$partner")

  private[sql] def selectPageAfter(group: Group.Id, now: Instant, params: Page.Params): SelectPage[Event] =
    table.selectPage[Event](params, fr0"WHERE e.group_id=$group AND e.start > $now")

  private[sql] def selectPageIncoming(user: User.Id, now: Instant, params: Page.Params): SelectPage[(Event.Full, Option[Event.Rsvp])] =
    tableFullWithMemberAndRsvp.selectPage[(Event.Full, Option[Event.Rsvp])](params, fr0"WHERE e.start > $now AND e.published IS NOT NULL AND gm.user_id=$user")

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "e")), Seq())

  private def where(group: Group.Id, event: Event.Slug): Fragment = fr0"WHERE e.group_id=$group AND e.slug=$event"

  private[sql] def countRsvp(event: Event.Id, answer: Event.Rsvp.Answer): Select[Long] =
    rsvpTable.select[Long](Seq(Field("count(*)", "")), fr0"WHERE er.event_id=$event AND er.answer=$answer GROUP BY er.event_id, er.answer", Seq())

  private[sql] def insertRsvp(e: Event.Rsvp): Insert[Event.Rsvp] =
    rsvpTable.insert[Event.Rsvp](e, _ => fr0"${e.event}, ${e.user.id}, ${e.answer}, ${e.answeredAt}")

  private[sql] def updateRsvp(event: Event.Id, user: User.Id, answer: Answer, now: Instant): Update =
    rsvpTable.update(fr0"answer=$answer, answered_at=$now", fr0"WHERE er.event_id=$event AND er.user_id=$user")

  private[sql] def selectPageRsvps(event: Event.Id, params: Page.Params): SelectPage[Event.Rsvp] =
    rsvpTableWithUser.selectPage[Event.Rsvp](params, fr0"WHERE er.event_id=$event")

  private[sql] def selectOneRsvp(event: Event.Id, user: User.Id): Select[Event.Rsvp] =
    rsvpTableWithUser.select[Event.Rsvp](fr0"WHERE er.event_id=$event AND er.user_id=$user")

  private[sql] def selectFirstRsvp(event: Event.Id, answer: Answer): Select[Event.Rsvp] =
    rsvpTableWithUser.selectOne[Event.Rsvp](fr0"WHERE er.event_id=$event AND er.answer=$answer", sort = Seq(Field("answered_at", "er")))
}
