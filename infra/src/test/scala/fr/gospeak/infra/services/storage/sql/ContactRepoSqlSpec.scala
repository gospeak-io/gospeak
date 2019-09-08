package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import fr.gospeak.core.domain.Contact.{FirstName, LastName}
import fr.gospeak.infra.services.storage.sql.ContactRepoSql._
import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.libs.scalautils.domain.EmailAddress

class ContactRepoSqlSpec extends RepoSpec {
  describe("ContactRepoSql") {
    it("should create a contact and retrieve it") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(group.id, partnerData1, user.id, now).unsafeRunSync()
      val contact = contactRepo.create(contactData.copy(partner = partner.id), user.id, Instant.now()).unsafeRunSync
      contactRepo.find(contact.id).unsafeRunSync shouldBe Some(contact)
    }
    it("should update contact") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(group.id, partnerData1, user.id, now).unsafeRunSync()
      val contact = contactRepo.create(contactData.copy(partner.id), user.id, Instant.now()).unsafeRunSync
      val address = EmailAddress.from("test@gmail.com").right.get
      val data = contactData.copy(partner = partner.id, firstName = FirstName("newFN"), lastName = LastName("newLN"), email = address)
      contactRepo.edit(contact.id, data)(user.id, Instant.now()).unsafeRunSync()
      contactRepo.find(contact.id).unsafeRunSync().map(_.data shouldBe data)
    }
    it("should list all contacts") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(group.id, partnerData1, user.id, now).unsafeRunSync()
      val contact1 = contactRepo.create(contactData.copy(partner = partner.id), user.id, Instant.now()).unsafeRunSync
      val contact2 = contactRepo.create(contactData.copy(partner = partner.id, firstName = FirstName("contact2")), user.id, Instant.now()).unsafeRunSync
      contactRepo.list(partner.id).unsafeRunSync() shouldBe Seq(contact1, contact2)
    }
    it("should find by mail") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val partner = partnerRepo.create(group.id, partnerData1, user.id, now).unsafeRunSync()
      val mail = EmailAddress.from("tes@gmail.com").right.get
      val _ = contactRepo.create(contactData.copy(partner = partner.id, email = mail), user.id, Instant.now()).unsafeRunSync
      contactRepo.exists(partner.id, mail).unsafeRunSync() shouldBe true
    }
  }


  describe("ContactRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = insert(contact)
        q.sql shouldBe s"INSERT INTO contacts ($fieldList) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(contact.id, contactData)(user.id, now)
        q.sql shouldBe "UPDATE contacts SET first_name=?, last_name=?, email=?, description=?, updated=?, updated_by=? WHERE id=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = selectPage(partner.id, params)
        s.sql shouldBe s"SELECT ${withPrefix(fieldList, "c.")} FROM contacts c INNER JOIN partners p ON c.partner_id=p.id WHERE p.id=? ORDER BY c.created IS NULL, c.created OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT COUNT(*) FROM contacts c INNER JOIN partners p ON c.partner_id=p.id WHERE p.id=? "
        check(s)
        check(c)
      }
      it("should build selectAll") {
        val q = selectAll(partner.id)
        q.sql shouldBe s"SELECT ${withPrefix(fieldList, "c.")} FROM contacts c INNER JOIN partners p ON c.partner_id=p.id WHERE p.id=?"
        check(q)
      }
      it("should build selectOne") {
        val q = selectOne(partner.id, contact.email)
        q.sql shouldBe s"SELECT ${withPrefix(fieldList, "c.")} FROM contacts c INNER JOIN partners p ON c.partner_id=p.id WHERE p.id=? AND c.email=?"
        check(q)
      }
    }
  }
}

object ContactRepoSqlSpec {
  val fieldList = "id, partner_id, first_name, last_name, email, description, created, created_by, updated, updated_by"
}
