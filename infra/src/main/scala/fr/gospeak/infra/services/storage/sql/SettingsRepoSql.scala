package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.meetup.domain.{MeetupCredentials, MeetupGroup, MeetupUser}
import fr.gospeak.core.services.slack.domain.{SlackAction, SlackCredentials, SlackToken}
import fr.gospeak.core.services.storage.SettingsRepo
import fr.gospeak.infra.services.storage.sql.SettingsRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}
import fr.gospeak.libs.scalautils.domain.{Crypted, Done, Url}
import io.circe._
import io.circe.generic.semiauto._

class SettingsRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SettingsRepo {
  private implicit val urlDecoder: Decoder[Url] = (c: HCursor) => c.as[String].flatMap(s => Url.from(s).leftMap(e => DecodingFailure(e.message, List())))
  private implicit val urlEncoder: Encoder[Url] = (a: Url) => Json.fromString(a.value)
  private implicit def textTemplateDecoder[A]: Decoder[MustacheTextTmpl[A]] = deriveDecoder[MustacheTextTmpl[A]]
  private implicit def textTemplateEncoder[A]: Encoder[MustacheTextTmpl[A]] = deriveEncoder[MustacheTextTmpl[A]]
  private implicit def markdownTemplateDecoder[A]: Decoder[MustacheMarkdownTmpl[A]] = deriveDecoder[MustacheMarkdownTmpl[A]]
  private implicit def markdownTemplateEncoder[A]: Encoder[MustacheMarkdownTmpl[A]] = deriveEncoder[MustacheMarkdownTmpl[A]]
  private implicit val cryptedDecoder: Decoder[Crypted] = (c: HCursor) => c.as[String].map(Crypted(_))
  private implicit val cryptedEncoder: Encoder[Crypted] = (a: Crypted) => Json.fromString(a.value)
  private implicit val meetupGroupSlugDecoder: Decoder[MeetupGroup.Slug] = (c: HCursor) => c.as[String].flatMap(s => MeetupGroup.Slug.from(s).leftMap(e => DecodingFailure(e.message, List())))
  private implicit val meetupGroupSlugEncoder: Encoder[MeetupGroup.Slug] = (a: MeetupGroup.Slug) => Json.fromString(a.value)
  private implicit val meetupUserIdDecoder: Decoder[MeetupUser.Id] = deriveDecoder[MeetupUser.Id]
  private implicit val meetupUserIdEncoder: Encoder[MeetupUser.Id] = deriveEncoder[MeetupUser.Id]
  private implicit val meetupCredentialsDecoder: Decoder[MeetupCredentials] = deriveDecoder[MeetupCredentials]
  private implicit val meetupCredentialsEncoder: Encoder[MeetupCredentials] = deriveEncoder[MeetupCredentials]
  private implicit val slackTokenDecoder: Decoder[SlackToken] = deriveDecoder[SlackToken]
  private implicit val slackTokenEncoder: Encoder[SlackToken] = deriveEncoder[SlackToken]
  private implicit val slackCredentialsDecoder: Decoder[SlackCredentials] = deriveDecoder[SlackCredentials]
  private implicit val slackCredentialsEncoder: Encoder[SlackCredentials] = deriveEncoder[SlackCredentials]
  private implicit val slackActionPostMessageDecoder: Decoder[SlackAction.PostMessage] = deriveDecoder[SlackAction.PostMessage]
  private implicit val slackActionPostMessageEncoder: Encoder[SlackAction.PostMessage] = deriveEncoder[SlackAction.PostMessage]
  private implicit val slackActionDecoder: Decoder[SlackAction] = deriveDecoder[SlackAction]
  private implicit val slackActionEncoder: Encoder[SlackAction] = deriveEncoder[SlackAction]
  private implicit val groupSettingsActionSlackDecoder: Decoder[Group.Settings.Action.Slack] = deriveDecoder[Group.Settings.Action.Slack]
  private implicit val groupSettingsActionSlackEncoder: Encoder[Group.Settings.Action.Slack] = deriveEncoder[Group.Settings.Action.Slack]
  private implicit val groupSettingsActionDecoder: Decoder[Group.Settings.Action] = deriveDecoder[Group.Settings.Action]
  private implicit val groupSettingsActionEncoder: Encoder[Group.Settings.Action] = deriveEncoder[Group.Settings.Action]
  private implicit val groupSettingsActionTriggerDecoder: KeyDecoder[Group.Settings.Action.Trigger] = (key: String) => Group.Settings.Action.Trigger.from(key)
  private implicit val groupSettingsActionTriggerEncoder: KeyEncoder[Group.Settings.Action.Trigger] = (e: Group.Settings.Action.Trigger) => e.toString
  private implicit val groupSettingsAccountsDecoder: Decoder[Group.Settings.Accounts] = deriveDecoder[Group.Settings.Accounts]
  private implicit val groupSettingsAccountsEncoder: Encoder[Group.Settings.Accounts] = deriveEncoder[Group.Settings.Accounts]
  private implicit val groupSettingsEventDecoder: Decoder[Group.Settings.Event] = deriveDecoder[Group.Settings.Event]
  private implicit val groupSettingsEventEncoder: Encoder[Group.Settings.Event] = deriveEncoder[Group.Settings.Event]
  private implicit val groupSettingsDecoder: Decoder[Group.Settings] = deriveDecoder[Group.Settings]
  private implicit val groupSettingsEncoder: Encoder[Group.Settings] = deriveEncoder[Group.Settings]

  override def find(group: Group.Id): IO[Group.Settings] =
    run(selectOne(group).option)
      .flatMap(_.map(parser.parse(_).toTry.flatMap(groupSettingsDecoder.decodeJson(_).toTry)).sequence.toIO)
      .map(_.getOrElse(Group.Settings.default))

  override def set(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): IO[Done] = {
    run(selectOne(group).option).flatMap { opt =>
      opt.map { _ =>
        run(update(group, groupSettingsEncoder.apply(settings).noSpaces, by, now))
      }.getOrElse {
        run(insert(group, groupSettingsEncoder.apply(settings).noSpaces, by, now))
      }
    }
  }
}

object SettingsRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "settings"
  private val fields = Seq("target", "target_id", "value", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val groupTarget = "group"

  private[sql] def insert(group: Group.Id, value: String, by: User.Id, now: Instant): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, fr0"$groupTarget, $group, $value, $now, $by").update

  private[sql] def update(group: Group.Id, value: String, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"value=$value, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group)).update
  }

  private[sql] def selectOne(group: Group.Id): doobie.Query0[String] =
    buildSelect(tableFr, fr0"value", where(group)).query[String]

  private def where(group: Group.Id) = fr0"WHERE target=$groupTarget AND target_id=$group"
}
