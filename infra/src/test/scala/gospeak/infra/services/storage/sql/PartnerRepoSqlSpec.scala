package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{table => contactTable}
import gospeak.infra.services.storage.sql.PartnerRepoSqlSpec._
import gospeak.infra.services.storage.sql.SponsorRepoSqlSpec.{table => sponsorTable}
import gospeak.infra.services.storage.sql.TablesSpec.socialFields
import gospeak.infra.services.storage.sql.VenueRepoSqlSpec.{table => venueTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class PartnerRepoSqlSpec extends RepoSpec {
  describe("PartnerRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = PartnerRepoSql.insert(partner)
        check(q, s"INSERT INTO ${table.stripSuffix(" pa")} (${mapFields(fields, _.stripPrefix("pa."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = PartnerRepoSql.update(group.id, partner.slug)(partner.data, user.id, now)
        check(q, s"UPDATE $table SET slug=?, name=?, notes=?, description=?, logo=?, " +
          s"social_facebook=?, social_instagram=?, social_twitter=?, social_linkedIn=?, social_youtube=?, social_meetup=?, social_eventbrite=?, social_slack=?, social_discord=?, social_github=?, " +
          s"updated_at=?, updated_by=? WHERE pa.group_id=? AND pa.slug=?")
      }
      it("should build delete") {
        val q = PartnerRepoSql.delete(group.id, partner.slug)
        check(q, s"DELETE FROM $table WHERE pa.group_id=? AND pa.slug=?")
      }
      it("should build selectPage") {
        val q = PartnerRepoSql.selectPage(group.id, params)
        check(q, s"SELECT $fields FROM $table WHERE pa.group_id=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull") {
        val q = PartnerRepoSql.selectPageFull(group.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? $groupByFull  $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAll") {
        val q = PartnerRepoSql.selectAll(group.id)
        check(q, s"SELECT $fields FROM $table WHERE pa.group_id=? $orderBy")
      }
      it("should build selectAll with partner ids") {
        val q = PartnerRepoSql.selectAll(NonEmptyList.of(partner.id))
        check(q, s"SELECT $fields FROM $table WHERE pa.id IN (?)  $orderBy")
      }
      it("should build selectOne by id") {
        val q = PartnerRepoSql.selectOne(group.id, partner.id)
        check(q, s"SELECT $fields FROM $table WHERE pa.group_id=? AND pa.id=? $orderBy")
      }
      it("should build selectOne by slug") {
        val q = PartnerRepoSql.selectOne(group.id, partner.slug)
        check(q, s"SELECT $fields FROM $table WHERE pa.group_id=? AND pa.slug=? $orderBy")
      }
    }
  }
}

object PartnerRepoSqlSpec {
  val table = "partners pa"
  val fields: String = mapFields(s"id, group_id, slug, name, notes, description, logo, $socialFields, created_at, created_by, updated_at, updated_by", "pa." + _)
  val orderBy = "ORDER BY pa.name IS NULL, pa.name"

  private val tableFull = s"$table LEFT OUTER JOIN $venueTable ON pa.id=v.partner_id LEFT OUTER JOIN $sponsorTable ON pa.id=s.partner_id LEFT OUTER JOIN $contactTable ON pa.id=ct.partner_id"
  private val fieldsFull = s"$fields, COALESCE(COUNT(DISTINCT v.id), 0) as venueCount, COALESCE(COUNT(DISTINCT s.id), 0) as sponsorCount, COALESCE(COUNT(DISTINCT ct.id), 0) as contactCount"
  private val groupByFull = s"GROUP BY $fields"
}
