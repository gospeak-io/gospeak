package gospeak.infra.services.storage.sql

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain.Event.Rsvp.Answer
import gospeak.core.domain._
import gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import gospeak.core.services.storage.EventRepo
import gospeak.infra.services.storage.sql.EventRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

class EventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with EventRepo {
  override def create(data: Event.Data)(implicit ctx: OrgaCtx): IO[Event] =
    insert(Event.create(ctx.group.id, data, ctx.info)).run(xa)

  override def edit(event: Event.Slug, data: Event.Data)(implicit ctx: OrgaCtx): IO[Done] = {
    if (data.slug != event) {
      find(data.slug).flatMap {
        case None => update(ctx.group.id, event)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have an event with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.id, event)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def editNotes(event: Event.Slug, notes: String)(implicit ctx: OrgaCtx): IO[Done] =
    updateNotes(ctx.group.id, event)(notes, ctx.user.id, ctx.now).run(xa)

  override def attachCfp(event: Event.Slug, cfp: Cfp.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateCfp(ctx.group.id, event)(cfp, ctx.user.id, ctx.now).run(xa)

  override def editTalks(event: Event.Slug, talks: Seq[Proposal.Id])(implicit ctx: OrgaCtx): IO[Done] =
    updateTalks(ctx.group.id, event)(talks, ctx.user.id, ctx.now).run(xa)

  override def publish(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Done] =
    updatePublished(ctx.group.id, event)(ctx.user.id, ctx.now).run(xa)

  override def find(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Option[Event]] = selectOne(ctx.group.id, event).runOption(xa)

  override def findFull(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Option[Event.Full]] = selectOneFull(ctx.group.id, event).runOption(xa)

  override def findPublished(group: Group.Id, event: Event.Slug): IO[Option[Event.Full]] = selectOnePublished(group, event).runOption(xa)

  override def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event]] = selectPage(ctx.group.id, params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event.Full]] = selectPageFull(ctx.group.id, params).run(xa)

  override def list(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Seq[Event]] = selectAll(ctx.group.id, venue).runList(xa)

  override def list(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[Seq[(Event, Venue)]] = selectAll(ctx.group.id, partner).runList(xa)

  override def listPublished(group: Group.Id, params: Page.Params): IO[Page[Event.Full]] = selectPagePublished(group, params).run(xa)

  override def listPublic(address: GMapPlace, params: Page.Params): IO[Page[Event.Full]] = selectPagePublic(address, params).run(xa)

  override def list(ids: Seq[Event.Id]): IO[Seq[Event]] = runNel(selectAll, ids)

  override def listAfter(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event.Full]] = selectPageAfterFull(ctx.group.id, ctx.now.truncatedTo(ChronoUnit.DAYS), params).run(xa)

  override def listIncoming(params: Page.Params)(implicit ctx: UserCtx): IO[Page[(Event.Full, Option[Event.Rsvp])]] = selectPageIncoming(ctx.user.id, ctx.now, params).run(xa)

  override def countYesRsvp(event: Event.Id): IO[Long] = countRsvp(event, Event.Rsvp.Answer.Yes).runOption(xa).map(_.getOrElse(0))

  override def listRsvps(event: Event.Id): IO[Seq[Event.Rsvp]] = selectAllRsvp(event).runList(xa)

  override def listRsvps(event: Event.Id, answers: NonEmptyList[Event.Rsvp.Answer]): IO[Seq[Event.Rsvp]] = selectAllRsvp(event, answers).runList(xa)

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
    .joinOpt(Tables.venues, _.venue -> _.id).get
    .dropFields(_.name.startsWith("address_"))
  private val tableFull = tableWithVenue
    .joinOpt(Tables.partners, _.partner_id("v") -> _.id).get
    .joinOpt(Tables.contacts, _.contact_id("v") -> _.id).get
    .joinOpt(Tables.cfps, _.cfp_id("e") -> _.id).get
    .join(Tables.groups.dropFields(_.name.startsWith("location_")), _.group_id("e") -> _.id).get
  private val rsvpTable = Tables.eventRsvps
  private val rsvpTableWithUser = rsvpTable
    .join(Tables.users, _.user_id -> _.id).flatMap(_.dropField(_.user_id)).get
  private val tableFullWithMemberAndRsvp = tableFull
    .join(Tables.groupMembers, _.id("g") -> _.group_id).get
    .joinOpt(rsvpTable,
      _.id("e") -> _.event_id,
      _.user_id("gm") -> _.user_id).get
    .joinOpt(Tables.users, _.user_id("er") -> _.id).get
    .dropField(_.user_id("er")).get
    .dropFields(_.prefix == "gm")

  private[sql] def insert(e: Event): Insert[Event] = {
    val values = fr0"${e.id}, ${e.group}, ${e.cfp}, ${e.slug}, ${e.name}, ${e.kind}, ${e.start}, ${e.maxAttendee}, ${e.allowRsvp}, ${e.description}, ${e.orgaNotes.text}, ${e.orgaNotes.updatedAt}, ${e.orgaNotes.updatedBy}, ${e.venue}, ${e.talks}, ${e.tags}, ${e.published}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.event)}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert(e, _ => values)
  }

  private[sql] def update(group: Group.Id, event: Event.Slug)(d: Event.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"cfp_id=${d.cfp}, slug=${d.slug}, name=${d.name}, kind=${d.kind}, start=${d.start}, max_attendee=${d.maxAttendee}, allow_rsvp=${d.allowRsvp}, description=${d.description}, venue=${d.venue}, tags=${d.tags}, meetupGroup=${d.refs.meetup.map(_.group)}, meetupEvent=${d.refs.meetup.map(_.event)}, updated_at=$now, updated_by=$by"
    table.update(fields, where(group, event))
  }

  private[sql] def updateNotes(group: Group.Id, event: Event.Slug)(notes: String, by: User.Id, now: Instant): Update =
    table.update(fr0"orga_notes=$notes, orga_notes_updated_at=$now, orga_notes_updated_by=$by", where(group, event))

  private[sql] def updateCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): Update =
    table.update(fr0"cfp_id=$cfp, updated_at=$now, updated_by=$by", where(group, event))

  private[sql] def updateTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): Update =
    table.update(fr0"talks=$talks, updated_at=$now, updated_by=$by", where(group, event))

  private[sql] def updatePublished(group: Group.Id, event: Event.Slug)(by: User.Id, now: Instant): Update =
    table.update(fr0"published=$now, updated_at=$now, updated_by=$by", where(group, event))

  private[sql] def selectOne(group: Group.Id, event: Event.Slug): Select[Event] =
    table.select[Event](where(group, event))

  private[sql] def selectOneFull(group: Group.Id, event: Event.Slug): Select[Event.Full] =
    tableFull.select[Event.Full](where(group, event))

  private[sql] def selectOnePublished(group: Group.Id, event: Event.Slug): Select[Event.Full] =
    tableFull.select[Event.Full](fr0"WHERE e.group_id=$group AND e.slug=$event AND e.published IS NOT NULL")

  private[sql] def selectPage(group: Group.Id, params: Page.Params): SelectPage[Event] =
    table.selectPage[Event](params, fr0"WHERE e.group_id=$group")

  private[sql] def selectPageFull(group: Group.Id, params: Page.Params): SelectPage[Event.Full] =
    tableFull.selectPage[Event.Full](params, fr0"WHERE e.group_id=$group")

  private[sql] def selectPagePublished(group: Group.Id, params: Page.Params): SelectPage[Event.Full] =
    tableFull.selectPage[Event.Full](params, fr0"WHERE e.group_id=$group AND e.published IS NOT NULL")

  private[sql] def selectPagePublic(address: GMapPlace, params: Page.Params): SelectPage[Event.Full] =
    tableFull.selectPage[Event.Full](params, fr0"WHERE v.address=$address AND e.published IS NOT NULL")

  private[sql] def selectAll(ids: NonEmptyList[Event.Id]): Select[Event] =
    table.select[Event](fr0"WHERE " ++ Fragments.in(fr"e.id", ids))

  private[sql] def selectAll(group: Group.Id, venue: Venue.Id): Select[Event] =
    table.select[Event](fr0"WHERE e.group_id=$group AND e.venue=$venue")

  private[sql] def selectAll(group: Group.Id, partner: Partner.Id): Select[(Event, Venue)] =
    tableWithVenue.select[(Event, Venue)](fr0"WHERE e.group_id=$group AND v.partner_id=$partner")

  private[sql] def selectPageAfterFull(group: Group.Id, now: Instant, params: Page.Params): SelectPage[Event.Full] =
    tableFull.selectPage[Event.Full](params, fr0"WHERE e.group_id=$group AND e.start > $now")

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

  private[sql] def selectAllRsvp(event: Event.Id): Select[Event.Rsvp] =
    rsvpTableWithUser.select[Event.Rsvp](fr0"WHERE er.event_id=$event")

  private[sql] def selectAllRsvp(event: Event.Id, answers: NonEmptyList[Event.Rsvp.Answer]): Select[Event.Rsvp] =
    rsvpTableWithUser.select[Event.Rsvp](fr0"WHERE er.event_id=$event AND " ++ Fragments.in(fr"er.answer", answers))

  private[sql] def selectOneRsvp(event: Event.Id, user: User.Id): Select[Event.Rsvp] =
    rsvpTableWithUser.select[Event.Rsvp](fr0"WHERE er.event_id=$event AND er.user_id=$user")

  private[sql] def selectFirstRsvp(event: Event.Id, answer: Answer): Select[Event.Rsvp] =
    rsvpTableWithUser.selectOne[Event.Rsvp](fr0"WHERE er.event_id=$event AND er.answer=$answer", sort = Seq(Field("answered_at", "er")))
}
