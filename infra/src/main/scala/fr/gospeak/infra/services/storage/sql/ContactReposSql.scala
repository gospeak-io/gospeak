package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.Contact.Id
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.storage.ContactRepo
import fr.gospeak.infra.services.storage.sql.ContactReposSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

class ContactReposSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ContactRepo {

  override def create(data: Contact.Data, by: User.Id, now: Instant): IO[Contact] =
    run(insert, Contact(data, Info(by, now)))

  override def edit(contact: Contact.Id, data: Contact.Data)(by: User.Id, now: Instant): IO[Done] = run(update(contact, data)(by, now))

  override def find(id: Contact.Id): IO[Option[Contact]] = run(selectOne(id).option)

  override def list(partner: Partner.Id): IO[Seq[Contact]] = run(selectAll(partner).to[List])

  override def list(partner: Partner.Id, params: Page.Params): IO[Page[Contact]] = run(Queries.selectPage(selectPage(partner, _), params))

  override def exists(partner: Partner.Id, email: EmailAddress): IO[Boolean] = run(selectOne(partner, email).option.map(_.isDefined))
}

object ContactReposSql {
  private val _ = contactIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "contacts"
  private[sql] val fields = Seq("id", "partner_id", "first_name", "last_name", "email", "description", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val partnerAndContactTables = Fragment.const0(s"$table c INNER JOIN ${PartnerRepoSql.table} p ON c.partner_id=p.id")
  private val partnerAndContactFields = Fragment.const0((PartnerRepoSql.fields.map("p." + _) ++ fields.map("c." + _)).mkString(", "))
  private val searchFields = Seq("id", "first_name", "last_name", "email")
  private val defaultSort = Page.OrderBy("c.created")
  private val prefixedFields = Fragment.const0(fields.map("c." + _).mkString(", "))

  private def values(c: Contact): Fragment =
    fr0"${c.id}, ${c.partner}, ${c.firstName}, ${c.lastName}, ${c.email}, ${c.description}, ${c.info.created}, ${c.info.createdBy}, ${c.info.updated}, ${c.info.updatedBy}"

  private[sql] def insert(elt: Contact): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def select(c: Contact.Id): doobie.Query0[Contact] = buildSelect(tableFr, fieldsFr, where(c)).query[Contact]

  private[sql] def selectBy(partner: Partner.Id): doobie.Query0[Contact] =
    buildSelect(tableFr, fieldsFr, where(partner)).query[Contact]

  private[sql] def update(contact: Contact.Id, data: Contact.Data)(by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"first_name=${data.firstName}, last_name=${data.lastName}, email=${data.email}, description=${data.description}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(contact)).update
  }

  private[sql] def selectPage(partner: Partner.Id, params: Page.Params): (doobie.Query0[Contact], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE p.id=$partner"))
    (buildSelect(partnerAndContactTables, prefixedFields, page.all).query[Contact], buildSelect(partnerAndContactTables, fr0"COUNT(*)", page.where).query[Long])
  }

  private[sql] def selectAll(partner: Partner.Id): doobie.Query0[Contact] = {
    buildSelect(partnerAndContactTables, prefixedFields, fr0"WHERE p.id=$partner").query[Contact]
  }

  private[sql] def selectOne(id: Contact.Id): doobie.Query0[Contact] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$id").query[Contact]

  private[sql] def selectOne(partner: Partner.Id, email: EmailAddress): doobie.Query0[Contact] =
    buildSelect(partnerAndContactTables, prefixedFields, where(partner, email)).query[Contact]

  private[sql] def where(partner: Partner.Id, email: EmailAddress): Fragment = fr0"WHERE p.id=$partner AND c.email=$email"

  private[sql] def where(id: Id): Fragment = fr0"WHERE id=$id"

  private[sql] def where(partner: Partner.Id) = fr0"WHERE partner_id=$partner"
}