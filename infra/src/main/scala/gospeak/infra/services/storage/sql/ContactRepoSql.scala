package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.services.storage.ContactRepo
import gospeak.infra.services.storage.sql.ContactRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.CONTACTS
import gospeak.infra.services.storage.sql.database.tables.CONTACTS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.domain.EmailAddress
import gospeak.libs.sql.dsl.Query

class ContactRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ContactRepo {
  override def create(data: Contact.Data)(implicit ctx: OrgaCtx): IO[Contact] = {
    val contact = Contact(data, ctx.info)
    insert(contact).run(xa).map(_ => contact)
  }

  override def edit(contact: Contact.Id, data: Contact.Data)(implicit ctx: OrgaCtx): IO[Unit] = update(contact, data)(ctx.user.id, ctx.now).run(xa)

  override def remove(partner: Partner.Id, contact: Contact.Id)(implicit ctx: OrgaCtx): IO[Unit] = delete(ctx.group.id, partner, contact)(ctx.user.id, ctx.now).run(xa)

  override def find(id: Contact.Id): IO[Option[Contact]] = selectOne(id).run(xa)

  override def list(partner: Partner.Id): IO[List[Contact]] = selectAll(partner).run(xa)

  override def exists(partner: Partner.Id, email: EmailAddress): IO[Boolean] = selectOne(partner, email).run(xa)
}

object ContactRepoSql {
  private[sql] def insert(e: Contact): Query.Insert[CONTACTS] =
    CONTACTS.insert.values(e.id, e.partner, e.firstName, e.lastName, e.email, e.notes, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)

  private[sql] def update(contact: Contact.Id, data: Contact.Data)(by: User.Id, now: Instant): Query.Update[CONTACTS] =
    CONTACTS.update.set(_.FIRST_NAME, data.firstName).set(_.LAST_NAME, data.lastName).set(_.EMAIL, data.email).set(_.NOTES, data.notes).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(_.ID.is(contact))

  private[sql] def delete(group: Group.Id, partner: Partner.Id, contact: Contact.Id)(by: User.Id, now: Instant): Query.Delete[CONTACTS] =
    CONTACTS.delete.where(_.ID is contact)

  private[sql] def selectAll(partner: Partner.Id): Query.Select.All[Contact] =
    CONTACTS.select.where(_.PARTNER_ID is partner).all[Contact]

  private[sql] def selectOne(id: Contact.Id): Query.Select.Optional[Contact] =
    CONTACTS.select.where(_.ID is id).option[Contact]

  private[sql] def selectOne(partner: Partner.Id, email: EmailAddress): Query.Select.Exists[Contact] =
    CONTACTS.select.where(c => c.PARTNER_ID.is(partner) and c.EMAIL.is(email)).exists[Contact]
}
