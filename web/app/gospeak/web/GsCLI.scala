package gospeak.web

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.IO
import gospeak.core.services.storage.DbConf
import gospeak.infra.services.storage.sql.utils.DbConnection
import gospeak.libs.scala.FileUtils
import gospeak.libs.sql.generator.Generator
import gospeak.libs.sql.generator.reader.H2Reader
import gospeak.libs.sql.generator.writer.ScalaWriter.{DatabaseConfig, FieldConfig, SchemaConfig, TableConfig}
import gospeak.libs.sql.generator.writer.{ScalaWriter, Writer}

import scala.util.Try

/**
 * A CLI to perform common tasks
 */
object GsCLI {
  def main(args: Array[String]): Unit = {
    // TODO
    GenerateTables.run().get
    println("done")
  }

  object GenerateTables {
    private[web] val reader = new H2Reader(
      schema = Some("PUBLIC"),
      excludes = Some(".*flyway.*"))
    private[web] val writer = new ScalaWriter(
      directory = FileUtils.adaptLocalPath("infra/src/main/scala"),
      packageName = "gospeak.infra.services.storage.sql.database",
      identifierStrategy = Writer.IdentifierStrategy.upperCase,
      config = DatabaseConfig(
        scaladoc = _ => Some(
          """Generated file, do not update it!
            |
            |Regenerate it using Gospeak CLI (`gospeak.web.GsCLI` class) to keep it in sync with the database state.
            |
            |--
            |""".stripMargin),
        imports = List(
          "gospeak.libs.scala.domain._",
          "gospeak.core.domain._",
          "gospeak.core.domain.utils.SocialAccounts.SocialAccount._",
          "gospeak.core.domain.messages.Message",
          "gospeak.core.services.meetup.domain.MeetupEvent",
          "gospeak.core.services.meetup.domain.MeetupGroup",
          "gospeak.core.services.meetup.domain.MeetupUser",
          "gospeak.core.services.meetup.domain.MeetupVenue",
          "gospeak.core.services.slack.domain.SlackToken",
          "cats.data.NonEmptyList",
          "java.time.LocalDateTime",
          "scala.concurrent.duration.FiniteDuration"),
        schemas = Map("PUBLIC" -> SchemaConfig(tables = Map(
          "users" -> TableConfig(alias = "u", sort = TableConfig.Sort("name", NonEmptyList.of("last_name", "first_name")), search = List("id", "slug", "first_name", "last_name", "email", "title", "bio", "mentoring"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "User.Id"),
            "slug" -> FieldConfig(index = 1, customType = "User.Slug"),
            "status" -> FieldConfig(index = 2, customType = "User.Status"),
            "first_name" -> FieldConfig(index = 3),
            "last_name" -> FieldConfig(index = 4),
            "email" -> FieldConfig(index = 5, customType = "EmailAddress"),
            "email_validated" -> FieldConfig(index = 6),
            "email_validation_before_login" -> FieldConfig(index = 7),
            "avatar" -> FieldConfig(index = 8, customType = "Avatar"),
            "title" -> FieldConfig(index = 9),
            "bio" -> FieldConfig(index = 10, customType = "Markdown"),
            "mentoring" -> FieldConfig(index = 11, customType = "Markdown"),
            "company" -> FieldConfig(index = 12),
            "location" -> FieldConfig(index = 13),
            "phone" -> FieldConfig(index = 14),
            "website" -> FieldConfig(index = 15, customType = "Url"),
            "social_facebook" -> FieldConfig(index = 16, customType = "FacebookAccount"),
            "social_instagram" -> FieldConfig(index = 17, customType = "InstagramAccount"),
            "social_twitter" -> FieldConfig(index = 18, customType = "TwitterAccount"),
            "social_linkedIn" -> FieldConfig(index = 19, customType = "LinkedInAccount"),
            "social_youtube" -> FieldConfig(index = 20, customType = "YoutubeAccount"),
            "social_meetup" -> FieldConfig(index = 21, customType = "MeetupAccount"),
            "social_eventbrite" -> FieldConfig(index = 22, customType = "EventbriteAccount"),
            "social_slack" -> FieldConfig(index = 23, customType = "SlackAccount"),
            "social_discord" -> FieldConfig(index = 24, customType = "DiscordAccount"),
            "social_github" -> FieldConfig(index = 25, customType = "GithubAccount"),
            "created_at" -> FieldConfig(index = 26),
            "updated_at" -> FieldConfig(index = 27))),
          "credentials" -> TableConfig(alias = "cd", sort = TableConfig.Sort("provider", NonEmptyList.of("provider_id", "provider_key")), fields = Map(
            "provider_id" -> FieldConfig(customType = "User.ProviderId"),
            "provider_key" -> FieldConfig(customType = "User.ProviderKey"),
            "hasher" -> FieldConfig(customType = "User.Hasher"),
            "password" -> FieldConfig(customType = "User.PasswordValue"),
            "salt" -> FieldConfig(customType = "User.Salt"))),
          "logins" -> TableConfig(alias = "lg", fields = Map(
            "provider_id" -> FieldConfig(customType = "User.ProviderId"),
            "provider_key" -> FieldConfig(customType = "User.ProviderKey"),
            "user_id" -> FieldConfig(customType = "User.Id"))),
          "talks" -> TableConfig(alias = "t", sort = TableConfig.Sort("title", "title", NonEmptyList.of(TableConfig.Sort.Field("status", "? = 'Archived'"), TableConfig.Sort.Field("title"))), search = List("id", "slug", "status", "title", "description", "message", "tags"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "Talk.Id"),
            "slug" -> FieldConfig(index = 1, customType = "Talk.Slug"),
            "status" -> FieldConfig(index = 2, customType = "Talk.Status"),
            "title" -> FieldConfig(index = 3, customType = "Talk.Title"),
            "duration" -> FieldConfig(index = 4, customType = "FiniteDuration"),
            "description" -> FieldConfig(index = 5, customType = "Markdown"),
            "message" -> FieldConfig(index = 6, customType = "Markdown"),
            "speakers" -> FieldConfig(index = 7, customType = "NonEmptyList[User.Id]"),
            "slides" -> FieldConfig(index = 8, customType = "Url.Slides"),
            "video" -> FieldConfig(index = 9, customType = "Url.Video"),
            "tags" -> FieldConfig(index = 10, customType = "List[Tag]"),
            "created_at" -> FieldConfig(index = 11),
            "created_by" -> FieldConfig(index = 12),
            "updated_at" -> FieldConfig(index = 13),
            "updated_by" -> FieldConfig(index = 14))),
          "groups" -> TableConfig(alias = "g", sort = "name", search = List("id", "slug", "name", "contact", "description", "location_locality", "location_country", "tags"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "Group.Id"),
            "slug" -> FieldConfig(index = 1, customType = "Group.Slug"),
            "name" -> FieldConfig(index = 2, customType = "Group.Name"),
            "logo" -> FieldConfig(index = 3, customType = "Logo"),
            "banner" -> FieldConfig(index = 4, customType = "Banner"),
            "contact" -> FieldConfig(index = 5, customType = "EmailAddress"),
            "website" -> FieldConfig(index = 6, customType = "Url"),
            "description" -> FieldConfig(index = 7, customType = "Markdown"),
            "location" -> FieldConfig(index = 8, customType = "GMapPlace"),
            "location_id" -> FieldConfig(index = 9),
            "location_lat" -> FieldConfig(index = 10),
            "location_lng" -> FieldConfig(index = 11),
            "location_locality" -> FieldConfig(index = 12),
            "location_country" -> FieldConfig(index = 13),
            "owners" -> FieldConfig(index = 14, customType = "NonEmptyList[User.Id]"),
            "social_facebook" -> FieldConfig(index = 15, customType = "FacebookAccount"),
            "social_instagram" -> FieldConfig(index = 16, customType = "InstagramAccount"),
            "social_twitter" -> FieldConfig(index = 17, customType = "TwitterAccount"),
            "social_linkedIn" -> FieldConfig(index = 18, customType = "LinkedInAccount"),
            "social_youtube" -> FieldConfig(index = 19, customType = "YoutubeAccount"),
            "social_meetup" -> FieldConfig(index = 20, customType = "MeetupAccount"),
            "social_eventbrite" -> FieldConfig(index = 21, customType = "EventbriteAccount"),
            "social_slack" -> FieldConfig(index = 22, customType = "SlackAccount"),
            "social_discord" -> FieldConfig(index = 23, customType = "DiscordAccount"),
            "social_github" -> FieldConfig(index = 24, customType = "GithubAccount"),
            "tags" -> FieldConfig(index = 25, customType = "List[Tag]"),
            "status" -> FieldConfig(index = 26, customType = "Group.Status"),
            "created_at" -> FieldConfig(index = 27),
            "created_by" -> FieldConfig(index = 28),
            "updated_at" -> FieldConfig(index = 29),
            "updated_by" -> FieldConfig(index = 30))),
          "group_settings" -> TableConfig(alias = "gs", sort = "group_id", fields = Map(
            "group_id" -> FieldConfig(index = 0),
            "meetup_access_token" -> FieldConfig(index = 1, customType = "Crypted"),
            "meetup_refresh_token" -> FieldConfig(index = 2, customType = "Crypted"),
            "meetup_group_slug" -> FieldConfig(index = 3, customType = "MeetupGroup.Slug"),
            "meetup_logged_user_id" -> FieldConfig(index = 4, customType = "MeetupUser.Id"),
            "meetup_logged_user_name" -> FieldConfig(index = 5),
            "slack_token" -> FieldConfig(index = 6, customType = "SlackToken"),
            "slack_bot_name" -> FieldConfig(index = 7),
            "slack_bot_avatar" -> FieldConfig(index = 8, customType = "Avatar"),
            "event_description" -> FieldConfig(index = 9, customType = "LiquidMarkdown[Message.EventInfo]"),
            "event_templates" -> FieldConfig(index = 10, customType = "Map[String, Liquid[Message.EventInfo]]"),
            "proposal_tweet" -> FieldConfig(index = 11, customType = "Liquid[Message.ProposalInfo]"),
            "actions" -> FieldConfig(index = 12, customType = "Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]"),
            "updated_at" -> FieldConfig(index = 13),
            "updated_by" -> FieldConfig(index = 14))),
          "group_members" -> TableConfig(alias = "gm", sort = TableConfig.Sort("joined", "join date", "joined_at"), search = List("role", "presentation")),
          "cfps" -> TableConfig(alias = "c", sort = TableConfig.Sort("date", NonEmptyList.of("-close", "name")), search = List("id", "slug", "name", "description", "tags"), fields = Map(
            "id" -> FieldConfig(customType = "Cfp.Id"),
            "slug" -> FieldConfig(customType = "Cfp.Slug"),
            "name" -> FieldConfig(customType = "Cfp.Name"),
            "begin" -> FieldConfig(customType = "LocalDateTime"),
            "close" -> FieldConfig(customType = "LocalDateTime"),
            "description" -> FieldConfig(customType = "Markdown"),
            "tags" -> FieldConfig(customType = "List[Tag]"))),
          "partners" -> TableConfig(alias = "pa", sort = "name", search = List("id", "slug", "name", "notes", "description"), fields = Map(
            "id" -> FieldConfig(customType = "Partner.Id"),
            "slug" -> FieldConfig(customType = "Partner.Slug"),
            "name" -> FieldConfig(customType = "Partner.Name"),
            "notes" -> FieldConfig(customType = "Markdown"),
            "description" -> FieldConfig(customType = "Markdown"),
            "logo" -> FieldConfig(customType = "Logo"),
            "social_facebook" -> FieldConfig(customType = "FacebookAccount"),
            "social_instagram" -> FieldConfig(customType = "InstagramAccount"),
            "social_twitter" -> FieldConfig(customType = "TwitterAccount"),
            "social_linkedIn" -> FieldConfig(customType = "LinkedInAccount"),
            "social_youtube" -> FieldConfig(customType = "YoutubeAccount"),
            "social_meetup" -> FieldConfig(customType = "MeetupAccount"),
            "social_eventbrite" -> FieldConfig(customType = "EventbriteAccount"),
            "social_slack" -> FieldConfig(customType = "SlackAccount"),
            "social_discord" -> FieldConfig(customType = "DiscordAccount"),
            "social_github" -> FieldConfig(customType = "GithubAccount"))),
          "contacts" -> TableConfig(alias = "ct", sort = TableConfig.Sort("name", NonEmptyList.of("last_name", "first_name")), search = List("id", "first_name", "last_name", "email"), fields = Map(
            "id" -> FieldConfig(customType = "Contact.Id"),
            "first_name" -> FieldConfig(customType = "Contact.FirstName"),
            "last_name" -> FieldConfig(customType = "Contact.LastName"),
            "email" -> FieldConfig(customType = "EmailAddress"),
            "notes" -> FieldConfig(customType = "Markdown"))),
          "venues" -> TableConfig(alias = "v", sort = TableConfig.Sort("created", "created_at"), search = List("id", "address", "notes"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "Venue.Id"),
            "partner_id" -> FieldConfig(index = 1),
            "contact_id" -> FieldConfig(index = 2),
            "address" -> FieldConfig(index = 3, customType = "GMapPlace"),
            "address_id" -> FieldConfig(index = 4),
            "address_lat" -> FieldConfig(index = 5),
            "address_lng" -> FieldConfig(index = 6),
            "address_locality" -> FieldConfig(index = 7),
            "address_country" -> FieldConfig(index = 8),
            "notes" -> FieldConfig(index = 9, customType = "Markdown"),
            "room_size" -> FieldConfig(index = 10),
            "meetupGroup" -> FieldConfig(index = 11, customType = "MeetupGroup.Slug"),
            "meetupVenue" -> FieldConfig(index = 12, customType = "MeetupVenue.Id"),
            "created_at" -> FieldConfig(index = 13),
            "created_by" -> FieldConfig(index = 14),
            "updated_at" -> FieldConfig(index = 15),
            "updated_by" -> FieldConfig(index = 16))),
          "events" -> TableConfig(alias = "e", sort = TableConfig.Sort("start", "-start"), search = List("id", "slug", "name", "description", "tags"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "Event.Id"),
            "group_id" -> FieldConfig(index = 1),
            "cfp_id" -> FieldConfig(index = 2),
            "slug" -> FieldConfig(index = 3, customType = "Event.Slug"),
            "name" -> FieldConfig(index = 4, customType = "Event.Name"),
            "kind" -> FieldConfig(index = 5, customType = "Event.Kind"),
            "start" -> FieldConfig(index = 6, customType = "LocalDateTime"),
            "max_attendee" -> FieldConfig(index = 7),
            "allow_rsvp" -> FieldConfig(index = 8),
            "description" -> FieldConfig(index = 9, customType = "LiquidMarkdown[Message.EventInfo]"),
            "orga_notes" -> FieldConfig(index = 10),
            "orga_notes_updated_at" -> FieldConfig(index = 11),
            "orga_notes_updated_by" -> FieldConfig(index = 12),
            "venue" -> FieldConfig(index = 13),
            "talks" -> FieldConfig(index = 14, customType = "List[Proposal.Id]"),
            "tags" -> FieldConfig(index = 15, customType = "List[Tag]"),
            "published" -> FieldConfig(index = 16),
            "meetupGroup" -> FieldConfig(index = 17, customType = "MeetupGroup.Slug"),
            "meetupEvent" -> FieldConfig(index = 18, customType = "MeetupEvent.Id"),
            "created_at" -> FieldConfig(index = 19),
            "created_by" -> FieldConfig(index = 20),
            "updated_at" -> FieldConfig(index = 21),
            "updated_by" -> FieldConfig(index = 22))),
          "event_rsvps" -> TableConfig(alias = "er", sort = TableConfig.Sort("answered", "answer date", "answered_at"), search = List("answer"), fields = Map(
            "answer" -> FieldConfig(customType = "Event.Rsvp.Answer"))),
          "proposals" -> TableConfig(alias = "p", sort = TableConfig.Sort("created", "-created_at"), search = List("id", "title", "status", "description", "message", "tags"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "Proposal.Id"),
            "talk_id" -> FieldConfig(index = 1),
            "cfp_id" -> FieldConfig(index = 2),
            "event_id" -> FieldConfig(index = 3),
            "status" -> FieldConfig(index = 4, customType = "Proposal.Status"),
            "title" -> FieldConfig(index = 5, customType = "Talk.Title"),
            "duration" -> FieldConfig(index = 6, customType = "FiniteDuration"),
            "description" -> FieldConfig(index = 7, customType = "Markdown"),
            "message" -> FieldConfig(index = 8, customType = "Markdown"),
            "speakers" -> FieldConfig(index = 9, customType = "NonEmptyList[User.Id]"),
            "slides" -> FieldConfig(index = 10, customType = "Url.Slides"),
            "video" -> FieldConfig(index = 11, customType = "Url.Video"),
            "tags" -> FieldConfig(index = 12, customType = "List[Tag]"),
            "orga_tags" -> FieldConfig(index = 13, customType = "List[Tag]"),
            "created_at" -> FieldConfig(index = 14),
            "created_by" -> FieldConfig(index = 15),
            "updated_at" -> FieldConfig(index = 16),
            "updated_by" -> FieldConfig(index = 17))),
          "proposal_ratings" -> TableConfig(alias = "pr", sort = TableConfig.Sort("created", "created_at"), search = List("proposal_id", "grade", "created_by"), fields = Map(
            "grade" -> FieldConfig(customType = "Proposal.Rating.Grade"))),
          "sponsor_packs" -> TableConfig(alias = "sp", sort = TableConfig.Sort("price", NonEmptyList.of("-active", "-price")), search = List("id", "slug", "name", "description"), fields = Map(
            "id" -> FieldConfig(customType = "SponsorPack.Id"),
            "slug" -> FieldConfig(customType = "SponsorPack.Slug"),
            "name" -> FieldConfig(customType = "SponsorPack.Name"),
            "description" -> FieldConfig(customType = "Markdown"),
            "currency" -> FieldConfig(customType = "Price.Currency"),
            "duration" -> FieldConfig(customType = "TimePeriod"))),
          "sponsors" -> TableConfig(alias = "s", sort = TableConfig.Sort("start", "start date", "-start"), search = List("id"), fields = Map(
            "id" -> FieldConfig(customType = "Sponsor.Id"),
            "currency" -> FieldConfig(customType = "Price.Currency"))),
          "comments" -> TableConfig(alias = "co", sort = TableConfig.Sort("created", "created_at"), search = List("id", "kind", "answers", "text", "created_by"), fields = Map(
            "id" -> FieldConfig(customType = "Comment.Id"),
            "kind" -> FieldConfig(customType = "Comment.Kind"))),
          "user_requests" -> TableConfig(alias = "ur", sort = TableConfig.Sort("created", "-created_at"), search = List("id", "email", "group_id", "created_by"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "UserRequest.Id"),
            "kind" -> FieldConfig(index = 1),
            "group_id" -> FieldConfig(index = 2),
            "cfp_id" -> FieldConfig(index = 3),
            "event_id" -> FieldConfig(index = 4),
            "talk_id" -> FieldConfig(index = 5),
            "proposal_id" -> FieldConfig(index = 6),
            "external_event_id" -> FieldConfig(index = 7),
            "external_cfp_id" -> FieldConfig(index = 8),
            "external_proposal_id" -> FieldConfig(index = 9),
            "email" -> FieldConfig(index = 10, customType = "EmailAddress"),
            "payload" -> FieldConfig(index = 11),
            "deadline" -> FieldConfig(index = 12),
            "created_at" -> FieldConfig(index = 13),
            "created_by" -> FieldConfig(index = 14),
            "accepted_at" -> FieldConfig(index = 15),
            "accepted_by" -> FieldConfig(index = 16),
            "rejected_at" -> FieldConfig(index = 17),
            "rejected_by" -> FieldConfig(index = 18),
            "canceled_at" -> FieldConfig(index = 19),
            "canceled_by" -> FieldConfig(index = 20))),
          "external_events" -> TableConfig(alias = "ee", sort = TableConfig.Sort("start", NonEmptyList.of("-start", "name")), search = List("id", "name", "description", "location", "url", "twitter_account", "twitter_hashtag", "tags"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "ExternalEvent.Id"),
            "name" -> FieldConfig(index = 1, customType = "Event.Name"),
            "kind" -> FieldConfig(index = 2, customType = "Event.Kind"),
            "logo" -> FieldConfig(index = 3, customType = "Logo"),
            "description" -> FieldConfig(index = 4, customType = "Markdown"),
            "start" -> FieldConfig(index = 5, customType = "LocalDateTime"),
            "finish" -> FieldConfig(index = 6, customType = "LocalDateTime"),
            "location" -> FieldConfig(index = 7, customType = "GMapPlace"),
            "location_id" -> FieldConfig(index = 8),
            "location_lat" -> FieldConfig(index = 9),
            "location_lng" -> FieldConfig(index = 10),
            "location_locality" -> FieldConfig(index = 11),
            "location_country" -> FieldConfig(index = 12),
            "url" -> FieldConfig(index = 13, customType = "Url"),
            "tickets_url" -> FieldConfig(index = 14, customType = "Url"),
            "videos_url" -> FieldConfig(index = 15, customType = "Url.Videos"),
            "twitter_account" -> FieldConfig(index = 16, customType = "TwitterAccount"),
            "twitter_hashtag" -> FieldConfig(index = 17, customType = "TwitterHashtag"),
            "tags" -> FieldConfig(index = 18, customType = "List[Tag]"),
            "created_at" -> FieldConfig(index = 19),
            "created_by" -> FieldConfig(index = 20),
            "updated_at" -> FieldConfig(index = 21),
            "updated_by" -> FieldConfig(index = 22))),
          "external_cfps" -> TableConfig(alias = "ec", sort = "close", search = List("id", "description", "url"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "ExternalCfp.Id"),
            "event_id" -> FieldConfig(index = 1),
            "description" -> FieldConfig(index = 2, customType = "Markdown"),
            "begin" -> FieldConfig(index = 3, customType = "LocalDateTime"),
            "close" -> FieldConfig(index = 4, customType = "LocalDateTime"),
            "url" -> FieldConfig(index = 5, customType = "Url"),
            "created_at" -> FieldConfig(index = 6),
            "created_by" -> FieldConfig(index = 7),
            "updated_at" -> FieldConfig(index = 8),
            "updated_by" -> FieldConfig(index = 9))),
          "external_proposals" -> TableConfig(alias = "ep", sort = TableConfig.Sort("title", NonEmptyList.of("title", "created_at")), search = List("id", "title", "status", "description", "message", "tags"), fields = Map(
            "id" -> FieldConfig(index = 0, customType = "ExternalProposal.Id"),
            "talk_id" -> FieldConfig(index = 1),
            "event_id" -> FieldConfig(index = 2),
            "status" -> FieldConfig(index = 3, customType = "Proposal.Status"),
            "title" -> FieldConfig(index = 4, customType = "Talk.Title"),
            "duration" -> FieldConfig(index = 5, customType = "FiniteDuration"),
            "description" -> FieldConfig(index = 6, customType = "Markdown"),
            "message" -> FieldConfig(index = 7, customType = "Markdown"),
            "speakers" -> FieldConfig(index = 8, customType = "NonEmptyList[User.Id]"),
            "slides" -> FieldConfig(index = 9, customType = "Url.Slides"),
            "video" -> FieldConfig(index = 10, customType = "Url.Video"),
            "url" -> FieldConfig(index = 11, customType = "Url"),
            "tags" -> FieldConfig(index = 12, customType = "List[Tag]"),
            "created_at" -> FieldConfig(index = 13),
            "created_by" -> FieldConfig(index = 14),
            "updated_at" -> FieldConfig(index = 15),
            "updated_by" -> FieldConfig(index = 16))),
          "videos" -> TableConfig(alias = "vi", sort = TableConfig.Sort("title", NonEmptyList.of("title", "published_at")), search = List("id", "channel_name", "playlist_name", "title", "description", "tags"), fields = Map(
            "url" -> FieldConfig(customType = "Url.Video"),
            "id" -> FieldConfig(customType = "Url.Video.Id"),
            "channel_id" -> FieldConfig(customType = "Url.Videos.Channel.Id"),
            "playlist_id" -> FieldConfig(customType = "Url.Videos.Playlist.Id"),
            "tags" -> FieldConfig(customType = "List[Tag]"),
            "duration" -> FieldConfig(customType = "FiniteDuration"))),
          "video_sources" -> TableConfig(alias = "vis", sort = TableConfig.Sort("video", "video_id"), search = List("video_id"))
        )))))

    def run(): Try[Unit] = {
      val xa = init()
      Try(Generator.generate(xa, reader, writer).unsafeRunSync())
    }

    def init(): doobie.Transactor[IO] = {
      val dbConf = DbConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
      val (xa, flyway) = DbConnection.create(dbConf)
      flyway.migrate()
      xa
    }
  }

}
