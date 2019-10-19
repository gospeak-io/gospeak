package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.Contact.Id
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.storage.ContactRepo
import fr.gospeak.infra.services.storage.sql.ContactRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.SelectPage
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

class ContactRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ContactRepo {
  override def create(data: Contact.Data, by: User.Id, now: Instant): IO[Contact] =
    run(insert, Contact(data, Info(by, now)))

  override def edit(contact: Contact.Id, data: Contact.Data)(by: User.Id, now: Instant): IO[Done] = run(update(contact, data)(by, now))

  override def find(id: Contact.Id): IO[Option[Contact]] = run(selectOne(id).option)

  override def list(partner: Partner.Id): IO[Seq[Contact]] = run(selectAll(partner).to[List])

  override def list(partner: Partner.Id, params: Page.Params): IO[Page[Contact]] = run(selectPage(partner, params).page)

  override def exists(partner: Partner.Id, email: EmailAddress): IO[Boolean] = run(selectOne(partner, email).option.map(_.isDefined))
}

object ContactRepoSql {
  private val _ = contactIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "contacts"
  private[sql] val fields = Seq("id", "partner_id", "first_name", "last_name", "email", "description", "created", "created_by", "updated", "updated_by")
  private val tableFr = Fragment.const0(table)
  private val fieldsFr = Fragment.const0(fields.mkString(", "))
  private[sql] val searchFields = Seq("id", "first_name", "last_name", "email")
  private val defaultSort = Page.OrderBy("created")

  private[sql] def insert(e: Contact): doobie.Update0 = {
    val values = fr0"${e.id}, ${e.partner}, ${e.firstName}, ${e.lastName}, ${e.email}, ${e.description}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    buildInsert(tableFr, fieldsFr, values).update
  }

  private[sql] def update(contact: Contact.Id, data: Contact.Data)(by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"first_name=${data.firstName}, last_name=${data.lastName}, email=${data.email}, description=${data.description}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(contact)).update
  }

  private[sql] def select(c: Contact.Id): doobie.Query0[Contact] = buildSelect(tableFr, fieldsFr, where(c)).query[Contact]

  private[sql] def selectBy(partner: Partner.Id): doobie.Query0[Contact] =
    buildSelect(tableFr, fieldsFr, where(partner)).query[Contact]

  private[sql] def selectPage(partner: Partner.Id, params: Page.Params): SelectPage[Contact] =
    SelectPage[Contact](table, fieldsFr, where(partner), params, defaultSort, searchFields)

  private[sql] def selectAll(partner: Partner.Id): doobie.Query0[Contact] =
    buildSelect(tableFr, fieldsFr, where(partner)).query[Contact]

  private[sql] def selectOne(id: Contact.Id): doobie.Query0[Contact] =
    buildSelect(tableFr, fieldsFr, where(id)).query[Contact]

  private[sql] def selectOne(partner: Partner.Id, email: EmailAddress): doobie.Query0[Contact] =
    buildSelect(tableFr, fieldsFr, where(partner, email)).query[Contact]

  private def where(partner: Partner.Id): Fragment = fr0"WHERE partner_id=$partner"

  private def where(partner: Partner.Id, email: EmailAddress): Fragment = fr0"WHERE partner_id=$partner AND email=$email"

  private def where(id: Id): Fragment = fr0"WHERE id=$id"
}
