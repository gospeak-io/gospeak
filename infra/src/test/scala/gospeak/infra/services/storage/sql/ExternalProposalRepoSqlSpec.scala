package gospeak.infra.services.storage.sql

import gospeak.core.domain.Proposal
import gospeak.infra.services.storage.sql.ExternalEventRepoSqlSpec.{fields => externalEventFields, table => externalEventTable}
import gospeak.infra.services.storage.sql.ExternalProposalRepoSqlSpec._
import gospeak.infra.services.storage.sql.TalkRepoSqlSpec.{fields => talkFields, table => talkTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class ExternalProposalRepoSqlSpec extends RepoSpec {
  describe("ExternalProposalRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = ExternalProposalRepoSql.insert(externalProposal)
        check(q, s"INSERT INTO ${table.stripSuffix(" ep")} (${mapFields(fields, _.stripPrefix("ep."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = ExternalProposalRepoSql.update(externalProposal.id)(externalProposal.data, user.id, now)
        check(q, s"UPDATE $table SET status=?, title=?, duration=?, description=?, message=?, slides=?, video=?, url=?, tags=?, updated_at=?, updated_by=? WHERE id=? AND speakers LIKE ?")
      }
      it("should build updateStatus") {
        val q = ExternalProposalRepoSql.updateStatus(externalProposal.id)(Proposal.Status.Accepted, user.id)
        check(q, s"UPDATE $table SET status=? WHERE ep.id=? AND ep.speakers LIKE ?")
      }
      it("should build updateSlides") {
        val q = ExternalProposalRepoSql.updateSlides(externalProposal.id)(urlSlides, user.id, now)
        check(q, s"UPDATE $table SET slides=?, updated_at=?, updated_by=? WHERE ep.id=? AND ep.speakers LIKE ?")
      }
      it("should build updateVideo") {
        val q = ExternalProposalRepoSql.updateVideo(externalProposal.id)(urlVideo, user.id, now)
        check(q, s"UPDATE $table SET video=?, updated_at=?, updated_by=? WHERE ep.id=? AND ep.speakers LIKE ?")
      }
      it("should build updateSpeakers") {
        val q = ExternalProposalRepoSql.updateSpeakers(externalProposal.id)(talk.speakers, user.id, now)
        check(q, s"UPDATE $table SET speakers=?, updated_at=?, updated_by=? WHERE ep.id=? AND ep.speakers LIKE ?")
      }
      it("should build delete") {
        val q = ExternalProposalRepoSql.delete(externalProposal.id, user.id)
        check(q, s"DELETE FROM $table WHERE ep.id=? AND ep.speakers LIKE ?")
      }
      it("should build selectOne") {
        val q = ExternalProposalRepoSql.selectOne(externalProposal.id)
        check(q, s"SELECT $fields FROM $table WHERE ep.id=? $orderBy LIMIT 1")
      }
      it("should build selectOneFull") {
        val q = ExternalProposalRepoSql.selectOneFull(externalProposal.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE ep.id=? $orderBy LIMIT 1")
      }
      it("should build selectAllPublicIds") {
        val q = ExternalProposalRepoSql.selectAllPublicIds()
        check(q, s"SELECT ep.event_id, ep.id FROM $table WHERE ep.status=? $orderBy")
      }
      it("should build selectPage for event") {
        val q = ExternalProposalRepoSql.selectPage(externalEvent.id, Proposal.Status.Accepted, params)
        check(q, s"SELECT $fields FROM $table WHERE ep.event_id=? AND ep.status=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageCommon for talk") {
        val q = ExternalProposalRepoSql.selectPageCommon(talk.id, params)
        val req = s"SELECT $commonFields FROM $commonTable WHERE p.talk_id=? $commonOrderBy LIMIT 20 OFFSET 0"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectPageCommon for user") {
        val q = ExternalProposalRepoSql.selectPageCommon(params)
        val req = s"SELECT $commonFields FROM $commonTable WHERE p.speakers LIKE ? $commonOrderBy LIMIT 20 OFFSET 0"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectPageCommonCurrent") {
        val q = ExternalProposalRepoSql.selectPageCommonCurrent(params)
        val current = "((p.status=?) OR (p.status=? AND (p.event_start > ? OR p.event_ext_start > ?)) OR (p.status=? AND p.updated_at > ?))"
        val req = s"SELECT $commonFields FROM $commonTable WHERE p.speakers LIKE ? AND $current $commonOrderBy LIMIT 20 OFFSET 0"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectAllCommon for talk") {
        val q = ExternalProposalRepoSql.selectAllCommon(talk.id)
        val req = s"SELECT $commonFields FROM $commonTable WHERE p.talk_id=? $commonOrderBy"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectAllCommon for user and status") {
        val q = ExternalProposalRepoSql.selectAllCommon(user.id, Proposal.Status.Accepted)
        val req = s"SELECT $commonFields FROM $commonTable WHERE p.speakers LIKE ? AND p.status=? $commonOrderBy"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
      it("should build selectAllCommon for talk and status") {
        val q = ExternalProposalRepoSql.selectAllCommon(talk.id, Proposal.Status.Accepted)
        val req = s"SELECT $commonFields FROM $commonTable WHERE p.talk_id=? AND p.status=? $commonOrderBy"
        q.fr.query.sql shouldBe req
        // check(q, req) // not null types become nullable when doing union, so it fails :(
      }
    }
  }
}

object ExternalProposalRepoSqlSpec {
  val table = "external_proposals ep"
  val fields: String = mapFields("id, talk_id, event_id, status, title, duration, description, message, speakers, slides, video, url, tags, created_at, created_by, updated_at, updated_by", "ep." + _)
  val orderBy = "ORDER BY ep.title IS NULL, ep.title, ep.created_at IS NULL, ep.created_at"

  val tableFull = s"$table INNER JOIN $talkTable ON ep.talk_id=t.id INNER JOIN $externalEventTable ON ep.event_id=ee.id"
  val fieldsFull = s"$fields, $talkFields, $externalEventFields"

  val commonTable: String = "(" +
    "(SELECT p.title, p.status, p.duration, p.speakers, p.slides, p.video, p.tags, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, null as ext_id, null as event_ext_id, null   as event_ext_name, null   as event_ext_kind, null   as event_ext_logo, null    as event_ext_start, null  as event_ext_url, null  as event_ext_proposal_url, p.id as int_id, g.id as group_id, g.slug as group_slug, g.name as group_name, g.logo as group_logo, g.owners as group_owners, c.id as cfp_id, c.slug as cfp_slug, c.name as cfp_name, e.id as event_id, e.slug as event_slug, e.name as event_name, e.kind as event_kind, e.start as event_start, p.created_at, p.created_by, p.updated_at, p.updated_by FROM proposals p          INNER JOIN talks t ON p.talk_id=t.id LEFT OUTER JOIN events e     ON p.event_id=e.id INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN groups g ON c.group_id=g.id WHERE e.published IS NOT NULL) UNION " +
    "(SELECT p.title, p.status, p.duration, p.speakers, p.slides, p.video, p.tags, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, p.id as ext_id, e.id as event_ext_id, e.name as event_ext_name, e.kind as event_ext_kind, e.logo as event_ext_logo, e.start as event_ext_start, e.url as event_ext_url, p.url as event_ext_proposal_url, null as int_id, null as group_id, null   as group_slug, null   as group_name, null   as group_logo, null     as group_owners, null as cfp_id, null   as cfp_slug, null   as cfp_name, null as event_id, null   as event_slug, null   as event_name, null   as event_kind, null    as event_start, p.created_at, p.created_by, p.updated_at, p.updated_by FROM external_proposals p INNER JOIN talks t ON p.talk_id=t.id INNER JOIN external_events e ON p.event_id=e.id)) p"
  val commonFields: String = mapFields("title, status, duration, speakers, slides, video, tags, talk_id, talk_slug, talk_duration, ext_id, event_ext_id, event_ext_name, event_ext_kind, event_ext_logo, event_ext_start, event_ext_url, event_ext_proposal_url, int_id, group_id, group_slug, group_name, group_logo, group_owners, cfp_id, cfp_slug, cfp_name, event_id, event_slug, event_name, event_kind, event_start, created_at, created_by, updated_at, updated_by", "p." + _)
  val commonOrderBy = "ORDER BY p.created_at IS NULL, p.created_at DESC"
}
