package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.utils.DoobieUtils.Table
import fr.gospeak.libs.scalautils.Extensions._

object Tables {
  val user: Table = Table.from(
    name = "users",
    prefix = "u",
    fields = Seq("id", "slug", "first_name", "last_name", "email", "email_validated", "avatar", "avatar_source", "status", "bio", "company", "location", "twitter", "linkedin", "phone", "website", "created", "updated"),
    sort = Seq("first_name"),
    search = Seq("id", "slug", "first_name", "last_name", "email")).get

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
    fields = Seq("id", "slug", "status", "title", "duration", "description", "speakers", "slides", "video", "tags", "created", "created_by", "updated", "updated_by"),
    sort = Seq("title"),
    search = Seq("id", "slug", "title", "description", "tags")).get

  val groups: Table = Table.from(
    name = "groups",
    prefix = "g",
    fields = Seq("id", "slug", "name", "contact", "description", "owners", "tags", "created", "created_by", "updated", "updated_by"),
    sort = Seq("name"),
    search = Seq("id", "slug", "name", "contact", "description", "tags")).get

  val cfps: Table = Table.from(
    name = "cfps",
    prefix = "c",
    fields = Seq("id", "group_id", "slug", "name", "begin", "close", "description", "tags", "created", "created_by", "updated", "updated_by"),
    sort = Seq("-close", "name"),
    search = Seq("id", "slug", "name", "description", "tags")).get

  val partners: Table = Table.from(
    name = "partners",
    prefix = "pa",
    fields = Seq("id", "group_id", "slug", "name", "notes", "description", "logo", "twitter", "created", "created_by", "updated", "updated_by"),
    sort = Seq("name"),
    search = Seq("id", "slug", "name", "notes", "description")).get

  val contacts: Table = Table.from(
    name = "contacts",
    prefix = "ct",
    fields = Seq("id", "partner_id", "first_name", "last_name", "email", "description", "created", "created_by", "updated", "updated_by"),
    sort = Seq("last_name", "first_name"),
    search = Seq("id", "first_name", "last_name", "email")).get

  val venues: Table = Table.from(
    name = "venues",
    prefix = "v",
    fields = Seq("id", "partner_id", "contact_id", "address", "address_lat", "address_lng", "address_country", "description", "room_size", "meetupGroup", "meetupVenue", "created", "created_by", "updated", "updated_by"),
    sort = Seq("created"),
    search = Seq("id", "address", "description")).get

  val events: Table = Table.from(
    name = "events",
    prefix = "e",
    fields = Seq("id", "group_id", "cfp_id", "slug", "name", "start", "max_attendee", "description", "venue", "talks", "tags", "published", "meetupGroup", "meetupEvent", "created", "created_by", "updated", "updated_by"),
    sort = Seq("-start"),
    search = Seq("id", "slug", "name", "description", "tags")).get

  val proposals: Table = Table.from(
    name = "proposals",
    prefix = "p",
    fields = Seq("id", "talk_id", "cfp_id", "event_id", "status", "title", "duration", "description", "speakers", "slides", "video", "tags", "created", "created_by", "updated", "updated_by"),
    sort = Seq("-created"),
    search = Seq("id", "title", "status", "description", "tags")).get

  val sponsorPacks: Table = Table.from(
    name = "sponsor_packs",
    prefix = "sp",
    fields = Seq("id", "group_id", "slug", "name", "description", "price", "currency", "duration", "active", "created", "created_by", "updated", "updated_by"),
    sort = Seq("active", "-price"),
    search = Seq("id", "slug", "name", "description")).get

  val sponsors: Table = Table.from(
    name = "sponsors",
    prefix = "s",
    fields = Seq("id", "group_id", "partner_id", "sponsor_pack_id", "contact_id", "start", "finish", "paid", "price", "currency", "created", "created_by", "updated", "updated_by"),
    sort = Seq("-start"),
    search = Seq("id")).get

  val requests: Table = Table.from(
    name = "requests",
    prefix = "r",
    fields = Seq("id", "kind", "group_id", "talk_id", "proposal_id", "email", "deadline", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by"),
    sort = Seq("-created"),
    search = Seq("id", "email", "group_id", "created_by")).get

  val groupSettings: Table = Table.from(
    name = "group_settings",
    prefix = "gs",
    fields = Seq("group_id", "meetup_access_token", "meetup_refresh_token", "meetup_group_slug", "meetup_logged_user_id", "meetup_logged_user_name", "slack_token", "slack_bot_name", "slack_bot_avatar", "event_description", "event_templates", "actions", "updated", "updated_by"),
    sort = Seq(),
    search = Seq()).get

  val members: Table = Table.from(
    name = "group_members",
    prefix = "gm",
    fields = Seq("group_id", "user_id", "role", "presentation", "joined_at"),
    sort = Seq("joined_at"),
    search = Seq("role", "presentation")).get

  val eventRsvps: Table = Table.from(
    name = "event_rsvps",
    prefix = "er",
    fields = Seq("event_id", "user_id", "answer", "answered_at"),
    sort = Seq("answered_at"),
    search = Seq("answer")).get

  val all: Seq[Table] = Seq(user, credentials, logins, talks, groups, cfps, partners, contacts, venues, events, proposals, sponsorPacks, sponsors, requests, groupSettings, members, eventRsvps)
}
