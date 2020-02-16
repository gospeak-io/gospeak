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
        check(q, s"UPDATE $table SET name=?, kind=?, logo=?, description=?, start=?, finish=?, location=?, location_id=?, location_lat=?, location_lng=?, location_locality=?, location_country=?, url=?, tickets_url=?, videos_url=?, twitter_account=?, twitter_hashtag=?, tags=?, updated_at=?, updated_by=? WHERE id=?")
      }
      it("should build selectOne") {
        val q = ExternalEventRepoSql.selectOne(externalEvent.id)
        check(q, s"SELECT $fields FROM $table WHERE ee.id=? LIMIT 1")
      }
      it("should build selectPage") {
        val q = ExternalEventRepoSql.selectPage(params)
        check(q, s"SELECT $fields FROM $table $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageCommon") {
        val q = ExternalEventRepoSql.selectPageCommon(params, now)
        val req = s"SELECT $commonFields FROM $commonTable WHERE true=true $commonOrderBy LIMIT 20 OFFSET 0"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectTags") {
        val q = ExternalEventRepoSql.selectTags()
        check(q, s"SELECT ee.tags FROM $table")
      }
      it("should build selectLogos") {
        val q = ExternalEventRepoSql.selectLogos()
        check(q, s"SELECT ee.logo FROM $table WHERE ee.logo IS NOT NULL")
      }
    }
  }
}

object ExternalEventRepoSqlSpec {
  val table = "external_events ee"
  val fieldsInsert: String = mapFields("id, name, kind, logo, description, start, finish, location, location_id, location_lat, location_lng, location_locality, location_country, url, tickets_url, videos_url, twitter_account, twitter_hashtag, tags, created_at, created_by, updated_at, updated_by", "ee." + _)
  val fields: String = fieldsInsert.split(", ").filterNot(_.startsWith("ee.location_")).mkString(", ")
  val orderBy = "ORDER BY ee.start IS NULL, ee.start DESC, ee.name IS NULL, ee.name"

  val commonTable: String = "(" +
    "(SELECT e.id, e.name, e.kind, e.start, v.address as location, g.social_twitter as twitter_account, null as twitter_hashtag, e.tags, g.id as int_group_id, g.slug as int_group_slug, g.name as int_group_name, g.logo as int_group_logo, c.id as int_cfp_id, c.slug as int_cfp_slug, c.name as int_cfp_name, v.id as int_venue_id, p.name as int_venue_name, p.logo as int_venue_logo, e.slug as int_event_slug, e.description as int_description, null   as ext_logo, null          as ext_description, null  as ext_url, null          as ext_tickets, null         as ext_videos, e.created_at, e.created_by, e.updated_at, e.updated_by FROM events e INNER JOIN groups g ON e.group_id=g.id LEFT OUTER JOIN cfps c ON e.cfp_id=c.id LEFT OUTER JOIN venues v ON e.venue=v.id LEFT OUTER JOIN partners p ON v.partner_id=p.id WHERE e.published IS NOT NULL) UNION " +
    "(SELECT e.id, e.name, e.kind, e.start,            e.location,                   e.twitter_account,       e.twitter_hashtag, e.tags, null as int_group_id, null   as int_group_slug, null   as int_group_name, null   as int_group_logo, null as int_cfp_id, null   as int_cfp_slug, null   as int_cfp_name, null as int_venue_id, null   as int_venue_name, null   as int_venue_logo, null   as int_event_slug, null          as int_description, e.logo as ext_logo, e.description as ext_description, e.url as ext_url, e.tickets_url as ext_tickets, e.videos_url as ext_videos, e.created_at, e.created_by, e.updated_at, e.updated_by FROM external_events e)) e"
  val commonFields: String = mapFields("id, name, kind, start, location, twitter_account, twitter_hashtag, tags, int_group_id, int_group_slug, int_group_name, int_group_logo, int_cfp_id, int_cfp_slug, int_cfp_name, int_venue_id, int_venue_name, int_venue_logo, int_event_slug, int_description, ext_logo, ext_description, ext_url, ext_tickets, ext_videos, created_at, created_by, updated_at, updated_by", "e." + _)
  val commonOrderBy = "ORDER BY e.start IS NULL, e.start DESC, e.created_at IS NULL, e.created_at DESC"
}
