package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.Contact.{FirstName, LastName}
import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields
import fr.gospeak.libs.scalautils.domain.EmailAddress

class ContactRepoSqlSpec extends RepoSpec {
  describe("ContactRepoSql") {
    it("should create a contact and retrieve it") {
      val (user, group, ctx) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val contact = contactRepo.create(contactData1.copy(partner = partner.id))(ctx).unsafeRunSync
      contactRepo.find(contact.id).unsafeRunSync shouldBe Some(contact)
    }
    it("should update contact") {
      val (user, group, ctx) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val contact = contactRepo.create(contactData1.copy(partner.id))(ctx).unsafeRunSync
      val address = EmailAddress.from("test@gmail.com").right.get
      val data = contactData1.copy(partner = partner.id, firstName = FirstName("newFN"), lastName = LastName("newLN"), email = address)
      contactRepo.edit(contact.id, data)(ctx).unsafeRunSync()
      contactRepo.find(contact.id).unsafeRunSync().map(_.data shouldBe data)
    }
    it("should list all contacts") {
      val (user, group, ctx) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val contact1 = contactRepo.create(contactData1.copy(partner = partner.id, lastName = LastName("contact1")))(ctx).unsafeRunSync
      val contact2 = contactRepo.create(contactData1.copy(partner = partner.id, lastName = LastName("contact2")))(ctx).unsafeRunSync
      contactRepo.list(partner.id).unsafeRunSync() shouldBe Seq(contact1, contact2)
    }
    it("should find by mail") {
      val (user, group, ctx) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val mail = EmailAddress.from("tes@gmail.com").right.get
      val _ = contactRepo.create(contactData1.copy(partner = partner.id, email = mail))(ctx).unsafeRunSync
      contactRepo.exists(partner.id, mail).unsafeRunSync() shouldBe true
    }
    describe("Queries") {
      it("should build insert") {
        val q = ContactRepoSql.insert(contact)
        check(q, s"INSERT INTO ${table.stripSuffix(" ct")} (${mapFields(fields, _.stripPrefix("ct."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = ContactRepoSql.update(contact.id, contactData1)(user.id, now)
        check(q, s"UPDATE $table SET first_name=?, last_name=?, email=?, description=?, updated_at=?, updated_by=? WHERE ct.id=?")
      }
      it("should build delete") {
        val q = ContactRepoSql.delete(group.id, partner.id, contact.id)(user.id, now)
        check(q, s"DELETE FROM $table WHERE ct.id=?")
      }
      it("should build selectPage") {
        val q = ContactRepoSql.selectPage(partner.id, params)
        check(q, s"SELECT $fields FROM $table WHERE ct.partner_id=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAll") {
        val q = ContactRepoSql.selectAll(partner.id)
        check(q, s"SELECT $fields FROM $table WHERE ct.partner_id=? $orderBy")
      }
      it("should build selectOne") {
        val q = ContactRepoSql.selectOne(contact.id)
        check(q, s"SELECT $fields FROM $table WHERE ct.id=? $orderBy")
      }
      it("should build selectOne by partner and email") {
        val q = ContactRepoSql.selectOne(partner.id, contact.email)
        check(q, s"SELECT $fields FROM $table WHERE ct.partner_id=? AND ct.email=? $orderBy")
      }
    }
  }
}

object ContactRepoSqlSpec {
  val table = "contacts ct"
  val fields: String = mapFields("id, partner_id, first_name, last_name, email, description, created_at, created_by, updated_at, updated_by", "ct." + _)
  val orderBy = "ORDER BY ct.last_name IS NULL, ct.last_name, ct.first_name IS NULL, ct.first_name"
}
