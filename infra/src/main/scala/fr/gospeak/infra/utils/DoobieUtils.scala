package fr.gospeak.infra.utils

import java.time.{Instant, LocalDateTime, ZoneOffset}

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import doobie.Transactor
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import doobie.util.{Meta, Read, Write}
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup}
import fr.gospeak.core.services.slack.domain.SlackAction
import fr.gospeak.infra.services.storage.sql.DatabaseConf
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}
import fr.gospeak.libs.scalautils.domain._
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

object DoobieUtils {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def transactor(conf: DatabaseConf): doobie.Transactor[IO] = conf match {
    case c: DatabaseConf.H2 => Transactor.fromDriverManager[IO]("org.h2.Driver", c.url, "", "")
    case c: DatabaseConf.PostgreSQL => Transactor.fromDriverManager[IO]("org.postgresql.Driver", c.url, c.user, c.pass.decode)
  }

  final case class Field(name: String, prefix: String) {
    def value: String = if (prefix.isEmpty) name else s"$prefix.$name"
  }

  // FIXME use `case class TableJoin(table: Table, left: Field, right: Field)` for joins
  final case class Table(name: String,
                         prefix: String,
                         fields: Seq[Field],
                         sort: Page.OrderBy,
                         search: Seq[Field],
                         hasJoin: Boolean) {
    def field(field: Field): Either[CustomException, Field] = fields.find(_ == field).toEither(CustomException(s"Missing field '${field.value}' in table '$name'"))

    def field(name: String, prefix: String): Either[CustomException, Field] = field(Field(name, prefix))

    def field(name: String): Either[CustomException, Field] =
      fields.findOne(_.name == name).leftMap(e => CustomException(s"Unable to get field '$name' in table '${this.name}'", Seq(CustomError(e.message))))

    def dropField(field: Field): Either[CustomException, Table] =
      this.field(field).map(_ => copy(fields = fields.filterNot(f => f.name == field.name && f.prefix == field.prefix)))

    def dropField(f: Table => Either[CustomException, Field]): Either[CustomException, Table] = f(this).flatMap(dropField)

    def dropFields(p: Field => Boolean): Table = copy(fields = fields.filterNot(p))

    def join(rightTable: Table, leftField: Table => Either[CustomException, Field], rightField: Table => Either[CustomException, Field]): Either[CustomException, Table] =
      join("INNER JOIN", rightTable, leftField, rightField)

    def joinOpt(rightTable: Table, leftField: Table => Either[CustomException, Field], rightField: Table => Either[CustomException, Field]): Either[CustomException, Table] =
      join("LEFT OUTER JOIN", rightTable, leftField, rightField)

    private def join(kind: String, rightTable: Table, leftField: Table => Either[CustomException, Field], rightField: Table => Either[CustomException, Field]): Either[CustomException, Table] = for {
      left <- leftField(this)
      right <- rightField(rightTable)
      res <- Table.from(
        name = s"$name $kind ${rightTable.name} ON ${left.value}=${right.value}",
        prefix = prefix,
        fields = fields ++ rightTable.fields,
        sort = sort,
        search = search ++ rightTable.search,
        hasJoin = true)
    } yield res

    def insert[A](value: A, build: A => Fragment): Insert[A] = Insert[A](name.split(" ").head, fields, value, build)

    def insertPartial[A](fields: Seq[Field], value: A, build: A => Fragment): Insert[A] = Insert[A](name.split(" ").head, fields, value, build)

    def update(fields: Fragment, where: Fragment): Update = Update(name, fields, where)

    def delete(where: Fragment): Delete = Delete(name, where)

    def select[A: Read](where: Fragment): Select[A] = Select[A](name, fields, Some(where))

    def select[A: Read](fields: Seq[Field]): Select[A] = Select[A](name, fields, None)

    def select[A: Read](fields: Seq[Field], where: Fragment): Select[A] = Select[A](name, fields, Some(where))

    def selectPage[A: Read](params: Page.Params): SelectPage[A] = SelectPage[A](name, fields, params, sort, search)

    def selectPage[A: Read](params: Page.Params, where: Fragment): SelectPage[A] = SelectPage[A](name, fields, where, params, sort, search)
  }

  object Table {
    def from(name: String, prefix: String, fields: Seq[String], sort: Seq[String], search: Seq[String]): Either[CustomException, Table] =
      from(
        name = s"$name $prefix",
        prefix = prefix,
        fields = fields.map(f => Field(f, prefix)),
        sort = Page.OrderBy(sort).prefix(prefix),
        search = search.map(f => Field(f, prefix)),
        hasJoin = false)

    def from(name: String, prefix: String, fields: Seq[Field], sort: Page.OrderBy, search: Seq[Field], hasJoin: Boolean): Either[CustomException, Table] = {
      val duplicateFields = fields.diff(fields.distinct).distinct
      val invalidSort = sort.values.map(_.stripPrefix("-")).diff(fields.map(_.value))
      val invalidSearch = search.diff(fields)
      val errors = Seq(
        duplicateFields.headOption.map(_ => CustomError(s"fields ${duplicateFields.map(s"'" + _.value + "'").mkString(", ")} are duplicated")),
        invalidSort.headOption.map(_ => CustomError(s"sorts ${invalidSort.map(s"'" + _ + "'").mkString(", ")} do not exist in fields")),
        invalidSearch.headOption.map(_ => CustomError(s"searches ${invalidSearch.map(s"'" + _.value + "'").mkString(", ")} do not exist in fields"))
      ).flatten
      errors.headOption.map(_ => CustomException("Invalid Table", errors)).map(Left(_)).getOrElse(Right(new Table(
        name = name,
        prefix = prefix,
        fields = fields,
        sort = sort,
        search = search,
        hasJoin = hasJoin)))
    }
  }

  final case class Insert[A](table: String, fields: Seq[Field], value: A, build: A => Fragment) {
    def fr: Fragment = const0(s"INSERT INTO $table (${fields.map(_.name).mkString(", ")}) VALUES (") ++ build(value) ++ fr0")"

    def run(xa: doobie.Transactor[IO]): IO[A] =
      fr.update.run.transact(xa).flatMap {
        case 1 => IO.pure(value)
        case code => IO.raiseError(CustomException(s"Failed to insert $value in $table (code: $code)"))
      }
  }

  final case class Update(table: String, fields: Fragment, where: Fragment) {
    def fr: Fragment = const0(s"UPDATE $table SET ") ++ fields ++ fr0" " ++ where

    def run(xa: doobie.Transactor[IO]): IO[Done] =
      fr.update.run.transact(xa).flatMap {
        case 1 => IO.pure(Done)
        case code => IO.raiseError(CustomException(s"Failed to update ${fr.update} (code: $code)"))
      }
  }

  final case class Delete(table: String, where: Fragment) {
    def fr: Fragment = const0(s"DELETE FROM $table ") ++ where

    def run(xa: doobie.Transactor[IO]): IO[Done] =
      fr.update.run.transact(xa).flatMap {
        case 1 => IO.pure(Done)
        case code => IO.raiseError(CustomException(s"Failed to delete ${fr.update} (code: $code)"))
      }
  }

  // FIXME add sort!!!
  final case class Select[A: Read](table: String, fields: Seq[Field], where: Option[Fragment]) {
    def fr: Fragment = const0(s"SELECT ${fields.map(_.value).mkString(", ")} FROM $table") ++ where.map(fr0" " ++ _).getOrElse(fr0"")

    def query: doobie.Query0[A] = fr.query[A]

    def runList(xa: doobie.Transactor[IO]): IO[List[A]] = query.to[List].transact(xa)

    def runOption(xa: doobie.Transactor[IO]): IO[Option[A]] = query.option.transact(xa)

    def runUnique(xa: doobie.Transactor[IO]): IO[A] = query.unique.transact(xa)

    def runExists(xa: doobie.Transactor[IO]): IO[Boolean] = query.option.map(_.isDefined).transact(xa)
  }

  final case class SelectPage[A: Read](table: String,
                                       fields: Seq[Field],
                                       where: Fragment,
                                       countWhere: Fragment,
                                       params: Page.Params) {
    def fr: Fragment = Select[A](table, fields, Some(where)).fr

    def query: doobie.Query0[A] = fr.query[A]

    def countFr: Fragment = Select[A](table, Seq(Field("count(*)", "")), Some(countWhere)).fr

    def countQuery: doobie.Query0[Long] = countFr.query[Long]

    def run(xa: doobie.Transactor[IO]): IO[Page[A]] = (for {
      elts <- query.to[List]
      total <- countQuery.unique
    } yield Page(elts, params, Page.Total(total))).transact(xa)
  }

  object SelectPage {
    def apply[A: Read](table: String, fields: Seq[Field], params: Page.Params, orderBy: Page.OrderBy, search: Seq[Field]): SelectPage[A] =
      apply[A](table, fields, None, params, orderBy, search, None)

    def apply[A: Read](table: String, fields: Seq[Field], where: Fragment, params: Page.Params, orderBy: Page.OrderBy, search: Seq[Field]): SelectPage[A] =
      apply[A](table, fields, Some(where), params, orderBy, search, None)

    def apply[A: Read](table: String, fields: Seq[Field], whereOpt: Option[Fragment], params: Page.Params, defaultSort: Page.OrderBy, searchFields: Seq[Field], prefix: Option[String]): SelectPage[A] = {
      SelectPage(
        table = table,
        fields = fields,
        where = paginationFragment(whereOpt, params, searchFields, defaultSort, prefix),
        countWhere = whereFragment(whereOpt, params.search, searchFields),
        params = params)
    }
  }

  def whereFragment(whereOpt: Option[Fragment], search: Option[Page.Search], fields: Seq[Field]): Fragment = {
    search.filter(_ => fields.nonEmpty)
      .map { search => fields.map(s => const0(s.value + " ") ++ fr0"ILIKE ${"%" + search.value + "%"} ").reduce(_ ++ fr0"OR " ++ _) }
      .map { search => whereOpt.map(_ ++ fr0" AND " ++ fr0"(" ++ search ++ fr0") ").getOrElse(fr0"WHERE " ++ search) }
      .orElse(whereOpt.map(_ ++ fr0" "))
      .getOrElse(fr0"")
  }

  def orderByFragment(orderBy: Page.OrderBy, prefix: Option[String] = None, nullsFirst: Boolean = false): Fragment = {
    if (orderBy.nonEmpty) {
      val fields = orderBy.values.map { v =>
        val p = prefix.map(_ + ".").getOrElse("")
        val f = p + v.stripPrefix("-")
        if (nullsFirst) {
          s"$f IS NOT NULL, $f" + (if (v.startsWith("-")) " DESC" else "")
        } else {
          s"$f IS NULL, $f" + (if (v.startsWith("-")) " DESC" else "")
        }
      }
      fr0"ORDER BY " ++ const0(fields.mkString(", ") + " ")
    } else {
      fr0""
    }
  }

  def limitFragment(start: Int, size: Page.Size): Fragment =
    fr0"OFFSET " ++ const0(start.toString + " ") ++ fr0"LIMIT " ++ const0(size.value.toString)

  def paginationFragment(whereOpt: Option[Fragment], params: Page.Params, searchFields: Seq[Field], defaultSort: Page.OrderBy, prefix: Option[String]): Fragment = {
    val where = whereFragment(whereOpt, params.search, searchFields)
    val orderBy = orderByFragment(params.orderBy.getOrElse(defaultSort), prefix, params.nullsFirst)
    val limit = limitFragment(params.offsetStart, params.pageSize)
    Seq(where, orderBy, limit).reduce(_ ++ _)
  }

  object Queries {
    def insertOne[A](insert: A => doobie.Update0)(elt: A)(implicit w: Write[A]): doobie.ConnectionIO[Int] =
      insert(elt).run

    def insertMany[A](insert: A => doobie.Update0)(elts: NonEmptyList[A])(implicit w: Write[A]): doobie.ConnectionIO[Int] =
      doobie.util.update.Update[A](insert(elts.head).sql).updateMany(elts)
  }

  object Mappings {

    import scala.reflect.runtime.universe._

    implicit val timePeriodMeta: Meta[TimePeriod] = Meta[String].timap(TimePeriod.from(_).get)(_.value)
    implicit val finiteDurationMeta: Meta[FiniteDuration] = Meta[Long].timap(Duration.fromNanos)(_.toNanos)
    implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Instant].timap(LocalDateTime.ofInstant(_, ZoneOffset.UTC))(_.toInstant(ZoneOffset.UTC))
    implicit val emailAddressMeta: Meta[EmailAddress] = Meta[String].timap(EmailAddress.from(_).get)(_.value)
    implicit val urlMeta: Meta[Url] = Meta[String].timap(Url.from(_).get)(_.value)
    implicit val slidesMeta: Meta[Slides] = Meta[String].timap(Slides.from(_).get)(_.value)
    implicit val videoMeta: Meta[Video] = Meta[String].timap(Video.from(_).get)(_.value)
    implicit val currencyMeta: Meta[Price.Currency] = Meta[String].timap(Price.Currency.from(_).get)(_.value)
    implicit val markdownMeta: Meta[Markdown] = Meta[String].timap(Markdown)(_.value)
    implicit def mustacheMarkdownTemplateMeta[A: TypeTag]: Meta[MustacheMarkdownTmpl[A]] = Meta[String].timap(MustacheMarkdownTmpl[A])(_.value)
    implicit def mustacheTextTemplateMeta[A: TypeTag]: Meta[MustacheTextTmpl[A]] = Meta[String].timap(MustacheTextTmpl[A])(_.value)
    implicit val avatarSourceMeta: Meta[Avatar.Source] = Meta[String].timap(Avatar.Source.from(_).get)(_.toString)
    implicit val tagsMeta: Meta[Seq[Tag]] = Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Tag(_)).toSeq)(_.map(_.value).mkString(","))
    implicit val gMapPlaceMeta: Meta[GMapPlace] = {
      implicit val geoDecoder: Decoder[Geo] = deriveDecoder[Geo]
      implicit val geoEncoder: Encoder[Geo] = deriveEncoder[Geo]
      implicit val gMapPlaceDecoder: Decoder[GMapPlace] = deriveDecoder[GMapPlace]
      implicit val gMapPlaceEncoder: Encoder[GMapPlace] = deriveEncoder[GMapPlace]
      Meta[String].timap(fromJson[GMapPlace](_).get)(toJson)
    }
    implicit val groupSettingsEventTemplatesMeta: Meta[Map[String, MustacheTextTmpl[TemplateData.EventInfo]]] = {
      implicit val textTemplateDecoder: Decoder[MustacheTextTmpl[TemplateData.EventInfo]] = deriveDecoder[MustacheTextTmpl[TemplateData.EventInfo]]
      implicit val textTemplateEncoder: Encoder[MustacheTextTmpl[TemplateData.EventInfo]] = deriveEncoder[MustacheTextTmpl[TemplateData.EventInfo]]
      Meta[String].timap(fromJson[Map[String, MustacheTextTmpl[TemplateData.EventInfo]]](_).get)(toJson)
    }
    implicit val groupSettingsActionsMeta: Meta[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]] = {
      implicit val markdownTemplateDecoder: Decoder[MustacheMarkdownTmpl[TemplateData]] = deriveDecoder[MustacheMarkdownTmpl[TemplateData]]
      implicit val markdownTemplateEncoder: Encoder[MustacheMarkdownTmpl[TemplateData]] = deriveEncoder[MustacheMarkdownTmpl[TemplateData]]
      implicit val slackActionPostMessageDecoder: Decoder[SlackAction.PostMessage] = deriveDecoder[SlackAction.PostMessage]
      implicit val slackActionPostMessageEncoder: Encoder[SlackAction.PostMessage] = deriveEncoder[SlackAction.PostMessage]
      implicit val slackActionDecoder: Decoder[SlackAction] = deriveDecoder[SlackAction]
      implicit val slackActionEncoder: Encoder[SlackAction] = deriveEncoder[SlackAction]
      implicit val groupSettingsActionSlackDecoder: Decoder[Group.Settings.Action.Slack] = deriveDecoder[Group.Settings.Action.Slack]
      implicit val groupSettingsActionSlackEncoder: Encoder[Group.Settings.Action.Slack] = deriveEncoder[Group.Settings.Action.Slack]
      implicit val groupSettingsActionDecoder: Decoder[Group.Settings.Action] = deriveDecoder[Group.Settings.Action]
      implicit val groupSettingsActionEncoder: Encoder[Group.Settings.Action] = deriveEncoder[Group.Settings.Action]
      implicit val groupSettingsActionTriggerDecoder: KeyDecoder[Group.Settings.Action.Trigger] = (key: String) => Group.Settings.Action.Trigger.from(key)
      implicit val groupSettingsActionTriggerEncoder: KeyEncoder[Group.Settings.Action.Trigger] = (e: Group.Settings.Action.Trigger) => e.toString
      Meta[String].timap(fromJson[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]](_).get)(toJson)
    }

    // TODO build Meta[Seq[A]] and Meta[NonEmptyList[A]]
    // implicit def seqMeta[A](implicit m: Meta[A]): Meta[Seq[A]] = ???
    // implicit def nelMeta[A](implicit m: Meta[A]): Meta[NonEmptyList[A]] = ???

    implicit val userIdMeta: Meta[User.Id] = Meta[String].timap(User.Id.from(_).get)(_.value)
    implicit val userSlugMeta: Meta[User.Slug] = Meta[String].timap(User.Slug.from(_).get)(_.value)
    implicit val userProfileStatusMeta: Meta[User.Profile.Status] = Meta[String].timap(User.Profile.Status.from(_).get)(_.value)
    implicit val userRequestIdMeta: Meta[UserRequest.Id] = Meta[String].timap(UserRequest.Id.from(_).get)(_.value)
    implicit val talkIdMeta: Meta[Talk.Id] = Meta[String].timap(Talk.Id.from(_).get)(_.value)
    implicit val talkSlugMeta: Meta[Talk.Slug] = Meta[String].timap(Talk.Slug.from(_).get)(_.value)
    implicit val talkTitleMeta: Meta[Talk.Title] = Meta[String].timap(Talk.Title)(_.value)
    implicit val talkStatusMeta: Meta[Talk.Status] = Meta[String].timap(Talk.Status.from(_).get)(_.value)
    implicit val groupIdMeta: Meta[Group.Id] = Meta[String].timap(Group.Id.from(_).get)(_.value)
    implicit val groupSlugMeta: Meta[Group.Slug] = Meta[String].timap(Group.Slug.from(_).get)(_.value)
    implicit val groupNameMeta: Meta[Group.Name] = Meta[String].timap(Group.Name)(_.value)
    implicit val eventIdMeta: Meta[Event.Id] = Meta[String].timap(Event.Id.from(_).get)(_.value)
    implicit val eventSlugMeta: Meta[Event.Slug] = Meta[String].timap(Event.Slug.from(_).get)(_.value)
    implicit val eventNameMeta: Meta[Event.Name] = Meta[String].timap(Event.Name)(_.value)
    implicit val cfpIdMeta: Meta[Cfp.Id] = Meta[String].timap(Cfp.Id.from(_).get)(_.value)
    implicit val cfpSlugMeta: Meta[Cfp.Slug] = Meta[String].timap(Cfp.Slug.from(_).get)(_.value)
    implicit val cfpNameMeta: Meta[Cfp.Name] = Meta[String].timap(Cfp.Name)(_.value)
    implicit val proposalIdMeta: Meta[Proposal.Id] = Meta[String].timap(Proposal.Id.from(_).get)(_.value)
    implicit val proposalStatusMeta: Meta[Proposal.Status] = Meta[String].timap(Proposal.Status.from(_).get)(_.value)
    implicit val partnerIdMeta: Meta[Partner.Id] = Meta[String].timap(Partner.Id.from(_).get)(_.value)
    implicit val partnerSlugMeta: Meta[Partner.Slug] = Meta[String].timap(Partner.Slug.from(_).get)(_.value)
    implicit val partnerNameMeta: Meta[Partner.Name] = Meta[String].timap(Partner.Name)(_.value)
    implicit val venueIdMeta: Meta[Venue.Id] = Meta[String].timap(Venue.Id.from(_).get)(_.value)
    implicit val sponsorPackIdMeta: Meta[SponsorPack.Id] = Meta[String].timap(SponsorPack.Id.from(_).get)(_.value)
    implicit val sponsorPackSlugMeta: Meta[SponsorPack.Slug] = Meta[String].timap(SponsorPack.Slug.from(_).get)(_.value)
    implicit val sponsorPackNameMeta: Meta[SponsorPack.Name] = Meta[String].timap(SponsorPack.Name)(_.value)
    implicit val sponsorIdMeta: Meta[Sponsor.Id] = Meta[String].timap(Sponsor.Id.from(_).get)(_.value)
    implicit val contactIdMeta: Meta[Contact.Id] = Meta[String].timap(Contact.Id.from(_).right.get)(_.value)
    implicit val meetupGroupSlugMeta: Meta[MeetupGroup.Slug] = Meta[String].timap(MeetupGroup.Slug.from(_).get)(_.value)
    implicit val meetupEventIdMeta: Meta[MeetupEvent.Id] = Meta[Long].timap(MeetupEvent.Id(_))(_.value)
    implicit val memberRoleMeta: Meta[Group.Member.Role] = Meta[String].timap(Group.Member.Role.from(_).get)(_.toString)
    implicit val rsvpAnswerMeta: Meta[Event.Rsvp.Answer] = Meta[String].timap(Event.Rsvp.Answer.from(_).get)(_.toString)

    implicit val userIdNelMeta: Meta[NonEmptyList[User.Id]] = Meta[String].timap(
      s => NonEmptyList.fromListUnsafe(s.split(",").filter(_.nonEmpty).map(User.Id.from(_).get).toList))(
      _.map(_.value).toList.mkString(","))
    implicit val proposalIdSeqMeta: Meta[Seq[Proposal.Id]] = Meta[String].timap(
      _.split(",").filter(_.nonEmpty).map(Proposal.Id.from(_).get).toSeq)(
      _.map(_.value).mkString(","))

    implicit val userRequestRead: Read[UserRequest] =
      Read[(UserRequest.Id, String, Option[Group.Id], Option[Talk.Id], Option[Proposal.Id], Option[EmailAddress], Option[Instant], Instant, Option[User.Id], Option[Instant], Option[User.Id], Option[Instant], Option[User.Id], Option[Instant], Option[User.Id])].map {
        case (id, "AccountValidation", _, _, _, Some(email), Some(deadline), created, Some(createdBy), accepted, _, _, _, _, _) =>
          UserRequest.AccountValidationRequest(id, email, deadline, created, createdBy, accepted)
        case (id, "PasswordReset", _, _, _, Some(email), Some(deadline), created, _, accepted, _, _, _, _, _) =>
          UserRequest.PasswordResetRequest(id, email, deadline, created, accepted)
        case (id, "UserAskToJoinAGroup", Some(groupId), _, _, _, _, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.UserAskToJoinAGroupRequest(id, groupId, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "GroupInvite", Some(groupId), _, _, Some(email), _, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.GroupInvite(id, groupId, email, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "TalkInvite", _, Some(talkId), _, Some(email), _, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.TalkInvite(id, talkId, email, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "ProposalInvite", _, _, Some(proposalId), Some(email), _, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.ProposalInvite(id, proposalId, email, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, kind, group, talk, proposal, email, deadline, created, createdBy, accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          throw new Exception(s"Unable to read UserRequest with ($id, $kind, group=$group, talk=$talk, proposal=$proposal, $email, $deadline, created=($created, $createdBy), accepted=($accepted, $acceptedBy), rejected=($rejected, $rejectedBy), canceled=($canceled, $canceledBy))")
      }

    private def toJson[A](v: A)(implicit e: Encoder[A]): String = e.apply(v).noSpaces

    private def fromJson[A](s: String)(implicit d: Decoder[A]): util.Try[A] = parser.parse(s).flatMap(d.decodeJson).toTry
  }

}
