package fr.gospeak.infra.services.storage.sql.tables

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.Email
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._

object UserTable {
  private val _ = userIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "users"
  private val fields = Seq("id", "first_name", "last_name", "email", "created", "updated")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(e: User): Fragment =
    fr0"${e.id}, ${e.firstName}, ${e.lastName}, ${e.email}, ${e.created}, ${e.updated}"

  def insert(elt: User): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def selectOne(email: Email): doobie.Query0[User] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE email=$email").query[User]
}
