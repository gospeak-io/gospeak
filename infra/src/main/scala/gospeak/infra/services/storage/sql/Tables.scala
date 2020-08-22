package gospeak.infra.services.storage.sql

import gospeak.libs.scala.Extensions._
import gospeak.libs.sql.doobie.{Field, Table}

object Tables {
  private val socialFields = List("facebook", "instagram", "twitter", "linkedIn", "youtube", "meetup", "eventbrite", "slack", "discord", "github").map("social_" + _)
  private val locationFields = List("location", "location_id", "location_lat", "location_lng", "location_locality", "location_country")

  val users: Table = Table.from(
    name = "users",
    prefix = "u",
    fields = List("id", "slug", "status", "first_name", "last_name", "email", "email_validated", "email_validation_before_login", "avatar", "title", "bio", "mentoring", "company", "location", "phone", "website") ++ socialFields ++ List("created_at", "updated_at"),
    sort = Table.Sort("name", Field("last_name", "u"), Field("first_name", "u")),
    search = List("id", "slug", "first_name", "last_name", "email", "title", "bio", "mentoring"),
    filters = List()).get

  val credentials: Table = Table.from(
    name = "credentials",
    prefix = "cd",
    fields = List("provider_id", "provider_key", "hasher", "password", "salt"),
    sort = Table.Sort("provider", Field("provider_id", "cd"), Field("provider_key", "cd")),
    search = List(),
    filters = List()).get

  val logins: Table = Table.from(
    name = "logins",
    prefix = "lg",
    fields = List("provider_id", "provider_key", "user_id"),
    sort = Table.Sort("provider", Field("provider_id", "lg"), Field("provider_key", "lg")),
    search = List(),
    filters = List()).get

  val talks: Table = Table.from(
    name = "talks",
    prefix = "t",
    fields = List("id", "slug", "status", "title", "duration", "description", "message", "speakers", "slides", "video", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("title", Field("status = 'Archived'", "t"), Field("title", "t")),
    search = List("id", "slug", "status", "title", "description", "message", "tags"),
    filters = List()).get

  val groups: Table = Table.from(
    name = "groups",
    prefix = "g",
    fields = List("id", "slug", "name", "logo", "banner", "contact", "website", "description") ++ locationFields ++ List("owners") ++ socialFields ++ List("tags", "status", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("name", "g"),
    search = List("id", "slug", "name", "contact", "description", "location_locality", "location_country", "tags"),
    filters = List()).get

  val groupSettings: Table = Table.from(
    name = "group_settings",
    prefix = "gs",
    fields = List("group_id", "meetup_access_token", "meetup_refresh_token", "meetup_group_slug", "meetup_logged_user_id", "meetup_logged_user_name", "slack_token", "slack_bot_name", "slack_bot_avatar", "event_description", "event_templates", "proposal_tweet", "actions", "updated_at", "updated_by"),
    sort = Table.Sort("group_id", "gs"),
    search = List(),
    filters = List()).get

  val groupMembers: Table = Table.from(
    name = "group_members",
    prefix = "gm",
    fields = List("group_id", "user_id", "role", "presentation", "joined_at", "leaved_at"),
    sort = Table.Sort("joined", "join date", Field("joined_at", "gm")),
    search = List("role", "presentation"),
    filters = List()).get

  val cfps: Table = Table.from(
    name = "cfps",
    prefix = "c",
    fields = List("id", "group_id", "slug", "name", "begin", "close", "description", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("close", "close date", Field("-close", "c"), Field("name", "c")),
    search = List("id", "slug", "name", "description", "tags"),
    filters = List()).get

  val partners: Table = Table.from(
    name = "partners",
    prefix = "pa",
    fields = List("id", "group_id", "slug", "name", "notes", "description", "logo") ++ socialFields ++ List("created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("name", "pa"),
    search = List("id", "slug", "name", "notes", "description"),
    filters = List()).get

  val contacts: Table = Table.from(
    name = "contacts",
    prefix = "ct",
    fields = List("id", "partner_id", "first_name", "last_name", "email", "notes", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("name", Field("last_name", "ct"), Field("first_name", "ct")),
    search = List("id", "first_name", "last_name", "email"),
    filters = List()).get

  val venues: Table = Table.from(
    name = "venues",
    prefix = "v",
    fields = List("id", "partner_id", "contact_id", "address", "address_id", "address_lat", "address_lng", "address_locality", "address_country", "notes", "room_size", "meetupGroup", "meetupVenue", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("created", Field("created_at", "v")),
    search = List("id", "address", "notes"),
    filters = List()).get

  val events: Table = Table.from(
    name = "events",
    prefix = "e",
    fields = List("id", "group_id", "cfp_id", "slug", "name", "kind", "start", "max_attendee", "allow_rsvp", "description", "orga_notes", "orga_notes_updated_at", "orga_notes_updated_by", "venue", "talks", "tags", "published", "meetupGroup", "meetupEvent", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("start", Field("-start", "e")),
    search = List("id", "slug", "name", "description", "tags"),
    filters = List()).get

  val eventRsvps: Table = Table.from(
    name = "event_rsvps",
    prefix = "er",
    fields = List("event_id", "user_id", "answer", "answered_at"),
    sort = Table.Sort("answered", "answer date", Field("answered_at", "er")),
    search = List("answer"),
    filters = List()).get

  val proposals: Table = Table.from(
    name = "proposals",
    prefix = "p",
    fields = List("id", "talk_id", "cfp_id", "event_id", "status", "title", "duration", "description", "message", "speakers", "slides", "video", "tags", "orga_tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("created", Field("-created_at", "p")),
    search = List("id", "title", "status", "description", "message", "tags"),
    filters = List()).get

  val proposalRatings: Table = Table.from(
    name = "proposal_ratings",
    prefix = "pr",
    fields = List("proposal_id", "grade", "created_at", "created_by"),
    sort = Table.Sort("created", Field("created_at", "pr")),
    search = List("proposal_id", "grade", "created_by"),
    filters = List()).get

  val sponsorPacks: Table = Table.from(
    name = "sponsor_packs",
    prefix = "sp",
    fields = List("id", "group_id", "slug", "name", "description", "price", "currency", "duration", "active", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("price", Field("-active", "sp"), Field("-price", "sp")),
    search = List("id", "slug", "name", "description"),
    filters = List()).get

  val sponsors: Table = Table.from(
    name = "sponsors",
    prefix = "s",
    fields = List("id", "group_id", "partner_id", "sponsor_pack_id", "contact_id", "start", "finish", "paid", "price", "currency", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("start", "start date", Field("-start", "s")),
    search = List("id"),
    filters = List()).get

  val comments: Table = Table.from(
    name = "comments",
    prefix = "co",
    fields = List("event_id", "proposal_id", "id", "kind", "answers", "text", "created_at", "created_by"),
    sort = Table.Sort("created", Field("created_at", "co")),
    search = List("id", "kind", "answers", "text", "created_by"),
    filters = List()).get

  val userRequests: Table = Table.from(
    name = "user_requests",
    prefix = "ur",
    fields = List("id", "kind", "group_id", "cfp_id", "event_id", "talk_id", "proposal_id", "external_event_id", "external_cfp_id", "external_proposal_id", "email", "payload", "deadline", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by"),
    sort = Table.Sort("created", Field("-created_at", "ur")),
    search = List("id", "email", "group_id", "created_by"),
    filters = List()).get

  val externalEvents: Table = Table.from(
    name = "external_events",
    prefix = "ee",
    fields = List("id", "name", "kind", "logo", "description", "start", "finish") ++ locationFields ++ List("url", "tickets_url", "videos_url", "twitter_account", "twitter_hashtag", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("start", Field("-start", "ee"), Field("name", "ee")),
    search = List("id", "name", "description", "location", "url", "twitter_account", "twitter_hashtag", "tags"),
    filters = List()).get

  val externalCfps: Table = Table.from(
    name = "external_cfps",
    prefix = "ec",
    fields = List("id", "event_id", "description", "begin", "close", "url", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("close", "ec"),
    search = List("id", "description", "url"),
    filters = List()).get

  val externalProposals: Table = Table.from(
    name = "external_proposals",
    prefix = "ep",
    fields = List("id", "talk_id", "event_id", "status", "title", "duration", "description", "message", "speakers", "slides", "video", "url", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Table.Sort("title", Field("title", "ep"), Field("created_at", "ep")),
    search = List("id", "title", "status", "description", "message", "tags"),
    filters = List()).get

  val videos: Table = Table.from(
    name = "videos",
    prefix = "vi",
    fields = List("platform", "url", "id", "channel_id", "channel_name", "playlist_id", "playlist_name", "title", "description", "tags", "published_at", "duration", "lang", "views", "likes", "dislikes", "comments", "updated_at"),
    sort = Table.Sort("title", Field("title", "vi"), Field("published_at", "vi")),
    search = List("id", "channel_name", "playlist_name", "title", "description", "tags"),
    filters = List()).get

  val videoSources: Table = Table.from(
    name = "video_sources",
    prefix = "vis",
    fields = List("video_id", "talk_id", "proposal_id", "external_proposal_id", "external_event_id"),
    sort = Table.Sort("video", Field("video_id", "vis")),
    search = List("video_id"),
    filters = List()).get

  val all: List[Table] = List(users, credentials, logins, talks, groups, groupSettings, groupMembers, cfps, partners, contacts, venues, events, eventRsvps, proposals, proposalRatings, sponsorPacks, sponsors, comments, userRequests, externalEvents, externalCfps, externalProposals, videos, videoSources)
}
