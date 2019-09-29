package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import fr.gospeak.core.domain.Contact.{FirstName, LastName}
import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.libs.scalautils.domain.EmailAddress

class ContactRepoSqlSpec extends RepoSpec {
  describe("ContactRepoSql") {
    it("should create a contact and retrieve it") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(group.id, partnerData1, user.id, now).unsafeRunSync()
      val contact = contactRepo.create(contactData1.copy(partner = partner.id), user.id, Instant.now()).unsafeRunSync
      contactRepo.find(contact.id).unsafeRunSync shouldBe Some(contact)
    }
    it("should update contact") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(group.id, partnerData1, user.id, now).unsafeRunSync()
      val contact = contactRepo.create(contactData1.copy(partner.id), user.id, Instant.now()).unsafeRunSync
      val address = EmailAddress.from("test@gmail.com").right.get
      val data = contactData1.copy(partner = partner.id, firstName = FirstName("newFN"), lastName = LastName("newLN"), email = address)
      contactRepo.edit(contact.id, data)(user.id, Instant.now()).unsafeRunSync()
      contactRepo.find(contact.id).unsafeRunSync().map(_.data shouldBe data)
    }
    it("should list all contacts") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(group.id, partnerData1, user.id, now).unsafeRunSync()
      val contact1 = contactRepo.create(contactData1.copy(partner = partner.id), user.id, Instant.now()).unsafeRunSync
      val contact2 = contactRepo.create(contactData1.copy(partner = partner.id, firstName = FirstName("contact2")), user.id, Instant.now()).unsafeRunSync
      contactRepo.list(partner.id).unsafeRunSync() shouldBe Seq(contact1, contact2)
    }
    it("should find by mail") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(group.id, partnerData1, user.id, now).unsafeRunSync()
      val mail = EmailAddress.from("tes@gmail.com").right.get
      val _ = contactRepo.create(contactData1.copy(partner = partner.id, email = mail), user.id, Instant.now()).unsafeRunSync
      contactRepo.exists(partner.id, mail).unsafeRunSync() shouldBe true
    }
    describe("Queries") {
      it("should build insert") {
        val q = ContactRepoSql.insert(contact)
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = ContactRepoSql.update(contact.id, contactData1)(user.id, now)
        q.sql shouldBe s"UPDATE $table SET first_name=?, last_name=?, email=?, description=?, updated=?, updated_by=? WHERE id=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = ContactRepoSql.selectPage(partner.id, params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE partner_id=? ORDER BY created IS NULL, created OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT COUNT(*) FROM $table WHERE partner_id=? "
        check(s)
        check(c)
      }
      it("should build selectAll") {
        val q = ContactRepoSql.selectAll(partner.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE partner_id=?"
        check(q)
      }
      it("should build selectOne") {
        val q = ContactRepoSql.selectOne(partner.id, contact.email)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE partner_id=? AND email=?"
        check(q)
      }
    }
  }
}

object ContactRepoSqlSpec {
  val table = "contacts"
  val fields = "id, partner_id, first_name, last_name, email, description, created, created_by, updated, updated_by"
}
