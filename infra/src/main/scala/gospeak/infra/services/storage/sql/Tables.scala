package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Sort, Table}
import gospeak.libs.scala.Extensions._

object Tables {
  private val socialFields = Seq("facebook", "instagram", "twitter", "linkedIn", "youtube", "meetup", "eventbrite", "slack", "discord", "github").map("social_" + _)
  private val locationFields = Seq("location", "location_id", "location_lat", "location_lng", "location_locality", "location_country")

  val users: Table = Table.from(
    name = "users",
    prefix = "u",
    fields = Seq("id", "slug", "status", "first_name", "last_name", "email", "email_validated", "email_validation_before_login", "avatar", "title", "bio", "mentoring", "company", "location", "phone", "website") ++ socialFields ++ Seq("created_at", "updated_at"),
    sort = Sort("name", Field("last_name", "u"), Field("first_name", "u")),
    search = Seq("id", "slug", "first_name", "last_name", "email", "title", "bio", "mentoring"),
    filters = Seq()).get

  val credentials: Table = Table.from(
    name = "credentials",
    prefix = "cd",
    fields = Seq("provider_id", "provider_key", "hasher", "password", "salt"),
    sort = Sort("provider", Field("provider_id", "cd"), Field("provider_key", "cd")),
    search = Seq(),
    filters = Seq()).get

  val logins: Table = Table.from(
    name = "logins",
    prefix = "lg",
    fields = Seq("provider_id", "provider_key", "user_id"),
    sort = Sort("provider", Field("provider_id", "lg"), Field("provider_key", "lg")),
    search = Seq(),
    filters = Seq()).get

  val talks: Table = Table.from(
    name = "talks",
    prefix = "t",
    fields = Seq("id", "slug", "status", "title", "duration", "description", "message", "speakers", "slides", "video", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("title", Field("status = 'Archived'", "t"), Field("title", "t")),
    search = Seq("id", "slug", "status", "title", "description", "message", "tags"),
    filters = Seq()).get

  val groups: Table = Table.from(
    name = "groups",
    prefix = "g",
    fields = Seq("id", "slug", "name", "logo", "banner", "contact", "website", "description") ++ locationFields ++ Seq("owners") ++ socialFields ++ Seq("tags", "status", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("name", "g"),
    search = Seq("id", "slug", "name", "contact", "description", "location_locality", "location_country", "tags"),
    filters = Seq()).get

  val groupSettings: Table = Table.from(
    name = "group_settings",
    prefix = "gs",
    fields = Seq("group_id", "meetup_access_token", "meetup_refresh_token", "meetup_group_slug", "meetup_logged_user_id", "meetup_logged_user_name", "slack_token", "slack_bot_name", "slack_bot_avatar", "event_description", "event_templates", "proposal_tweet", "actions", "updated_at", "updated_by"),
    sort = Sort("group_id", "gs"),
    search = Seq(),
    filters = Seq()).get

  val groupMembers: Table = Table.from(
    name = "group_members",
    prefix = "gm",
    fields = Seq("group_id", "user_id", "role", "presentation", "joined_at", "leaved_at"),
    sort = Sort("joined", "join date", Field("joined_at", "gm")),
    search = Seq("role", "presentation"),
    filters = Seq()).get

  val cfps: Table = Table.from(
    name = "cfps",
    prefix = "c",
    fields = Seq("id", "group_id", "slug", "name", "begin", "close", "description", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("close", "close date", Field("-close", "c"), Field("name", "c")),
    search = Seq("id", "slug", "name", "description", "tags"),
    filters = Seq()).get

  val partners: Table = Table.from(
    name = "partners",
    prefix = "pa",
    fields = Seq("id", "group_id", "slug", "name", "notes", "description", "logo") ++ socialFields ++ Seq("created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("name", "pa"),
    search = Seq("id", "slug", "name", "notes", "description"),
    filters = Seq()).get

  val contacts: Table = Table.from(
    name = "contacts",
    prefix = "ct",
    fields = Seq("id", "partner_id", "first_name", "last_name", "email", "notes", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("name", Field("last_name", "ct"), Field("first_name", "ct")),
    search = Seq("id", "first_name", "last_name", "email"),
    filters = Seq()).get

  val venues: Table = Table.from(
    name = "venues",
    prefix = "v",
    fields = Seq("id", "partner_id", "contact_id", "address", "address_id", "address_lat", "address_lng", "address_locality", "address_country", "notes", "room_size", "meetupGroup", "meetupVenue", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("created", Field("created_at", "v")),
    search = Seq("id", "address", "notes"),
    filters = Seq()).get

  val events: Table = Table.from(
    name = "events",
    prefix = "e",
    fields = Seq("id", "group_id", "cfp_id", "slug", "name", "kind", "start", "max_attendee", "allow_rsvp", "description", "orga_notes", "orga_notes_updated_at", "orga_notes_updated_by", "venue", "talks", "tags", "published", "meetupGroup", "meetupEvent", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("start", Field("-start", "e")),
    search = Seq("id", "slug", "name", "description", "tags"),
    filters = Seq()).get

  val eventRsvps: Table = Table.from(
    name = "event_rsvps",
    prefix = "er",
    fields = Seq("event_id", "user_id", "answer", "answered_at"),
    sort = Sort("answered", "answer date", Field("answered_at", "er")),
    search = Seq("answer"),
    filters = Seq()).get

  val proposals: Table = Table.from(
    name = "proposals",
    prefix = "p",
    fields = Seq("id", "talk_id", "cfp_id", "event_id", "status", "title", "duration", "description", "message", "speakers", "slides", "video", "tags", "orga_tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("created", Field("-created_at", "p")),
    search = Seq("id", "title", "status", "description", "message", "tags"),
    filters = Seq()).get

  val proposalRatings: Table = Table.from(
    name = "proposal_ratings",
    prefix = "pr",
    fields = Seq("proposal_id", "grade", "created_at", "created_by"),
    sort = Sort("created", Field("created_at", "pr")),
    search = Seq("proposal_id", "grade", "created_by"),
    filters = Seq()).get

  val sponsorPacks: Table = Table.from(
    name = "sponsor_packs",
    prefix = "sp",
    fields = Seq("id", "group_id", "slug", "name", "description", "price", "currency", "duration", "active", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("price", Field("-active", "sp"), Field("-price", "sp")),
    search = Seq("id", "slug", "name", "description"),
    filters = Seq()).get

  val sponsors: Table = Table.from(
    name = "sponsors",
    prefix = "s",
    fields = Seq("id", "group_id", "partner_id", "sponsor_pack_id", "contact_id", "start", "finish", "paid", "price", "currency", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("start", "start date", Field("-start", "s")),
    search = Seq("id"),
    filters = Seq()).get

  val comments: Table = Table.from(
    name = "comments",
    prefix = "co",
    fields = Seq("event_id", "proposal_id", "id", "kind", "answers", "text", "created_at", "created_by"),
    sort = Sort("created", Field("created_at", "co")),
    search = Seq("id", "kind", "answers", "text", "created_by"),
    filters = Seq()).get

  val userRequests: Table = Table.from(
    name = "user_requests",
    prefix = "ur",
    fields = Seq("id", "kind", "group_id", "cfp_id", "event_id", "talk_id", "proposal_id", "external_event_id", "external_cfp_id", "external_proposal_id", "email", "payload", "deadline", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by"),
    sort = Sort("created", Field("-created_at", "ur")),
    search = Seq("id", "email", "group_id", "created_by"),
    filters = Seq()).get

  val externalEvents: Table = Table.from(
    name = "external_events",
    prefix = "ee",
    fields = Seq("id", "name", "kind", "logo", "description", "start", "finish") ++ locationFields ++ Seq("url", "tickets_url", "videos_url", "twitter_account", "twitter_hashtag", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("start", Field("-start", "ee"), Field("name", "ee")),
    search = Seq("id", "name", "description", "location", "url", "twitter_account", "twitter_hashtag", "tags"),
    filters = Seq()).get

  val externalCfps: Table = Table.from(
    name = "external_cfps",
    prefix = "ec",
    fields = Seq("id", "event_id", "description", "begin", "close", "url", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("close", "ec"),
    search = Seq("id", "description", "url"),
    filters = Seq()).get

  val externalProposals: Table = Table.from(
    name = "external_proposals",
    prefix = "ep",
    fields = Seq("id", "talk_id", "event_id", "status", "title", "duration", "description", "message", "speakers", "slides", "video", "url", "tags", "created_at", "created_by", "updated_at", "updated_by"),
    sort = Sort("title", Field("title", "ep"), Field("created_at", "ep")),
    search = Seq("id", "title", "status", "description", "message", "tags"),
    filters = Seq()).get

  val videos: Table = Table.from(
    name = "videos",
    prefix = "vi",
    fields = Seq("platform", "url", "id", "channel_id", "channel_name", "playlist_id", "playlist_name", "title", "description", "tags", "published_at", "duration", "lang", "views", "likes", "dislikes", "comments", "updated_at"),
    sort = Sort("title", Field("title", "vi"), Field("published_at", "vi")),
    search = Seq("id", "channel_name", "playlist_name", "title", "description", "tags"),
    filters = Seq()).get

  val all: Seq[Table] = Seq(users, credentials, logins, talks, groups, groupSettings, groupMembers, cfps, partners, contacts, venues, events, eventRsvps, proposals, proposalRatings, sponsorPacks, sponsors, comments, userRequests, externalEvents, externalCfps, externalProposals, videos)
}
