package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.utils.DoobieUtils.Table
import gospeak.libs.scala.Extensions._

object Tables {
  private val socialFields = Seq("facebook", "instagram", "twitter", "linkedIn", "youtube", "meetup", "eventbrite", "slack", "discord", "github").map("social_" + _)

  val users: Table = Table.from(
    name = "users",
    prefix = "u",
    fields = Seq("id", "slug", "status", "first_name", "last_name", "email", "email_validated", "email_validation_before_login", "avatar", "title", "bio", "company", "location", "phone", "website") ++ socialFields ++ Seq("created_at", "updated_at"),
    sort = Seq("first_name"),
    search = Seq("id", "slug", "first_name", "last_name", "email", "title", "bio")).get

  val credentials: Table = Table.from(
    name = "credentials",
    prefix = "cd",
    fields = Seq("provider_id", "provider_key", "hasher", "password", "salt"),
    sort = Seq(),
    search = Seq()).get

  val logins: Table = Table.from(
    name = "logins",
    prefix = "lg",
    fields = Seq("provider_id", "provider_key", "user_id"),
    sort = Seq(),
    search = Seq()).get

  val talks: Table = Table.from(
    name = "talks",
    prefix = "t",
    fields = Seq("id", "slug", "status", "title", "duration", "description", "message", "speakers", "slides", "video", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("status = 'Archived'", "title"),
    search = Seq("id", "slug", "status", "title", "description", "message", "tags")).get

  val groups: Table = Table.from(
    name = "groups",
    prefix = "g",
    fields = Seq("id", "slug", "name", "logo", "banner", "contact", "website", "description", "location", "location_id", "location_lat", "location_lng", "location_locality", "location_country", "owners") ++ socialFields ++ Seq("tags", "status", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("name"),
    search = Seq("id", "slug", "name", "contact", "description", "location_locality", "location_country", "tags")).get

  val groupSettings: Table = Table.from(
    name = "group_settings",
    prefix = "gs",
    fields = Seq("group_id", "meetup_access_token", "meetup_refresh_token", "meetup_group_slug", "meetup_logged_user_id", "meetup_logged_user_name", "slack_token", "slack_bot_name", "slack_bot_avatar", "event_description", "event_templates", "actions", "updated_at", "updated_by"),
    sort = Seq(),
    search = Seq()).get

  val groupMembers: Table = Table.from(
    name = "group_members",
    prefix = "gm",
    fields = Seq("group_id", "user_id", "role", "presentation", "joined_at", "leaved_at"),
    sort = Seq("joined_at"),
    search = Seq("role", "presentation")).get

  val cfps: Table = Table.from(
    name = "cfps",
    prefix = "c",
    fields = Seq("id", "group_id", "slug", "name", "begin", "close", "description", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("-close", "name"),
    search = Seq("id", "slug", "name", "description", "tags")).get

  val partners: Table = Table.from(
    name = "partners",
    prefix = "pa",
    fields = Seq("id", "group_id", "slug", "name", "notes", "description", "logo") ++ socialFields ++ Seq("created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("name"),
    search = Seq("id", "slug", "name", "notes", "description")).get

  val contacts: Table = Table.from(
    name = "contacts",
    prefix = "ct",
    fields = Seq("id", "partner_id", "first_name", "last_name", "email", "notes", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("last_name", "first_name"),
    search = Seq("id", "first_name", "last_name", "email")).get

  val venues: Table = Table.from(
    name = "venues",
    prefix = "v",
    fields = Seq("id", "partner_id", "contact_id", "address", "address_id", "address_lat", "address_lng", "address_locality", "address_country", "notes", "room_size", "meetupGroup", "meetupVenue", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("created_at"),
    search = Seq("id", "address", "notes")).get

  val events: Table = Table.from(
    name = "events",
    prefix = "e",
    fields = Seq("id", "group_id", "cfp_id", "slug", "name", "start", "max_attendee", "allow_rsvp", "description", "orga_notes", "orga_notes_updated_at", "orga_notes_updated_by", "venue", "talks", "tags", "published", "meetupGroup", "meetupEvent", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("-start"),
    search = Seq("id", "slug", "name", "description", "tags")).get

  val eventRsvps: Table = Table.from(
    name = "event_rsvps",
    prefix = "er",
    fields = Seq("event_id", "user_id", "answer", "answered_at"),
    sort = Seq("answered_at"),
    search = Seq("answer")).get

  val proposals: Table = Table.from(
    name = "proposals",
    prefix = "p",
    fields = Seq("id", "talk_id", "cfp_id", "event_id", "status", "title", "duration", "description", "message", "speakers", "slides", "video", "tags", "orga_tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("-created_at"),
    search = Seq("id", "title", "status", "description", "message", "tags")).get

  val proposalRatings: Table = Table.from(
    name = "proposal_ratings",
    prefix = "pr",
    fields = Seq("proposal_id", "grade", "created_at", "created_by"),
    sort = Seq("created_at"),
    search = Seq("proposal_id", "grade", "created_by")).get

  val sponsorPacks: Table = Table.from(
    name = "sponsor_packs",
    prefix = "sp",
    fields = Seq("id", "group_id", "slug", "name", "description", "price", "currency", "duration", "active", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("-active", "-price"),
    search = Seq("id", "slug", "name", "description")).get

  val sponsors: Table = Table.from(
    name = "sponsors",
    prefix = "s",
    fields = Seq("id", "group_id", "partner_id", "sponsor_pack_id", "contact_id", "start", "finish", "paid", "price", "currency", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("-start"),
    search = Seq("id")).get

  val comments: Table = Table.from(
    name = "comments",
    prefix = "co",
    fields = Seq("event_id", "proposal_id", "id", "kind", "answers", "text", "created_at", "created_by"),
    sort = Seq("created_at"),
    search = Seq("id", "kind", "answers", "text", "created_by")).get

  val userRequests: Table = Table.from(
    name = "user_requests",
    prefix = "ur",
    fields = Seq("id", "kind", "group_id", "cfp_id", "event_id", "talk_id", "proposal_id", "email", "payload", "deadline", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by"),
    sort = Seq("-created_at"),
    search = Seq("id", "email", "group_id", "created_by")).get

  val externalEvents: Table = Table.from(
    name = "external_events",
    prefix = "ee",
    fields = Seq("id", "name", "kind", "logo", "description", "start", "finish", "location", "location_id", "location_lat", "location_lng", "location_locality", "location_country", "url", "tickets_url", "videos_url", "twitter_account", "twitter_hashtag", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("-start", "name"),
    search = Seq("id", "name", "description", "location", "url", "twitter_account", "twitter_hashtag", "tags")).get

  val externalCfps: Table = Table.from(
    name = "external_cfps",
    prefix = "ec",
    fields = Seq("id", "event_id", "description", "begin", "close", "url", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("close", "id"),
    search = Seq("id", "description", "url")).get

  val externalProposals: Table = Table.from(
    name = "external_proposals",
    prefix = "ep",
    fields = Seq("id", "talk_id", "event_id", "status", "title", "duration", "description", "message", "speakers", "slides", "video", "url", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Seq("title", "created_at"),
    search = Seq("id", "title", "status", "description", "message", "tags")).get

  val all: Seq[Table] = Seq(users, credentials, logins, talks, groups, groupSettings, groupMembers, cfps, partners, contacts, venues, events, eventRsvps, proposals, proposalRatings, sponsorPacks, sponsors, comments, userRequests, externalEvents, externalCfps, externalProposals)
}
