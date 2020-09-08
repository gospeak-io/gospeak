package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.services.storage.ContactRepo
import gospeak.infra.services.storage.sql.ContactRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.CONTACTS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.domain.{Done, EmailAddress}
import gospeak.libs.sql.doobie.Query
import gospeak.libs.sql.dsl.Query.Select

class ContactRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ContactRepo {
  override def create(data: Contact.Data)(implicit ctx: OrgaCtx): IO[Contact] = insert(Contact(data, ctx.info)).run(xa)

  override def edit(contact: Contact.Id, data: Contact.Data)(implicit ctx: OrgaCtx): IO[Done] = update(contact, data)(ctx.user.id, ctx.now).run(xa)

  override def remove(partner: Partner.Id, contact: Contact.Id)(implicit ctx: OrgaCtx): IO[Done] = delete(ctx.group.id, partner, contact)(ctx.user.id, ctx.now).run(xa)

  override def find(id: Contact.Id): IO[Option[Contact]] = selectOne(id).runOption(xa)

  override def list(partner: Partner.Id): IO[List[Contact]] = selectAll(partner).runList(xa)

  override def exists(partner: Partner.Id, email: EmailAddress): IO[Boolean] = selectOne(partner, email).runExists(xa)
}

object ContactRepoSql {
  private val _ = contactIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.contacts

  private[sql] def insert(e: Contact): Query.Insert[Contact] = {
    val values = fr0"${e.id}, ${e.partner}, ${e.firstName}, ${e.lastName}, ${e.email}, ${e.notes}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    val q1 = table.insert[Contact](e, _ => values)
    val q2 = CONTACTS.insert.values(e.id, e.partner, e.firstName, e.lastName, e.email, e.notes, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(contact: Contact.Id, data: Contact.Data)(by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"first_name=${data.firstName}, last_name=${data.lastName}, email=${data.email}, notes=${data.notes}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(where(contact))
    val q2 = CONTACTS.update.set(_.FIRST_NAME, data.firstName).set(_.LAST_NAME, data.lastName).set(_.EMAIL, data.email).set(_.NOTES, data.notes).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(_.ID.is(contact))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def delete(group: Group.Id, partner: Partner.Id, contact: Contact.Id)(by: User.Id, now: Instant): Query.Delete = {
    val q1 = table.delete.where(where(contact))
    val q2 = CONTACTS.delete.where(_.ID is contact)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(partner: Partner.Id): Query.Select[Contact] = {
    val q1 = table.select[Contact].where(where(partner))
    val q2: Select.All[Contact] = CONTACTS.select.where(_.PARTNER_ID is partner).all[Contact]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(id: Contact.Id): Query.Select[Contact] = {
    val q1 = table.select[Contact].where(where(id))
    val q2 = CONTACTS.select.where(_.ID is id).option[Contact]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(partner: Partner.Id, email: EmailAddress): Query.Select[Contact] = {
    val q1 = table.select[Contact].where(where(partner, email))
    val q2 = CONTACTS.select.where(c => c.PARTNER_ID.is(partner) and c.EMAIL.is(email)).exists[Contact]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def where(partner: Partner.Id): Fragment = fr0"ct.partner_id=$partner"

  private def where(partner: Partner.Id, email: EmailAddress): Fragment = fr0"ct.partner_id=$partner AND ct.email=$email"

  private def where(id: Contact.Id): Fragment = fr0"ct.id=$id"
}
