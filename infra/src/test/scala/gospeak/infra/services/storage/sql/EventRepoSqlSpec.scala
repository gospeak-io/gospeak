package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.FakeCtx
import gospeak.core.domain.{Event, Venue}
import gospeak.infra.services.storage.sql.CfpRepoSqlSpec.{fields => cfpFields, table => cfpTable}
import gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import gospeak.infra.services.storage.sql.EventRepoSql._
import gospeak.infra.services.storage.sql.EventRepoSqlSpec._
import gospeak.infra.services.storage.sql.GroupRepoSqlSpec.{memberTable, fields => groupFields, table => groupTable}
import gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import gospeak.infra.services.storage.sql.VenueRepoSqlSpec.{fields => venueFields, table => venueTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.libs.scala.domain.LiquidMarkdown

class EventRepoSqlSpec extends RepoSpec {
  describe("EventRepoSql") {
    it("should handle crud operations") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue()(ctx).unsafeRunSync()
      val eventData = eventData1.copy(venue = eventData1.venue.map(_ => venue.id))

      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List()
      val event = eventRepo.create(eventData)(ctx).unsafeRunSync()
      eventRepo.listRsvps(event.id).unsafeRunSync() shouldBe List()
      val rsvp = Event.Rsvp(event.id, Event.Rsvp.Answer.Yes, ctx.now, ctx.user)
      eventRepo.createRsvp(rsvp.event, rsvp.answer)(rsvp.user, rsvp.answeredAt).unsafeRunSync()

      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(event)
      eventRepo.listRsvps(event.id).unsafeRunSync() shouldBe List(rsvp)

      val data = eventData2.copy(venue = eventData2.venue.map(_ => venue.id))
      eventRepo.edit(event.slug, data)(ctx).unsafeRunSync()
      eventRepo.editRsvp(rsvp.event, Event.Rsvp.Answer.No)(rsvp.user, rsvp.answeredAt).unsafeRunSync()

      eventRepo.list(params)(ctx).unsafeRunSync().items.map(_.data) shouldBe List(data)
      eventRepo.listRsvps(event.id).unsafeRunSync() shouldBe List(rsvp.copy(answer = Event.Rsvp.Answer.No))
      // no delete...
    }
    it("should fail to create an event when the group does not exists") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user, group)
      an[Exception] should be thrownBy eventRepo.create(eventData1)(ctx).unsafeRunSync()
    }
    it("should fail on duplicate slug for the same group") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue()(ctx).unsafeRunSync()
      val eventData = eventData1.copy(venue = eventData1.venue.map(_ => venue.id))
      eventRepo.create(eventData)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy eventRepo.create(eventData)(ctx).unsafeRunSync()
    }
    it("should perform specific updates") {
      val (user, group, cfp, partner, venue, contact, event, talk, proposal, ctx) = createProposal().unsafeRunSync()
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(event)

      val e1 = event.copy(description = LiquidMarkdown[Message.EventInfo]("desc"))
      eventRepo.editDescription(event.id, e1.description)(ctx.adminCtx).unsafeRunSync()
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(e1)

      val e2 = e1.copy(orgaNotes = Event.Notes("notes", ctx.now, ctx.user.id))
      eventRepo.editNotes(event.slug, e2.orgaNotes.text)(ctx).unsafeRunSync()
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(e2)

      val e3 = e2.copy(cfp = Some(cfp.id))
      eventRepo.attachCfp(event.slug, cfp.id)(ctx).unsafeRunSync()
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(e3)

      val e4 = e3.copy(talks = List(proposal.id))
      eventRepo.editTalks(event.slug, List(proposal.id))(ctx).unsafeRunSync()
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(e4)

      val e5 = e4.copy(published = Some(ctx.now))
      eventRepo.publish(event.slug)(ctx).unsafeRunSync()
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(e5)
    }
    it("should select a page") {
      val (user, group, cfp, partner, venue, contact, event, talk, proposal, ctx) = createProposal().unsafeRunSync()
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(event)
      eventRepo.list(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      eventRepo.list(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(event)
      eventRepo.list(params.search(event.name.value))(ctx).unsafeRunSync().items shouldBe List(event)
      eventRepo.list(params.orderBy("slug"))(ctx).unsafeRunSync().items shouldBe List(event)

      val eventFull = Event.Full(event, event.venue.map(_ => Venue.Full(venue, partner, contact)), event.cfp.map(_ => cfp), group)
      eventRepo.listFull(params)(ctx).unsafeRunSync().items shouldBe List(eventFull)
      eventRepo.listFull(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      eventRepo.listFull(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(eventFull)
      // FIXME eventRepo.listFull(params.search(eventFull.name.value))(ctx).unsafeRunSync().items shouldBe List(eventFull) // ignored during migration: more search than before
      eventRepo.listFull(params.orderBy("slug"))(ctx).unsafeRunSync().items shouldBe List(eventFull)

      eventRepo.listIncoming(params)(ctx).unsafeRunSync().items shouldBe List()
      eventRepo.listIncoming(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      eventRepo.listIncoming(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List()
      // FIXME eventRepo.listIncoming(params.search("q"))(ctx).unsafeRunSync().items shouldBe List() // ignored during migration: more search than before
      eventRepo.listIncoming(params.orderBy("slug"))(ctx).unsafeRunSync().items shouldBe List()
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue()(ctx).unsafeRunSync()
      val eventData = eventData1.copy(venue = eventData1.venue.map(_ => venue.id))
      val event = eventRepo.create(eventData)(ctx).unsafeRunSync()
      val rsvp = Event.Rsvp(event.id, Event.Rsvp.Answer.Yes, ctx.now, ctx.user)
      eventRepo.createRsvp(rsvp.event, rsvp.answer)(rsvp.user, rsvp.answeredAt).unsafeRunSync()
      val eventFull = Event.Full(event, eventData1.venue.map(_ => Venue.Full(venue, partner, contact)), None, group)

      eventRepo.find(event.slug)(ctx).unsafeRunSync() shouldBe Some(event)
      eventRepo.find(event.id)(ctx.adminCtx).unsafeRunSync() shouldBe Some(event)
      eventRepo.findFull(event.slug)(ctx).unsafeRunSync() shouldBe Some(eventFull)
      eventRepo.findPublished(group.id, event.slug).unsafeRunSync() // shouldBe Some(eventFull) published should not be null
      eventRepo.findFull(group.slug, event.slug)(ctx.userAwareCtx).unsafeRunSync() shouldBe Some(eventFull)
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(event)
      eventRepo.listFull(params)(ctx).unsafeRunSync().items shouldBe List(eventFull)
      eventRepo.list(venue.id)(ctx).unsafeRunSync() shouldBe List(event)
      eventRepo.list(partner.id)(ctx).unsafeRunSync() shouldBe List(event -> venue)
      eventRepo.listAllPublishedSlugs()(ctx.userAwareCtx).unsafeRunSync() // shouldBe List(group.id -> event.slug) // published should not be null
      eventRepo.listPublished(group.id, params)(ctx.userAwareCtx).unsafeRunSync().items // shouldBe List(eventFull) // published should not be null
      eventRepo.list(List(event.id)).unsafeRunSync() shouldBe List(event)
      eventRepo.listAllFromGroups(List(group.id))(ctx.adminCtx).unsafeRunSync() shouldBe List(event)
      eventRepo.listAfter(params)(ctx).unsafeRunSync().items // shouldBe List(eventFull) // start date should be after now
      eventRepo.listIncoming(params)(ctx).unsafeRunSync().items // shouldBe List(eventFull -> None) // start date should be after now and published should not be null
      eventRepo.countYesRsvp(event.id).unsafeRunSync() shouldBe 1
      eventRepo.listRsvps(event.id).unsafeRunSync() shouldBe List(rsvp)
      eventRepo.listRsvps(event.id, NonEmptyList.of(Event.Rsvp.Answer.Yes)).unsafeRunSync() shouldBe List(rsvp)
      eventRepo.findRsvp(event.id, rsvp.user.id).unsafeRunSync() shouldBe Some(rsvp)
      eventRepo.findFirstWait(event.id).unsafeRunSync() shouldBe None
      eventRepo.listTags().unsafeRunSync() shouldBe event.tags
    }
    it("should check queries") {
      check(insert(event), s"INSERT INTO ${table.stripSuffix(" e")} (${mapFields(fields, _.stripPrefix("e."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(group.id, event.slug)(eventData1, user.id, now), s"UPDATE $table SET cfp_id=?, slug=?, name=?, kind=?, start=?, max_attendee=?, allow_rsvp=?, description=?, venue=?, tags=?, meetupGroup=?, meetupEvent=?, updated_at=?, updated_by=? WHERE e.group_id=? AND e.slug=?")
      check(updateDescription(event.id)(LiquidMarkdown[Message.EventInfo]("")), s"UPDATE $table SET description=? WHERE e.id=?")
      check(updateNotes(group.id, event.slug)("notes", user.id, now), s"UPDATE $table SET orga_notes=?, orga_notes_updated_at=?, orga_notes_updated_by=? WHERE e.group_id=? AND e.slug=?")
      check(updateCfp(group.id, event.slug)(cfp.id, user.id, now), s"UPDATE $table SET cfp_id=?, updated_at=?, updated_by=? WHERE e.group_id=? AND e.slug=?")
      check(updateTalks(group.id, event.slug)(List(), user.id, now), s"UPDATE $table SET talks=?, updated_at=?, updated_by=? WHERE e.group_id=? AND e.slug=?")
      check(updatePublished(group.id, event.slug)(user.id, now), s"UPDATE $table SET published=?, updated_at=?, updated_by=? WHERE e.group_id=? AND e.slug=?")
      check(selectOne(event.id), s"SELECT $fields FROM $table WHERE e.id=? $orderBy LIMIT 1")
      check(selectOne(group.id, event.slug), s"SELECT $fields FROM $table WHERE e.group_id=? AND e.slug=? $orderBy LIMIT 1")
      check(selectOneFull(group.id, event.slug), s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.slug=? $orderBy LIMIT 1")
      check(selectOneFull(group.slug, event.slug), s"SELECT $fieldsFull FROM $tableFull WHERE g.slug=? AND e.slug=? AND (e.published IS NOT NULL OR g.owners LIKE ?) $orderBy LIMIT 1")
      check(selectOnePublished(group.id, event.slug), s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.slug=? AND e.published IS NOT NULL $orderBy LIMIT 1")
      check(selectPage(params), s"SELECT $fields FROM $table WHERE e.group_id=? $orderBy LIMIT 20 OFFSET 0")
      check(selectPageFull(params), s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? $orderBy LIMIT 20 OFFSET 0")
      check(selectAllPublishedSlugs(), s"SELECT e.group_id, e.slug FROM $table WHERE e.published IS NOT NULL $orderBy")
      check(selectPagePublished(group.id, params), s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL $orderBy LIMIT 20 OFFSET 0")
      check(selectAll(NonEmptyList.of(event.id)), s"SELECT $fields FROM $table WHERE e.id IN (?)  $orderBy")
      check(selectAllFromGroups(NonEmptyList.of(group.id))(adminCtx), s"SELECT $fields FROM $table WHERE e.group_id IN (?)  $orderBy")
      check(selectAll(group.id, venue.id), s"SELECT $fields FROM $table WHERE e.group_id=? AND e.venue=? $orderBy")
      check(selectAll(group.id, partner.id), s"SELECT $fieldsWithVenue FROM $tableWithVenue WHERE e.group_id=? AND v.partner_id=? $orderBy")
      check(selectPageAfterFull(params), s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.start > ? $orderBy LIMIT 20 OFFSET 0", checkCount = false)
      check(selectPageIncoming(params), s"SELECT $fieldsFullWithMemberAndRsvp FROM $tableFullWithMemberAndRsvp WHERE e.start > ? AND e.published IS NOT NULL AND gm.user_id=? $orderBy LIMIT 20 OFFSET 0", checkCount = false)
      check(selectTags(), s"SELECT e.tags FROM $table $orderBy")

      check(countRsvp(event.id, rsvp.answer), s"SELECT COUNT(*) FROM $rsvpTable WHERE er.event_id=? AND er.answer=? GROUP BY er.event_id, er.answer ORDER BY er.event_id IS NULL, er.event_id LIMIT 1")
      check(insertRsvp(rsvp), s"INSERT INTO ${rsvpTable.stripSuffix(" er")} (${mapFields(rsvpFields, _.stripPrefix("er."))}) VALUES (${mapFields(rsvpFields, _ => "?")})")
      check(updateRsvp(event.id, user.id, rsvp.answer, now), s"UPDATE $rsvpTable SET answer=?, answered_at=? WHERE er.event_id=? AND er.user_id=?")
      check(selectAllRsvp(event.id), s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? $rsvpOrderBy")
      check(selectAllRsvp(event.id, NonEmptyList.of(rsvp.answer)), s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? AND er.answer IN (?)  $rsvpOrderBy")
      check(selectOneRsvp(event.id, user.id), s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? AND er.user_id=? $rsvpOrderBy")
      check(selectFirstRsvp(event.id, Event.Rsvp.Answer.Wait), s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? AND er.answer=? ORDER BY er.answered_at IS NULL, er.answered_at LIMIT 1")
    }
  }
}

object EventRepoSqlSpec {

  import RepoSpec._

  val table = "events e"
  val fields: String = mapFields("id, group_id, cfp_id, slug, name, kind, start, max_attendee, allow_rsvp, description, orga_notes, orga_notes_updated_at, orga_notes_updated_by, venue, talks, tags, published, meetupGroup, meetupEvent, created_at, created_by, updated_at, updated_by", "e." + _)
  val orderBy = "ORDER BY e.start IS NULL, e.start DESC"

  private val tableWithVenue = s"$table LEFT OUTER JOIN $venueTable ON e.venue=v.id"
  private val fieldsWithVenue = s"$fields, $venueFields"

  private val tableFull = s"$tableWithVenue LEFT OUTER JOIN $partnerTable ON v.partner_id=pa.id LEFT OUTER JOIN $contactTable ON v.contact_id=ct.id LEFT OUTER JOIN $cfpTable ON e.cfp_id=c.id INNER JOIN $groupTable ON e.group_id=g.id"
  private val fieldsFull = s"$fieldsWithVenue, $partnerFields, $contactFields, $cfpFields, $groupFields"

  private val rsvpTable = "event_rsvps er"
  private val rsvpFields = mapFields("event_id, user_id, answer, answered_at", "er." + _)
  private val rsvpOrderBy = "ORDER BY er.answered_at IS NULL, er.answered_at"
  private val rsvpTableWithUser = s"$rsvpTable INNER JOIN $userTable ON er.user_id=u.id"
  private val rsvpFieldsWithUser = s"${rsvpFields.replaceAll("er.user_id, ", "")}, $userFields"

  private val tableFullWithMemberAndRsvp = s"$tableFull INNER JOIN $memberTable ON g.id=gm.group_id LEFT OUTER JOIN $rsvpTable ON e.id=er.event_id AND gm.user_id=er.user_id LEFT OUTER JOIN $userTable ON er.user_id=u.id"
  private val fieldsFullWithMemberAndRsvp = s"$fieldsFull, $rsvpFieldsWithUser"
}
