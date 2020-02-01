package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.ExternalEventRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class ExternalEventRepoSqlSpec extends RepoSpec {
  describe("ExternalEventRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = ExternalEventRepoSql.insert(externalEvent)
        check(q, s"INSERT INTO ${table.stripSuffix(" ee")} (${mapFields(fieldsInsert, _.stripPrefix("ee."))}) VALUES (${mapFields(fieldsInsert, _ => "?")})")
      }
      it("should build update") {
        val q = ExternalEventRepoSql.update(externalEvent.id)(externalEvent.data, user.id, now)
        check(q, s"UPDATE $table SET name=?, logo=?, description=?, start=?, finish=?, location=?, location_id=?, location_lat=?, location_lng=?, location_locality=?, location_country=?, url=?, tickets_url=?, videos_url=?, twitter_account=?, twitter_hashtag=?, tags=?, updated_at=?, updated_by=? WHERE id=?")
      }
      it("should build selectOne") {
        val q = ExternalEventRepoSql.selectOne(externalEvent.id)
        check(q, s"SELECT $fields FROM $table WHERE ee.id=? LIMIT 1")
      }
      it("should build selectPage") {
        val q = ExternalEventRepoSql.selectPage(params)
        val req = s"SELECT $fields FROM $table $orderBy LIMIT 20 OFFSET 0"
        q.fr.query.sql shouldBe req
        check(q, req)
      }
    }
  }
}

object ExternalEventRepoSqlSpec {
  val table = "external_events ee"
  val fieldsInsert: String = mapFields("id, name, logo, description, start, finish, location, location_id, location_lat, location_lng, location_locality, location_country, url, tickets_url, videos_url, twitter_account, twitter_hashtag, tags, created_at, created_by, updated_at, updated_by", "ee." + _)
  val fields: String = fieldsInsert.split(", ").filterNot(_.startsWith("ee.location_")).mkString(", ")
  val orderBy = "ORDER BY ee.start IS NULL, ee.start DESC, ee.name IS NULL, ee.name"
}
