package fr.gospeak.infra.services.storage.sql.utils

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0
import doobie.util.transactor.Transactor
import doobie.util.{Meta, Read}
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.SocialAccounts.SocialAccount._
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
import scala.language.dynamics

object DoobieUtils {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def transactor(conf: DatabaseConf): doobie.Transactor[IO] = conf match {
    case c: DatabaseConf.H2 => Transactor.fromDriverManager[IO]("org.h2.Driver", c.url, "", "")
    case c: DatabaseConf.PostgreSQL => Transactor.fromDriverManager[IO]("org.postgresql.Driver", c.url, c.user, c.pass.decode)
  }

  final case class Field(name: String, prefix: String) {
    def value: String = if (prefix.isEmpty) name else s"$prefix.$name"
  }

  final case class AggregateField(formula: String, name: String) {
    def value: String = s"$formula as $name"
  }

  final case class TableJoin(kind: String,
                             name: String,
                             prefix: String,
                             on: NonEmptyList[(Field, Field)]) {
    def value: String = s"$kind $name $prefix ON ${on.map { case (l, r) => s"${l.value}=${r.value}" }.toList.mkString(" AND ")}"
  }

  final case class Sorts(default: Seq[Field],
                         other: Map[String, Seq[Field]]) {
    def all: Seq[Seq[Field]] = default :: other.values.toList

    def names: Seq[String] = other.keySet.toList

    def get(orderBy: Option[Page.OrderBy], prefix: String): Seq[Field] = {
      orderBy.map { o =>
        o.values.flatMap { sort =>
          other.get(sort.stripPrefix("-")).map(_.map { field =>
            (sort.startsWith("-"), field.name.startsWith("-")) match {
              case (true, true) => field.copy(name = field.name.stripPrefix("-"))
              case (true, false) => field.copy(name = "-" + field.name)
              case (false, _) => field
            }
          }).getOrElse(Seq(Field(sort, prefix)))
        }
      }.getOrElse(default)
    }
  }

  final case class Table(name: String,
                         prefix: String,
                         joins: Seq[TableJoin],
                         fields: Seq[Field],
                         aggFields: Seq[AggregateField],
                         sorts: Sorts,
                         search: Seq[Field]) extends Dynamic {
    def value: String = s"$name $prefix" + joins.map(" " + _.value).mkString

    private def field(field: Field): Either[CustomException, Field] = fields.find(_ == field).toEither(CustomException(s"Unable to find field '${field.value}' in table '$value'"))

    def field(name: String, prefix: String): Either[CustomException, Field] = field(Field(name, prefix))

    def field(name: String): Either[CustomException, Field] =
      fields.filter(_.name == name) match {
        case Seq() => Left(CustomException(s"Unable to find field '$name' in table '$value'"))
        case Seq(f) => Right(f)
        case l => Left(CustomException(s"Ambiguous field '$name' (possible values: ${l.map(f => s"'${f.value}'").mkString(", ")}) in table '$value'"))
      }

    def selectDynamic(name: String): Either[CustomException, Field] = field(name)

    def applyDynamic(name: String)(prefix: String): Either[CustomException, Field] = field(name, prefix)

    def dropFields(p: Field => Boolean): Table = copy(fields = fields.filterNot(p), search = search.filterNot(p))

    def dropField(field: Field): Either[CustomException, Table] =
      this.field(field).map(_ => dropFields(f => f.name == field.name && f.prefix == field.prefix))

    def dropField(f: Table => Either[CustomException, Field]): Either[CustomException, Table] = f(this).flatMap(dropField)

    def join(rightTable: Table, on: Table.BuildJoinFields, more: Table.BuildJoinFields*): Either[CustomException, Table] =
      doJoin("INNER JOIN", rightTable, NonEmptyList.of(on, more: _*))

    def joinOpt(rightTable: Table, on: Table.BuildJoinFields, more: Table.BuildJoinFields*): Either[CustomException, Table] =
      doJoin("LEFT OUTER JOIN", rightTable, NonEmptyList.of(on, more: _*))

    private def doJoin(kind: String, rightTable: Table, on: NonEmptyList[Table.BuildJoinFields]): Either[CustomException, Table] = for {
      joinFields <- on
        .map(f => f(this, rightTable))
        .map { case (leftField, rightField) => leftField.flatMap(lf => rightField.map(rf => (lf, rf))) }.sequence
      res <- Table.from(
        name = name,
        prefix = prefix,
        joins = joins ++ Seq(TableJoin(kind, rightTable.name, rightTable.prefix, joinFields)) ++ rightTable.joins,
        fields = fields ++ rightTable.fields,
        aggFields = aggFields ++ rightTable.aggFields,
        sorts = sorts,
        search = search ++ rightTable.search)
    } yield res

    def aggregate(formula: String, name: String): Table = copy(aggFields = aggFields :+ AggregateField(formula, name))

    def setSorts(default: (String, Seq[Field]), other: (String, Seq[Field])*): Table = copy(sorts = Sorts(default._2, (default +: other).toMap))

    def insert[A](elt: A, build: A => Fragment): Insert[A] = Insert[A](name, fields, elt, build)

    def insertPartial[A](fields: Seq[Field], elt: A, build: A => Fragment): Insert[A] = Insert[A](name, fields, elt, build)

    def update(fields: Fragment, where: Fragment): Update = Update(value, fields, fr0" " ++ where)

    def delete(where: Fragment): Delete = Delete(value, fr0" " ++ where)

    def select[A: Read](where: Fragment): Select[A] = Select[A](value, fields, aggFields, Some(fr0" " ++ where), sorts, None)

    def select[A: Read](fields: Seq[Field]): Select[A] = Select[A](value, fields, aggFields, None, sorts, None)

    def select[A: Read](fields: Seq[Field], sort: Seq[Field]): Select[A] = Select[A](value, fields, aggFields, None, Sorts(sort, Map()), None)

    def select[A: Read](fields: Seq[Field], where: Fragment): Select[A] = Select[A](value, fields, aggFields, Some(fr0" " ++ where), sorts, None)

    def select[A: Read](fields: Seq[Field], where: Fragment, sort: Seq[Field]): Select[A] = Select[A](value, fields, aggFields, Some(fr0" " ++ where), Sorts(sort, Map()), None)

    def selectOne[A: Read](where: Fragment, sort: Seq[Field]): Select[A] = Select[A](value, fields, aggFields, Some(fr0" " ++ where), Sorts(sort, Map()), Some(1))

    def selectPage[A: Read](params: Page.Params): SelectPage[A] = SelectPage[A](value, prefix, fields, aggFields, None, params, sorts, search)

    def selectPage[A: Read](params: Page.Params, where: Fragment): SelectPage[A] = SelectPage[A](value, prefix, fields, aggFields, Some(fr0" " ++ where), params, sorts, search)
  }

  object Table {
    private type BuildJoinFields = (Table, Table) => (Either[CustomException, Field], Either[CustomException, Field])

    def from(name: String, prefix: String, fields: Seq[String], sort: Seq[String], search: Seq[String]): Either[CustomException, Table] =
      from(
        name = name,
        prefix = prefix,
        joins = Seq(),
        fields = fields.map(f => Field(f, prefix)),
        aggFields = Seq(),
        sorts = Sorts(sort.map(f => Field(f, prefix)), Map()),
        search = search.map(f => Field(f, prefix)))

    private def from(name: String, prefix: String, joins: Seq[TableJoin], fields: Seq[Field], aggFields: Seq[AggregateField], sorts: Sorts, search: Seq[Field]): Either[CustomException, Table] = {
      val duplicateFields = fields.diff(fields.distinct).distinct
      val invalidSort = sorts.all.flatten.map(s => s.copy(name = s.name.stripPrefix("-")).value).diff(fields.map(_.value) ++ aggFields.map(_.formula))
      val invalidSearch = search.diff(fields)
      val errors = Seq(
        duplicateFields.headOption.map(_ => s"fields ${duplicateFields.map(s"'" + _.value + "'").mkString(", ")} are duplicated"),
        invalidSort.headOption.map(_ => s"sorts ${invalidSort.map(s"'" + _ + "'").mkString(", ")} do not exist in fields"),
        invalidSearch.headOption.map(_ => s"searches ${invalidSearch.map(s"'" + _.value + "'").mkString(", ")} do not exist in fields")
      ).flatten.map(CustomError)
      errors.headOption.map(_ => CustomException("Invalid Table", errors)).map(Left(_)).getOrElse(Right(new Table(
        name = name,
        prefix = prefix,
        joins = joins,
        fields = fields,
        aggFields = aggFields,
        sorts = sorts,
        search = search)))
    }
  }

  final case class Insert[A](table: String, fields: Seq[Field], elt: A, build: A => Fragment) {
    def fr: Fragment = const0(s"INSERT INTO $table (${fields.map(_.name).mkString(", ")}) VALUES (") ++ build(elt) ++ fr0")"

    def run(xa: doobie.Transactor[IO]): IO[A] =
      fr.update.run.transact(xa).flatMap {
        case 1 => IO.pure(elt)
        case code => IO.raiseError(CustomException(s"Failed to insert $elt in $table (code: $code)"))
      }
  }

  final case class Update(table: String, fields: Fragment, where: Fragment) {
    def fr: Fragment = const0(s"UPDATE $table SET ") ++ fields ++ where

    def run(xa: doobie.Transactor[IO]): IO[Done] =
      fr.update.run.transact(xa).flatMap {
        case 1 => IO.pure(Done)
        case code => IO.raiseError(CustomException(s"Failed to update ${fr.update} (code: $code)"))
      }
  }

  final case class Delete(table: String, where: Fragment) {
    def fr: Fragment = const0(s"DELETE FROM $table") ++ where

    def run(xa: doobie.Transactor[IO]): IO[Done] =
      fr.update.run.transact(xa).flatMap {
        case 1 => IO.pure(Done)
        case code => IO.raiseError(CustomException(s"Failed to delete ${fr.update} (code: $code)"))
      }
  }

  final case class Select[A: Read](table: String,
                                   fields: Seq[Field],
                                   aggFields: Seq[AggregateField],
                                   whereOpt: Option[Fragment],
                                   sorts: Sorts,
                                   limit: Option[Int]) {
    def fr: Fragment = {
      val select = const0(s"SELECT ${(fields.map(_.value) ++ aggFields.map(_.value)).mkString(", ")} FROM $table")
      val where = whereOpt.getOrElse(fr0"")
      val groupBy = aggFields.headOption.map(_ => const0(s" GROUP BY ${fields.map(_.value).mkString(", ")}")).getOrElse(fr0"")
      val orderBy = NonEmptyList.fromList(sorts.default.toList).map(orderByFragment(_)).getOrElse(fr0"")
      select ++ where ++ groupBy ++ orderBy ++ limit.map(l => const0(s" LIMIT $l")).getOrElse(fr0"")
    }

    def query: doobie.Query0[A] = fr.query[A]

    def runList(xa: doobie.Transactor[IO]): IO[List[A]] = query.to[List].transact(xa)

    def runOption(xa: doobie.Transactor[IO]): IO[Option[A]] = query.option.transact(xa)

    def runUnique(xa: doobie.Transactor[IO]): IO[A] = query.unique.transact(xa)

    def runExists(xa: doobie.Transactor[IO]): IO[Boolean] = query.option.map(_.isDefined).transact(xa)
  }

  final case class SelectPage[A: Read](table: String,
                                       prefix: String,
                                       fields: Seq[Field],
                                       aggFields: Seq[AggregateField],
                                       whereOpt: Option[Fragment],
                                       params: Page.Params,
                                       sorts: Sorts,
                                       searchFields: Seq[Field]) {
    private val select: Fragment = const0(s"SELECT ${(fields.map(_.value) ++ aggFields.map(_.value)).mkString(", ")} FROM $table")
    private val groupBy: Fragment = aggFields.headOption.map(_ => const0(s" GROUP BY ${fields.map(_.value).mkString(", ")}")).getOrElse(fr0"")

    def fr: Fragment = {
      val (where, orderBy, limit) = paginationFragment(prefix, whereOpt, params, sorts, searchFields)
      select ++ where ++ groupBy ++ orderBy ++ limit
    }

    def countFr: Fragment = {
      val select = const0(s"SELECT ${fields.headOption.map(_.value).getOrElse("*")} FROM $table")
      val where = whereFragment(whereOpt, params.search, searchFields).getOrElse(fr0"")
      fr0"SELECT count(*) FROM (" ++ select ++ where ++ groupBy ++ fr0") as cnt"
    }

    def query: doobie.Query0[A] = fr.query[A]

    def countQuery: doobie.Query0[Long] = countFr.query[Long]

    def run(xa: doobie.Transactor[IO]): IO[Page[A]] = (for {
      elts <- query.to[List]
      total <- countQuery.unique
    } yield Page(elts, params, Page.Total(total), sorts.names)).transact(xa)
  }

  def whereFragment(whereOpt: Option[Fragment], search: Option[Page.Search], fields: Seq[Field]): Option[Fragment] = {
    search.filter(_ => fields.nonEmpty)
      .map { search => fields.map(s => const0(s.value + " ") ++ fr0"ILIKE ${"%" + search.value + "%"}").reduce(_ ++ fr0" OR " ++ _) }
      .map { search => whereOpt.map(_ ++ fr0" AND " ++ fr0"(" ++ search ++ fr0")").getOrElse(fr0"WHERE " ++ search) }
      .orElse(whereOpt)
  }

  def orderByFragment(sort: NonEmptyList[Field], nullsFirst: Boolean = false): Fragment = {
    val fields = sort.map { v =>
      val f = v.copy(name = v.name.stripPrefix("-")).value
      if (nullsFirst) {
        s"$f IS NOT NULL, $f" + (if (v.name.startsWith("-")) " DESC" else "")
      } else {
        s"$f IS NULL, $f" + (if (v.name.startsWith("-")) " DESC" else "")
      }
    }
    fr0" ORDER BY " ++ const0(fields.toList.mkString(", "))
  }

  def limitFragment(size: Page.Size, start: Page.Offset): Fragment =
    fr0" LIMIT " ++ const0(size.value.toString) ++ fr0" OFFSET " ++ const0(start.value.toString)

  def paginationFragment(prefix: String, whereOpt: Option[Fragment], params: Page.Params, sorts: Sorts, searchFields: Seq[Field]): (Fragment, Fragment, Fragment) = {
    val where = whereFragment(whereOpt, params.search, searchFields).getOrElse(fr0"")
    val sortFields = sorts.get(params.orderBy, prefix)
    val orderBy = NonEmptyList.fromList(sortFields.toList).map(orderByFragment(_, params.nullsFirst)).getOrElse(fr0"")
    val limit = limitFragment(params.pageSize, params.offset)
    (where, orderBy, limit)
  }

  object Mappings {

    import scala.reflect.runtime.universe._

    implicit val timePeriodMeta: Meta[TimePeriod] = Meta[String].timap(TimePeriod.from(_).get)(_.value)
    implicit val finiteDurationMeta: Meta[FiniteDuration] = Meta[Long].timap(Duration.fromNanos)(_.toNanos)
    implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Instant].timap(LocalDateTime.ofInstant(_, ZoneOffset.UTC))(_.toInstant(ZoneOffset.UTC))
    implicit val emailAddressMeta: Meta[EmailAddress] = Meta[String].timap(EmailAddress.from(_).get)(_.value)
    implicit val urlMeta: Meta[Url] = Meta[String].timap(Url.from(_).get)(_.value)
    implicit val logoMeta: Meta[Logo] = urlMeta.timap(Logo)(_.url)
    implicit val bannerMeta: Meta[Banner] = urlMeta.timap(Banner)(_.url)
    implicit val facebookAccountMeta: Meta[FacebookAccount] = urlMeta.timap(FacebookAccount)(_.url)
    implicit val instagramAccountMeta: Meta[InstagramAccount] = urlMeta.timap(InstagramAccount)(_.url)
    implicit val twitterAccountMeta: Meta[TwitterAccount] = urlMeta.timap(TwitterAccount)(_.url)
    implicit val linkedInAccountMeta: Meta[LinkedInAccount] = urlMeta.timap(LinkedInAccount)(_.url)
    implicit val youtubeAccountMeta: Meta[YoutubeAccount] = urlMeta.timap(YoutubeAccount)(_.url)
    implicit val meetupAccountMeta: Meta[MeetupAccount] = urlMeta.timap(MeetupAccount)(_.url)
    implicit val eventbriteAccountMeta: Meta[EventbriteAccount] = urlMeta.timap(EventbriteAccount)(_.url)
    implicit val slackAccountMeta: Meta[SlackAccount] = urlMeta.timap(SlackAccount)(_.url)
    implicit val discordAccountMeta: Meta[DiscordAccount] = urlMeta.timap(DiscordAccount)(_.url)
    implicit val slidesMeta: Meta[Slides] = urlMeta.timap(Slides.from(_).get)(_.url)
    implicit val videoMeta: Meta[Video] = urlMeta.timap(Video.from(_).get)(_.url)
    implicit val twitterHashtagMeta: Meta[TwitterHashtag] = Meta[String].timap(TwitterHashtag.from(_).get)(_.value)
    implicit val currencyMeta: Meta[Price.Currency] = Meta[String].timap(Price.Currency.from(_).get)(_.value)
    implicit val markdownMeta: Meta[Markdown] = Meta[String].timap(Markdown)(_.value)

    implicit def mustacheMarkdownTemplateMeta[A: TypeTag]: Meta[MustacheMarkdownTmpl[A]] = Meta[String].timap(MustacheMarkdownTmpl[A])(_.value)

    implicit def mustacheTextTemplateMeta[A: TypeTag]: Meta[MustacheTextTmpl[A]] = Meta[String].timap(MustacheTextTmpl[A])(_.value)

    implicit val avatarSourceMeta: Meta[Avatar.Source] = Meta[String].timap(Avatar.Source.from(_).get)(_.toString)
    implicit val tagsMeta: Meta[Seq[Tag]] = Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Tag(_)).toSeq)(_.map(_.value).mkString(","))
    implicit val gMapPlaceMeta: Meta[GMapPlace] = {
      import JsonEncoders.{gMapPlaceDecoder, gMapPlaceEncoder}
      Meta[String].timap(fromJson[GMapPlace](_).get)(toJson)
    }
    implicit val groupSettingsEventTemplatesMeta: Meta[Map[String, MustacheTextTmpl[TemplateData.EventInfo]]] = {
      import JsonEncoders.{textTemplateDecoder, textTemplateEncoder}
      Meta[String].timap(fromJson[Map[String, MustacheTextTmpl[TemplateData.EventInfo]]](_).get)(toJson)
    }
    implicit val groupSettingsActionsMeta: Meta[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]] = {
      import JsonEncoders.{groupSettingsActionDecoder, groupSettingsActionEncoder, groupSettingsActionTriggerKeyDecoder, groupSettingsActionTriggerKeyEncoder}
      Meta[String].timap(fromJson[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]](_).get)(toJson)
    }

    // TODO build Meta[Seq[A]] and Meta[NonEmptyList[A]]
    // implicit def seqMeta[A](implicit m: Meta[A]): Meta[Seq[A]] = ???
    // implicit def nelMeta[A](implicit m: Meta[A]): Meta[NonEmptyList[A]] = ???

    implicit val userIdMeta: Meta[User.Id] = Meta[String].timap(User.Id.from(_).get)(_.value)
    implicit val userSlugMeta: Meta[User.Slug] = Meta[String].timap(User.Slug.from(_).get)(_.value)
    implicit val userProfileStatusMeta: Meta[User.Profile.Status] = Meta[String].timap(User.Profile.Status.from(_).get)(_.value)
    implicit val userRequestIdMeta: Meta[UserRequest.Id] = Meta[String].timap(UserRequest.Id.from(_).get)(_.value)
    implicit val userRequestProposalCreationPayloadMeta: Meta[UserRequest.ProposalCreation.Payload] = Meta[String].timap(parser.parse(_).flatMap(JsonEncoders.payloadDecoder.decodeJson).get)(JsonEncoders.payloadEncoder(_).noSpaces)
    implicit val talkIdMeta: Meta[Talk.Id] = Meta[String].timap(Talk.Id.from(_).get)(_.value)
    implicit val talkSlugMeta: Meta[Talk.Slug] = Meta[String].timap(Talk.Slug.from(_).get)(_.value)
    implicit val talkTitleMeta: Meta[Talk.Title] = Meta[String].timap(Talk.Title)(_.value)
    implicit val talkStatusMeta: Meta[Talk.Status] = Meta[String].timap(Talk.Status.from(_).get)(_.value)
    implicit val groupIdMeta: Meta[Group.Id] = Meta[String].timap(Group.Id.from(_).get)(_.value)
    implicit val groupSlugMeta: Meta[Group.Slug] = Meta[String].timap(Group.Slug.from(_).get)(_.value)
    implicit val groupStatusMeta: Meta[Group.Status] = Meta[String].timap(Group.Status.from(_).get)(_.value)
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
    implicit val commentIdMeta: Meta[Comment.Id] = Meta[String].timap(Comment.Id.from(_).get)(_.value)
    implicit val commentKindMeta: Meta[Comment.Kind] = Meta[String].timap(Comment.Kind.from(_).get)(_.value)
    implicit val meetupGroupSlugMeta: Meta[MeetupGroup.Slug] = Meta[String].timap(MeetupGroup.Slug.from(_).get)(_.value)
    implicit val meetupEventIdMeta: Meta[MeetupEvent.Id] = Meta[Long].timap(MeetupEvent.Id(_))(_.value)
    implicit val memberRoleMeta: Meta[Group.Member.Role] = Meta[String].timap(Group.Member.Role.from(_).get)(_.toString)
    implicit val rsvpAnswerMeta: Meta[Event.Rsvp.Answer] = Meta[String].timap(Event.Rsvp.Answer.from(_).get)(_.toString)
    implicit val externalCfpIdMeta: Meta[ExternalCfp.Id] = Meta[String].timap(ExternalCfp.Id.from(_).get)(_.value)
    implicit val externalCfpNameMeta: Meta[ExternalCfp.Name] = Meta[String].timap(ExternalCfp.Name)(_.value)
    implicit val voteMeta: Meta[Proposal.Rating.Grade] = Meta[Int].timap(Proposal.Rating.Grade.from(_).get)(_.value)

    implicit val userIdNelMeta: Meta[NonEmptyList[User.Id]] = Meta[String].timap(
      s => NonEmptyList.fromListUnsafe(s.split(",").filter(_.nonEmpty).map(User.Id.from(_).get).toList))(
      _.map(_.value).toList.mkString(","))
    implicit val proposalIdSeqMeta: Meta[Seq[Proposal.Id]] = Meta[String].timap(
      _.split(",").filter(_.nonEmpty).map(Proposal.Id.from(_).get).toSeq)(
      _.map(_.value).mkString(","))

    implicit val userRequestRead: Read[UserRequest] =
      Read[(UserRequest.Id, String, Option[Group.Id], Option[Cfp.Id], Option[Event.Id], Option[Talk.Id], Option[Proposal.Id], Option[EmailAddress], Option[String], Option[Instant], Instant, Option[User.Id], Option[Instant], Option[User.Id], Option[Instant], Option[User.Id], Option[Instant], Option[User.Id])].map {
        case (id, "AccountValidation", _, _, _, _, _, Some(email), _, Some(deadline), created, Some(createdBy), accepted, _, _, _, _, _) =>
          UserRequest.AccountValidationRequest(id, email, deadline, created, createdBy, accepted)
        case (id, "PasswordReset", _, _, _, _, _, Some(email), _, Some(deadline), created, _, accepted, _, _, _, _, _) =>
          UserRequest.PasswordResetRequest(id, email, deadline, created, accepted)
        case (id, "UserAskToJoinAGroup", Some(groupId), _, _, _, _, _, _, _, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.UserAskToJoinAGroupRequest(id, groupId, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "GroupInvite", Some(groupId), _, _, _, _, Some(email), _, _, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.GroupInvite(id, groupId, email, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "TalkInvite", _, _, _, Some(talkId), _, Some(email), _, _, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.TalkInvite(id, talkId, email, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "ProposalInvite", _, _, _, _, Some(proposalId), Some(email), _, _, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          UserRequest.ProposalInvite(id, proposalId, email, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.flatMap(date => rejectedBy.map(UserRequest.Meta(date, _))),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, "ProposalCreation", _, Some(cfp), event, _, _, Some(email), Some(payload), _, created, Some(createdBy), accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          import JsonEncoders.payloadDecoder
          UserRequest.ProposalCreation(id, cfp, event, email, fromJson[UserRequest.ProposalCreation.Payload](payload).get, created, createdBy,
            accepted.flatMap(date => acceptedBy.map(UserRequest.Meta(date, _))),
            rejected.map(date => UserRequest.MetaAnon(date, rejectedBy)),
            canceled.flatMap(date => canceledBy.map(UserRequest.Meta(date, _))))
        case (id, kind, group, cfp, event, talk, proposal, email, payload, deadline, created, createdBy, accepted, acceptedBy, rejected, rejectedBy, canceled, canceledBy) =>
          throw new Exception(s"Unable to read UserRequest with ($id, $kind, group=$group, cfp=$cfp, event=$event, talk=$talk, proposal=$proposal, email=$email, payload=$payload, deadline=$deadline, created=($created, $createdBy), accepted=($accepted, $acceptedBy), rejected=($rejected, $rejectedBy), canceled=($canceled, $canceledBy))")
      }

    private def toJson[A](v: A)(implicit e: Encoder[A]): String = e.apply(v).noSpaces

    private def fromJson[A](s: String)(implicit d: Decoder[A]): util.Try[A] = parser.parse(s).flatMap(d.decodeJson).toTry
  }

  object JsonEncoders {
    implicit val geoDecoder: Decoder[Geo] = deriveDecoder[Geo]
    implicit val geoEncoder: Encoder[Geo] = deriveEncoder[Geo]
    implicit val gMapPlaceDecoder: Decoder[GMapPlace] = deriveDecoder[GMapPlace]
    implicit val gMapPlaceEncoder: Encoder[GMapPlace] = deriveEncoder[GMapPlace]

    implicit val textTemplateDecoder: Decoder[MustacheTextTmpl[TemplateData.EventInfo]] = deriveDecoder[MustacheTextTmpl[TemplateData.EventInfo]]
    implicit val textTemplateEncoder: Encoder[MustacheTextTmpl[TemplateData.EventInfo]] = deriveEncoder[MustacheTextTmpl[TemplateData.EventInfo]]

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
    implicit val groupSettingsActionTriggerKeyDecoder: KeyDecoder[Group.Settings.Action.Trigger] = (key: String) => Group.Settings.Action.Trigger.from(key).toOption
    implicit val groupSettingsActionTriggerKeyEncoder: KeyEncoder[Group.Settings.Action.Trigger] = (e: Group.Settings.Action.Trigger) => e.toString

    implicit val userSlugDecoder: Decoder[User.Slug] = (c: HCursor) => c.as[String].flatMap(User.Slug.from(_).leftMap(e => DecodingFailure(e.getMessage, List())))
    implicit val userSlugEncoder: Encoder[User.Slug] = (a: User.Slug) => Json.fromString(a.value)
    implicit val talkSlugDecoder: Decoder[Talk.Slug] = (c: HCursor) => c.as[String].flatMap(Talk.Slug.from(_).leftMap(e => DecodingFailure(e.getMessage, List())))
    implicit val talkSlugEncoder: Encoder[Talk.Slug] = (a: Talk.Slug) => Json.fromString(a.value)
    implicit val talkTitleDecoder: Decoder[Talk.Title] = (c: HCursor) => c.as[String].map(Talk.Title)
    implicit val talkTitleEncoder: Encoder[Talk.Title] = (a: Talk.Title) => Json.fromString(a.value)
    implicit val markdownDecoder: Decoder[Markdown] = (c: HCursor) => c.as[String].map(Markdown)
    implicit val markdownEncoder: Encoder[Markdown] = (a: Markdown) => Json.fromString(a.value)
    implicit val tagDecoder: Decoder[Tag] = (c: HCursor) => c.as[String].map(Tag(_))
    implicit val tagEncoder: Encoder[Tag] = (a: Tag) => Json.fromString(a.value)
    implicit val finiteDurationDecoder: Decoder[FiniteDuration] = (c: HCursor) => c.downField("length").as[Long].flatMap(length => c.downField("unit").as[String].map(unit => FiniteDuration(length, TimeUnit.valueOf(unit))))
    implicit val finiteDurationEncoder: Encoder[FiniteDuration] = (a: FiniteDuration) => Json.obj("length" -> Json.fromLong(a.length), "unit" -> Json.fromString(a.unit.name()))
    implicit val submissionDecoder: Decoder[UserRequest.ProposalCreation.Submission] = deriveDecoder[UserRequest.ProposalCreation.Submission]
    implicit val submissionEncoder: Encoder[UserRequest.ProposalCreation.Submission] = deriveEncoder[UserRequest.ProposalCreation.Submission]
    implicit val accountDecoder: Decoder[UserRequest.ProposalCreation.Account] = deriveDecoder[UserRequest.ProposalCreation.Account]
    implicit val accountEncoder: Encoder[UserRequest.ProposalCreation.Account] = deriveEncoder[UserRequest.ProposalCreation.Account]
    implicit val payloadDecoder: Decoder[UserRequest.ProposalCreation.Payload] = deriveDecoder[UserRequest.ProposalCreation.Payload]
    implicit val payloadEncoder: Encoder[UserRequest.ProposalCreation.Payload] = deriveEncoder[UserRequest.ProposalCreation.Payload]
  }

}
