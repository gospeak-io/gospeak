package gospeak.infra.services.storage.sql

import gospeak.core.domain.Contact.LastName
import gospeak.infra.services.storage.sql.ContactRepoSql._
import gospeak.infra.services.storage.sql.ContactRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class ContactRepoSqlSpec extends RepoSpec {
  describe("ContactRepoSql") {
    it("should handle crud operations") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      contactRepo.list(partner.id).unsafeRunSync() shouldBe List()
      val contact = contactRepo.create(contactData1.copy(partner = partner.id))(ctx).unsafeRunSync()
      contactRepo.list(partner.id).unsafeRunSync() shouldBe List(contact)

      val data = contactData2.copy(partner = partner.id)
      contactRepo.edit(contact.id, data)(ctx).unsafeRunSync()
      contactRepo.list(partner.id).unsafeRunSync().map(_.data) shouldBe List(data)

      contactRepo.remove(partner.id, contact.id)(ctx).unsafeRunSync()
      contactRepo.list(partner.id).unsafeRunSync() shouldBe List()
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val contact = contactRepo.create(contactData1.copy(partner.id))(ctx).unsafeRunSync()

      contactRepo.find(contact.id).unsafeRunSync() shouldBe Some(contact)
      contactRepo.list(partner.id).unsafeRunSync() shouldBe List(contact)
      contactRepo.exists(partner.id, contact.email).unsafeRunSync() shouldBe true
    }
    it("should list all contacts") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val contact1 = contactRepo.create(contactData1.copy(partner = partner.id, lastName = LastName("contact1")))(ctx).unsafeRunSync()
      val contact2 = contactRepo.create(contactData1.copy(partner = partner.id, lastName = LastName("contact2")))(ctx).unsafeRunSync()
      contactRepo.list(partner.id).unsafeRunSync() shouldBe List(contact1, contact2)
    }
    it("should check queries") {
      check(insert(contact), s"INSERT INTO ${table.stripSuffix(" ct")} (${mapFields(fields, _.stripPrefix("ct."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(contact.id, contactData1)(user.id, now), s"UPDATE $table SET first_name=?, last_name=?, email=?, notes=?, updated_at=?, updated_by=? WHERE ct.id=?")
      check(delete(group.id, partner.id, contact.id)(user.id, now), s"DELETE FROM $table WHERE ct.id=?")
      check(selectAll(partner.id), s"SELECT $fields FROM $table WHERE ct.partner_id=? $orderBy")
      check(selectOne(contact.id), s"SELECT $fields FROM $table WHERE ct.id=? $orderBy")
      check(selectOne(partner.id, contact.email), s"SELECT $fields FROM $table WHERE ct.partner_id=? AND ct.email=? $orderBy")
    }
  }
}

object ContactRepoSqlSpec {
  val table = "contacts ct"
  val fields: String = mapFields("id, partner_id, first_name, last_name, email, notes, created_at, created_by, updated_at, updated_by", "ct." + _)
  val orderBy = "ORDER BY ct.last_name IS NULL, ct.last_name, ct.first_name IS NULL, ct.first_name"
}
