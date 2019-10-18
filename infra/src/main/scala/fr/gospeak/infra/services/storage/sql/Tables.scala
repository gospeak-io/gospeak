package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.utils.DoobieUtils.Table
import fr.gospeak.libs.scalautils.domain.Page

object Tables {

  val groups = Table(
    name = "groups",
    prefix = "g",
    fields = Seq("id", "slug", "name", "contact", "description", "owners", "tags", "created", "created_by", "updated", "updated_by"),
    sort = Page.OrderBy("name"),
    search = Seq("id", "slug", "name", "contact", "description", "tags"))

}
