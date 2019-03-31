package fr.gospeak.infra.services.storage.sql.tables

import java.time.Instant

import cats.data.NonEmptyList
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.User
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.EmailAddress

object UserTable {
  private val _ = userIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val credentialsTable = "credentials"
  private val credentialsFields = Seq("provider_id", "provider_key", "hasher", "password", "salt")
  private val loginTable = "logins"
  private val loginFields = Seq("provider_id", "provider_key", "user_id")
  private val table = "users"
  private val fields = Seq("id", "slug", "first_name", "last_name", "email", "email_validated", "avatar", "avatar_source", "created", "updated")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(e: User): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.firstName}, ${e.lastName}, ${e.email}, ${e.emailValidated}, ${e.avatar.url}, ${e.avatar.source}, ${e.created}, ${e.updated}"

  def insert(elt: User): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def update(elt: User): doobie.Update0 = {
    val fields = fr0"slug=${elt.slug}, first_name=${elt.firstName}, last_name=${elt.lastName}, email=${elt.email}, updated=${elt.updated}"
    val where = fr0"WHERE id=${elt.id}"
    buildUpdate(tableFr, fields, where).update
  }

  def validateAccount(id: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"email_validated=$now", fr0"WHERE id=$id").update

  def insertLoginRef(i: User.LoginRef): doobie.Update0 =
    buildInsert(Fragment.const0(loginTable), Fragment.const0(loginFields.mkString(", ")), fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.user}").update

  def insertCredentials(i: User.Credentials): doobie.Update0 =
    buildInsert(Fragment.const0(credentialsTable), Fragment.const0(credentialsFields.mkString(", ")), fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.pass.hasher}, ${i.pass.password}, ${i.pass.salt}").update

  def updateCredentials(login: User.Login)(pass: User.Password): doobie.Update0 = {
    val fields = fr0"hasher=${pass.hasher}, password=${pass.password}, salt=${pass.salt}"
    val where = fr0"WHERE provider_id=${login.providerId} AND provider_key=${login.providerKey}"
    buildUpdate(Fragment.const0(credentialsTable), fields, where).update
  }

  def deleteCredentials(login: User.Login): doobie.Update0 = {
    val where = fr0"WHERE provider_id=${login.providerId} AND provider_key=${login.providerKey}"
    buildDelete(Fragment.const0(credentialsTable), where).update
  }

  def selectCredentials(login: User.Login): doobie.Query0[User.Credentials] =
    buildSelect(Fragment.const0(credentialsTable), Fragment.const0(credentialsFields.mkString(", ")), fr0"WHERE provider_id=${login.providerId} AND provider_key=${login.providerKey}").query[User.Credentials]

  def selectOne(login: User.Login): doobie.Query0[User] = {
    val selectTables = Fragment.const0(s"$table u INNER JOIN $loginTable l ON u.id=l.user_id")
    val selectedFields = Fragment.const0(fields.map("u." + _).mkString(", "))
    buildSelect(selectTables, selectedFields, fr0"WHERE l.provider_id=${login.providerId} AND l.provider_key=${login.providerKey}").query[User]
  }

  def selectOne(email: EmailAddress): doobie.Query0[User] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE email=$email").query[User]

  def selectOne(slug: User.Slug): doobie.Query0[User] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE slug=$slug").query[User]

  def selectAll(ids: NonEmptyList[User.Id]): doobie.Query0[User] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[User]
}
