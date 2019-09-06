package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.storage.GroupSettingsRepo
import fr.gospeak.infra.services.storage.sql.GroupSettingsRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments.{buildInsert, buildSelect, buildUpdate}
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.Done

class GroupSettingsRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with GroupSettingsRepo {
  override def find(group: Group.Id): IO[Group.Settings] =
    run(selectOne(group).option).map(_.getOrElse(Group.Settings.default))

  override def set(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): IO[Done] =
    run(selectOne(group).option).flatMap { opt =>
      opt.map { _ =>
        run(update(group, settings, by, now))
      }.getOrElse {
        run(insert(group, settings, by, now))
      }
    }
}

object GroupSettingsRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "group_settings"
  private val fields = Seq("group_id", "meetup_access_token", "meetup_refresh_token", "meetup_group_slug", "meetup_logged_user_id", "meetup_logged_user_name",
    "slack_token", "slack_bot_name", "slack_bot_avatar", "event_default_description", "event_templates", "actions", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val fieldsFrSelect: Fragment = Fragment.const0(fields.drop(1).dropRight(2).mkString(", "))

  private[sql] def insert(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, fr0"$group, " ++
      fr0"${settings.accounts.meetup.map(_.accessToken)}, ${settings.accounts.meetup.map(_.refreshToken)}, ${settings.accounts.meetup.map(_.group)}, ${settings.accounts.meetup.map(_.loggedUserId)}, ${settings.accounts.meetup.map(_.loggedUserName)}, " ++
      fr0"${settings.accounts.slack.map(_.token)}, ${settings.accounts.slack.map(_.name)}, ${settings.accounts.slack.flatMap(_.avatar)}, " ++
      fr0"${settings.event.defaultDescription}, ${settings.event.templates}, ${settings.actions}, $now, $by").update

  private[sql] def update(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"meetup_access_token=${settings.accounts.meetup.map(_.accessToken)}, meetup_refresh_token=${settings.accounts.meetup.map(_.refreshToken)}, meetup_group_slug=${settings.accounts.meetup.map(_.group)}, meetup_logged_user_id=${settings.accounts.meetup.map(_.loggedUserId)}, meetup_logged_user_name=${settings.accounts.meetup.map(_.loggedUserName)}, " ++
      fr0"slack_token=${settings.accounts.slack.map(_.token)}, slack_bot_name=${settings.accounts.slack.map(_.name)}, slack_bot_avatar=${settings.accounts.slack.flatMap(_.avatar)}, " ++
      fr0"event_default_description=${settings.event.defaultDescription}, event_templates=${settings.event.templates}, actions=${settings.actions}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group)).update
  }

  private[sql] def selectOne(group: Group.Id): doobie.Query0[Group.Settings] =
    buildSelect(tableFr, fieldsFrSelect, where(group)).query[Group.Settings]

  private def where(group: Group.Id) = fr0"WHERE group_id=$group"
}
