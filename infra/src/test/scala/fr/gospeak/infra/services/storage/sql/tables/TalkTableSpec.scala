package fr.gospeak.infra.services.storage.sql.tables

import java.time.Instant

import cats.data.NonEmptyList
import fr.gospeak.core.domain.Talk
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.infra.services.storage.sql.tables.TalkTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

import scala.concurrent.duration.{Duration, MINUTES}

class TalkTableSpec extends TableSpec {
  private val slug = Talk.Slug.from("my-talk").get
  private val data = Talk.Data(slug, Talk.Title("My Talk"), Duration(10, MINUTES), "best talk")
  private val talk = Talk(talkId, slug, Talk.Title("My Talk"), Duration(10, MINUTES), Talk.Status.Draft, "best talk", NonEmptyList.of(userId), Info(userId))

  describe("TalkTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(talk)
        q.sql shouldBe "INSERT INTO talks (id, slug, title, duration, status, description, speakers, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate the query") {
        val q = selectOne(userId, slug)
        q.sql shouldBe "SELECT id, slug, title, duration, status, description, speakers, created, created_by, updated, updated_by FROM talks WHERE speakers LIKE ? AND slug=?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate the query") {
        val (s, c) = selectPage(userId, params)
        s.sql shouldBe "SELECT id, slug, title, duration, status, description, speakers, created, created_by, updated, updated_by FROM talks WHERE speakers LIKE ? ORDER BY title OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM talks WHERE speakers LIKE ? "
        check(s)
        check(c)
      }
    }
    describe("updateAll") {
      it("should generate the query") {
        val q = updateAll(userId, slug)(data, Instant.now())
        q.sql shouldBe "UPDATE talks SET slug=?, title=?, duration=?, description=?, updated=?, updated_by=? WHERE speakers LIKE ? AND slug=?"
        check(q)
      }
    }
    describe("updateStatus") {
      it("should generate the query") {
        val q = updateStatus(userId, slug)(Talk.Status.Public)
        q.sql shouldBe "UPDATE talks SET status=? WHERE speakers LIKE ? AND slug=?"
        check(q)
      }
    }
  }
}
