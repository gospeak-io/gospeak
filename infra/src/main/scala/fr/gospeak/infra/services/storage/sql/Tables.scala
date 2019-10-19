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

  val groups: Table = Table.from(
    name = "groups",
    prefix = "g",
    fields = Seq("id", "slug", "name", "contact", "description", "owners", "tags", "created", "created_by", "updated", "updated_by"),
    sort = Seq("name"),
    search = Seq("id", "slug", "name", "contact", "description", "tags")).get

  val members: Table = Table.from(
    name = "group_members",
    prefix = "gm",
    fields = Seq("group_id", "user_id", "role", "presentation", "joined_at"),
    sort = Seq("joined_at"),
    search = Seq("role", "presentation")).get

  val all: Seq[Table] = Seq(user, groups, members)
}
