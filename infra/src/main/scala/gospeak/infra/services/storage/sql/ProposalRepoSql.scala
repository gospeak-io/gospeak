package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.{BasicCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.ProposalRepo
import gospeak.infra.services.storage.sql.ProposalRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._
import gospeak.libs.sql.doobie.{CustomField, DbCtx, Field, Query, Table}

class ProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ProposalRepo {
  override def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[Proposal] =
    insert(Proposal(talk, cfp, None, data, Proposal.Status.Pending, speakers, ctx.info)).run(xa)

  override def edit(cfp: Cfp.Slug, proposal: Proposal.Id, data: Proposal.DataOrga)(implicit ctx: OrgaCtx): IO[Done] =
    update(ctx.user.id, ctx.group.slug, cfp, proposal)(data, ctx.now).run(xa)

  override def edit(talk: Talk.Slug, cfp: Cfp.Slug, data: Proposal.Data)(implicit ctx: UserCtx): IO[Done] =
    update(ctx.user.id, talk, cfp)(data, ctx.now).run(xa)

  override def editSlides(cfp: Cfp.Slug, id: Proposal.Id, slides: Url.Slides)(implicit ctx: OrgaCtx): IO[Done] =
    updateSlides(cfp, id)(slides, ctx.user.id, ctx.now).run(xa)

  override def editSlides(talk: Talk.Slug, cfp: Cfp.Slug, slides: Url.Slides)(implicit ctx: UserCtx): IO[Done] =
    updateSlides(ctx.user.id, talk, cfp)(slides, ctx.user.id, ctx.now).run(xa)

  override def editVideo(cfp: Cfp.Slug, id: Proposal.Id, video: Url.Video)(implicit ctx: OrgaCtx): IO[Done] =
    updateVideo(cfp, id)(video, ctx.user.id, ctx.now).run(xa)

  override def editVideo(talk: Talk.Slug, cfp: Cfp.Slug, video: Url.Video)(implicit ctx: UserCtx): IO[Done] =
    updateVideo(ctx.user.id, talk, cfp)(video, ctx.user.id, ctx.now).run(xa)

  override def editOrgaTags(cfp: Cfp.Slug, id: Proposal.Id, orgaTags: Seq[Tag])(implicit ctx: OrgaCtx): IO[Done] =
    updateOrgaTags(cfp, id)(orgaTags, ctx.user.id, ctx.now).run(xa)

  override def addSpeaker(proposal: Proposal.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(proposal).flatMap {
      case Some(proposalElt) =>
        if (proposalElt.speakers.toList.contains(speaker)) {
          IO.raiseError(new IllegalArgumentException("speaker already added"))
        } else {
          updateSpeakers(proposalElt.id)(proposalElt.speakers.append(speaker), by, now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, speaker: User.Id)(implicit ctx: UserCtx): IO[Done] =
    removeSpeaker(find(talk, cfp))(speaker, ctx.user.id, ctx.now)

  override def removeSpeaker(cfp: Cfp.Slug, id: Proposal.Id, speaker: User.Id)(implicit ctx: OrgaCtx): IO[Done] =
    removeSpeaker(find(cfp, id))(speaker, ctx.user.id, ctx.now)

  private def removeSpeaker(proposal: IO[Option[Proposal]])(speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    proposal.flatMap {
      case Some(proposalElt) =>
        if (proposalElt.info.createdBy == speaker) {
          IO.raiseError(new IllegalArgumentException("proposal creator can't be removed"))
        } else if (proposalElt.speakers.toList.contains(speaker)) {
          proposalElt.speakers.filter(_ != speaker).toNel.map { speakers =>
            updateSpeakers(proposalElt.id)(speakers, by, now).run(xa)
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last speaker can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a speaker"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable proposal"))
    }

  override def accept(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Accepted, Some(event)).run(xa) // FIXME track user & date

  override def cancel(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Pending, None).run(xa) // FIXME track user & date + check event id was set

  override def reject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Declined, None).run(xa) // FIXME track user & date

  override def reject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Declined, None).run(xa) // FIXME track user & date

  override def cancelReject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Pending, None).run(xa) // FIXME track user & date

  override def rate(cfp: Cfp.Slug, id: Proposal.Id, grade: Proposal.Rating.Grade)(implicit ctx: OrgaCtx): IO[Done] =
    selectOneRating(id, ctx.user.id).runOption(xa).flatMap {
      case Some(_) => update(Proposal.Rating(id, grade, ctx.now, ctx.user.id)).run(xa)
      case None => insert(Proposal.Rating(id, grade, ctx.now, ctx.user.id)).run(xa).map(_ => Done)
    }

  override def find(proposal: Proposal.Id): IO[Option[Proposal]] = selectOne(proposal).runOption(xa)

  override def find(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal]] = selectOne(cfp, id).runOption(xa)

  override def find(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): IO[Option[Proposal]] = selectOne(ctx.user.id, talk, cfp).runOption(xa)

  override def findFull(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Option[Proposal.Full]] = selectOneFull(cfp, id).runOption(xa)

  override def findFull(proposal: Proposal.Id)(implicit ctx: UserCtx): IO[Option[Proposal.Full]] = selectOneFull(proposal).runOption(xa)

  override def findFull(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): IO[Option[Proposal.Full]] = selectOneFull(talk, cfp).runOption(xa)

  override def findPublicFull(group: Group.Id, proposal: Proposal.Id)(implicit ctx: UserAwareCtx): IO[Option[Proposal.Full]] = selectOnePublicFull(group, proposal).runOption(xa)

  override def listFull(cfp: Cfp.Slug, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(cfp, params).run(xa)

  override def listFull(cfp: Cfp.Slug, status: Proposal.Status, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(cfp, status, params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(params).run(xa)

  override def listFull(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Proposal.Full]] = selectPageFull(talk, params).run(xa)

  override def listFull(speaker: User.Id, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(speaker, params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Proposal.Full]] = selectPageFullSpeaker(params).run(xa)

  override def listAllPublicIds()(implicit ctx: UserAwareCtx): IO[List[(Group.Id, Proposal.Id)]] = selectAllPublicIds().runList(xa)

  override def listAllPublicFull(speaker: User.Id)(implicit ctx: UserAwareCtx): IO[List[Proposal.Full]] = selectAllFullPublic(speaker).runList(xa)

  override def listPublicFull(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Proposal.Full]] = selectPageFullPublic(group, params).run(xa)

  override def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]] = runNel(selectAll, ids)

  override def listFull(ids: Seq[Proposal.Id])(implicit ctx: UserAwareCtx): IO[Seq[Proposal.Full]] = runNel(selectAllFull, ids)

  override def listPublic(ids: Seq[Proposal.Id]): IO[Seq[Proposal]] = runNel(selectAllPublic, ids)

  override def listPublicFull(ids: Seq[Proposal.Id])(implicit ctx: UserAwareCtx): IO[Seq[Proposal.Full]] = runNel[Proposal.Id, Proposal.Full](selectAllFullPublic(_), ids)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)

  override def listOrgaTags()(implicit ctx: OrgaCtx): IO[Seq[Tag]] = selectOrgaTags(ctx.group.id).runList(xa).map(_.flatten.distinct)

  override def listRatings(id: Proposal.Id): IO[Seq[Proposal.Rating.Full]] = selectAllRatings(id).runList(xa)

  override def listRatings(cfp: Cfp.Slug)(implicit ctx: OrgaCtx): IO[Seq[Proposal.Rating]] = selectAllRatings(cfp, ctx.user.id).runList(xa)

  override def listRatings(proposals: Seq[Proposal.Id])(implicit ctx: OrgaCtx): IO[Seq[Proposal.Rating]] =
    proposals.toNel.map(selectAllRatings(ctx.user.id, _).runList(xa)).getOrElse(IO.pure(Seq()))
}

object ProposalRepoSql {
  private val _ = proposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.proposals
  private val ratingTable = Tables.proposalRatings
  private val tableWithCfp = table
    .join(Tables.cfps, _.cfp_id -> _.id).get
  private val tableWithEvent = table
    .joinOpt(Tables.events, _.event_id -> _.id).get
    .dropFields(_.prefix == Tables.events.prefix)
  val tableFullBase: Table = table
    .join(Tables.cfps, _.cfp_id -> _.id).get
    .join(Tables.groups.dropFields(_.name.startsWith("location_")), _.group_id("c") -> _.id).get
    .join(Tables.talks, _.talk_id("p") -> _.id).get
    .joinOpt(Tables.events, _.event_id("p") -> _.id).get
    .joinOpt(Tables.venues.dropFields(_.name.startsWith("address_")), _.venue("e") -> _.id).get
    .joinOpt(Tables.partners, _.partner_id("v") -> _.id).get
    .joinOpt(Tables.contacts, _.contact_id("v") -> _.id).get
    .joinOpt(Tables.comments.setPrefix("sco"), fr0"sco.kind=${Comment.Kind.Proposal: Comment.Kind}", _.id("p") -> _.proposal_id).get.dropFields(_.prefix == "sco")
    .joinOpt(Tables.comments.setPrefix("oco"), fr0"oco.kind=${Comment.Kind.ProposalOrga: Comment.Kind}", _.id("p") -> _.proposal_id).get.dropFields(_.prefix == "oco")
    .aggregate(s"COALESCE(COUNT(DISTINCT sco.id), 0)", "speakerCommentCount")
    .aggregate(s"MAX(sco.created_at)", "speakerLastComment")
    .aggregate(s"COALESCE(COUNT(DISTINCT oco.id), 0)", "orgaCommentCount")
    .aggregate(s"MAX(oco.created_at)", "orgaLastComment")
    .addField(CustomField(fr0"(SELECT COALESCE(SUM(grade), 0) FROM proposal_ratings WHERE proposal_id=p.id)", "score"))
    .addField(CustomField(fr0"(SELECT COUNT(grade) FROM proposal_ratings WHERE proposal_id=p.id AND grade=${Proposal.Rating.Grade.Like: Proposal.Rating.Grade})", "likes"))
    .addField(CustomField(fr0"(SELECT COUNT(grade) FROM proposal_ratings WHERE proposal_id=p.id AND grade=${Proposal.Rating.Grade.Dislike: Proposal.Rating.Grade})", "dislikes"))
    .copy(filters = List(
      Table.Filter.Enum.fromEnum("status", "Status", "p.status", Seq(
        "pending" -> Proposal.Status.Pending.value,
        "accepted" -> Proposal.Status.Accepted.value,
        "declined" -> Proposal.Status.Declined.value)),
      Table.Filter.Bool.fromNullable("slides", "With slides", "p.slides"),
      Table.Filter.Bool.fromNullable("video", "With video", "p.video"),
      Table.Filter.Bool.fromCountExpr("comment", "With comments", "COALESCE(COUNT(DISTINCT sco.id), 0) + COALESCE(COUNT(DISTINCT oco.id), 0)"),
      Table.Filter.Value.fromField("tags", "With tag", "p.tags"),
      Table.Filter.Value.fromField("orga-tags", "With orga tag", "p.orga_tags")))
    .setSorts(
      Table.Sort("score", Field("-(SELECT COALESCE(SUM(grade), 0) FROM proposal_ratings WHERE proposal_id=p.id)", ""), Field("-created_at", "p")),
      Table.Sort("title", Field("LOWER(p.title)", "")),
      Table.Sort("comment", "last comment", Field("-MAX(GREATEST(sco.created_at, oco.created_at))", "")),
      Table.Sort("created", Field("-created_at", "p")),
      Table.Sort("updated", Field("-updated_at", "p")))

  private def userGrade(user: User) = fr0"(SELECT grade FROM proposal_ratings WHERE created_by=${user.id} AND proposal_id=p.id)"

  private def tableFull(user: Option[User]): Table = tableFullBase.addField(CustomField(user.map(userGrade).getOrElse(fr0"null"), "user_grade"))

  private def tableFull(user: User): Table = tableFull(Some(user))

  private val ratingTableFull = ratingTable
    .join(Tables.users, _.created_by -> _.id).get
    .join(Tables.proposals, _.proposal_id -> _.id).get
  private val ratingTableWithProposalCfp = ratingTable
    .join(table, _.proposal_id -> _.id).get
    .join(Tables.cfps, _.cfp_id -> _.id).get
    .dropFields(_.prefix == table.prefix)
    .dropFields(_.prefix == Tables.cfps.prefix)

  private[sql] def insert(e: Proposal): Query.Insert[Proposal] = {
    val values = fr0"${e.id}, ${e.talk}, ${e.cfp}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.orgaTags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Proposal](e, _ => values)
  }

  private[sql] def insert(e: Proposal.Rating): Query.Insert[Proposal.Rating] =
    ratingTable.insert[Proposal.Rating](e, _ => fr0"${e.proposal}, ${e.grade.value}, ${e.createdAt}, ${e.createdBy}")

  private[sql] def update(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(data: Proposal.DataOrga, now: Instant): Query.Update = {
    val fields = fr0"title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, tags=${data.tags}, orga_tags=${data.orgaTags}, updated_at=$now, updated_by=$orga"
    table.update(fields).where(where(orga, group, cfp, proposal))
  }

  private[sql] def update(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(data: Proposal.Data, now: Instant): Query.Update = {
    val fields = fr0"title=${data.title}, duration=${data.duration}, description=${data.description}, message=${data.message}, slides=${data.slides}, video=${data.video}, tags=${data.tags}, updated_at=$now, updated_by=$speaker"
    table.update(fields).where(where(speaker, talk, cfp))
  }

  private[sql] def update(e: Proposal.Rating): Query.Update =
    ratingTable.update(fr"grade=${e.grade.value}, created_at=${e.createdAt}").where(fr0"pr.proposal_id=${e.proposal} AND pr.created_by=${e.createdBy}")

  private[sql] def updateStatus(cfp: Cfp.Slug, id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): Query.Update =
    table.update(fr0"status=$status, event_id=$event").where(where(cfp, id))

  private[sql] def updateSlides(cfp: Cfp.Slug, id: Proposal.Id)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update =
    table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by").where(where(cfp, id))

  private[sql] def updateSlides(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update =
    table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by").where(where(speaker, talk, cfp))

  private[sql] def updateVideo(cfp: Cfp.Slug, id: Proposal.Id)(video: Url.Video, by: User.Id, now: Instant): Query.Update =
    table.update(fr0"video=$video, updated_at=$now, updated_by=$by").where(where(cfp, id))

  private[sql] def updateVideo(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(video: Url.Video, by: User.Id, now: Instant): Query.Update =
    table.update(fr0"video=$video, updated_at=$now, updated_by=$by").where(where(speaker, talk, cfp))

  private[sql] def updateOrgaTags(cfp: Cfp.Slug, id: Proposal.Id)(orgaTags: Seq[Tag], by: User.Id, now: Instant): Query.Update =
    table.update(fr0"orga_tags=$orgaTags").where(where(cfp, id))

  private[sql] def updateSpeakers(id: Proposal.Id)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update =
    table.update(fr0"speakers=$speakers, updated_at=$now, updated_by=$by").where(fr0"p.id=$id")

  private[sql] def selectOne(id: Proposal.Id): Query.Select[Proposal] =
    table.select[Proposal].where(where(id))

  private[sql] def selectOne(cfp: Cfp.Slug, id: Proposal.Id): Query.Select[Proposal] =
    table.select[Proposal].where(where(cfp, id))

  private[sql] def selectOne(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Query.Select[Proposal] =
    table.select[Proposal].where(where(speaker, talk, cfp))

  private[sql] def selectOneFull(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): Query.Select[Proposal.Full] =
    tableFull(ctx.user).select[Proposal.Full].where(where(cfp, id))

  private[sql] def selectOneFull(id: Proposal.Id)(implicit ctx: UserCtx): Query.Select[Proposal.Full] =
    tableFull(ctx.user).select[Proposal.Full].where(fr0"p.id=$id")

  private[sql] def selectOneFull(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): Query.Select[Proposal.Full] =
    tableFull(ctx.user).select[Proposal.Full].where(fr0"t.slug=$talk AND c.slug=$cfp AND p.speakers LIKE ${"%" + ctx.user.id.value + "%"}")

  private[sql] def selectOnePublicFull(group: Group.Id, id: Proposal.Id)(implicit ctx: UserAwareCtx): Query.Select[Proposal.Full] =
    tableFull(ctx.user).select[Proposal.Full].where(fr0"c.group_id=$group AND p.id=$id AND e.published IS NOT NULL")

  private[sql] def selectPageFull(cfp: Cfp.Slug, params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Proposal.Full] =
    tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"c.slug=$cfp")

  private[sql] def selectPageFull(cfp: Cfp.Slug, status: Proposal.Status, params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Proposal.Full] =
    tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"c.slug=$cfp AND p.status=$status")

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Proposal.Full] =
    tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"c.group_id=${ctx.group.id}")

  private[sql] def selectPageFull(speaker: User.Id, params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Proposal.Full] =
    tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"c.group_id=${ctx.group.id} AND p.speakers LIKE ${"%" + speaker.value + "%"}")

  private[sql] def selectPageFull(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[Proposal.Full] =
    tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"p.talk_id=$talk")

  private[sql] def selectPageFullSpeaker(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[Proposal.Full] =
    tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"p.speakers LIKE ${"%" + ctx.user.id.value + "%"}")

  private[sql] def selectAllPublicIds()(implicit ctx: UserAwareCtx): Query.Select[(Group.Id, Proposal.Id)] =
    tableWithEvent.select[(Group.Id, Proposal.Id)].fields(Field("group_id", "e"), Field("id", "p")).where(fr0"e.published IS NOT NULL")

  private[sql] def selectAllFullPublic(speaker: User.Id)(implicit ctx: UserAwareCtx): Query.Select[Proposal.Full] =
    tableFull(ctx.user).select[Proposal.Full].where(fr0"p.speakers LIKE ${"%" + speaker.value + "%"} AND e.published IS NOT NULL").sort(Table.Sort("created", Field("-created_at", "p")))

  private[sql] def selectPageFullPublic(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[Proposal.Full] =
    tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"e.group_id=$group AND e.published IS NOT NULL")

  private[sql] def selectAll(ids: NonEmptyList[Proposal.Id]): Query.Select[Proposal] =
    table.select[Proposal].where(Fragments.in(fr"id", ids))

  private[sql] def selectAllFull(ids: NonEmptyList[Proposal.Id])(implicit ctx: UserAwareCtx): Query.Select[Proposal.Full] =
    tableFull(ctx.user).select[Proposal.Full].where(Fragments.in(fr"p.id", ids))

  private[sql] def selectAllPublic(ids: NonEmptyList[Proposal.Id]): Query.Select[Proposal] =
    tableWithEvent.select[Proposal].where(Fragments.in(fr"p.id", ids) ++ fr0"AND e.published IS NOT NULL")

  private[sql] def selectAllFullPublic(ids: NonEmptyList[Proposal.Id])(implicit ctx: UserAwareCtx): Query.Select[Proposal.Full] =
    tableFull(ctx.user).select[Proposal.Full].where(Fragments.in(fr"p.id", ids) ++ fr0"AND e.published IS NOT NULL")

  private[sql] def selectTags(): Query.Select[Seq[Tag]] =
    table.select[Seq[Tag]].fields(Field("tags", "p"))

  private[sql] def selectOrgaTags(group: Group.Id): Query.Select[Seq[Tag]] =
    tableWithCfp.select[Seq[Tag]].fields(Field("orga_tags", "p")).where(fr0"c.group_id=$group")

  private[sql] def selectOneRating(id: Proposal.Id, user: User.Id): Query.Select[Proposal.Rating] =
    ratingTable.select[Proposal.Rating].where(fr0"pr.proposal_id=$id AND pr.created_by=$user")

  private[sql] def selectAllRatings(id: Proposal.Id): Query.Select[Proposal.Rating.Full] =
    ratingTableFull.select[Proposal.Rating.Full].where(fr0"pr.proposal_id=$id")

  private[sql] def selectAllRatings(cfp: Cfp.Slug, user: User.Id): Query.Select[Proposal.Rating] =
    ratingTableWithProposalCfp.select[Proposal.Rating].where(fr0"c.slug=$cfp AND pr.created_by=$user")

  private[sql] def selectAllRatings(user: User.Id, proposals: NonEmptyList[Proposal.Id]): Query.Select[Proposal.Rating] =
    ratingTable.select[Proposal.Rating].where(Fragments.in(fr"pr.proposal_id", proposals) ++ fr0"AND pr.created_by=$user")

  private def where(id: Proposal.Id): Fragment =
    fr0"p.id=$id"

  private def where(cfp: Cfp.Slug, id: Proposal.Id): Fragment =
    fr0"p.id=(SELECT p.id FROM " ++ table.value ++
      fr0" INNER JOIN " ++ Tables.cfps.value ++ fr0" ON p.cfp_id=c.id" ++
      fr0" WHERE p.id=$id AND c.slug=$cfp" ++ fr0")"

  private def where(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Fragment =
    fr0"p.id=(SELECT p.id FROM " ++ table.value ++
      fr0" INNER JOIN " ++ Tables.cfps.value ++ fr0" ON p.cfp_id=c.id" ++
      fr0" INNER JOIN " ++ Tables.groups.value ++ fr0" ON c.group_id=g.id" ++
      fr0" WHERE p.id=$proposal AND c.slug=$cfp AND g.slug=$group AND g.owners LIKE ${"%" + orga.value + "%"}" ++ fr0")"

  private def where(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Fragment =
    fr0"p.id=(SELECT p.id FROM " ++ table.value ++
      fr0" INNER JOIN " ++ Tables.cfps.value ++ fr0" ON p.cfp_id=c.id" ++
      fr0" INNER JOIN " ++ Tables.talks.value ++ fr0" ON p.talk_id=t.id" ++
      fr0" WHERE c.slug=$cfp AND t.slug=$talk AND p.speakers LIKE ${"%" + speaker.value + "%"}" ++ fr0")"

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
