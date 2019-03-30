package fr.gospeak.infra.services.storage.sql

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.infra.services.storage.sql.tables._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.infra.utils.{DoobieUtils, FlywayUtils}
import fr.gospeak.libs.scalautils.domain._

import scala.concurrent.duration._

class GospeakDbSql(conf: DbSqlConf) extends GospeakDb {
  private val flyway = FlywayUtils.build(conf)
  private[sql] val xa = DoobieUtils.transactor(conf)

  def createTables(): IO[Int] = IO(flyway.migrate())

  def dropTables(): IO[Done] = IO(flyway.clean()).map(_ => Done)

  def insertMockData(): IO[Done] = {
    val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
    val now = Instant.now()

    def user(slug: String, email: String, firstName: String, lastName: String): User =
      User(User.Id.generate(), User.Slug.from(slug).right.get, firstName, lastName, Email.from(email).right.get, None, now, now)

    def group(slug: String, name: String, by: User, owners: Seq[User] = Seq()): Group =
      Group(Group.Id.generate(), Group.Slug.from(slug).right.get, Group.Name(name), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(by.id) ++ owners.map(_.id).toList, Info(by.id, now))

    def cfp(group: Group, slug: String, name: String, description: String, by: User): Cfp =
      Cfp(Cfp.Id.generate(), group.id, Cfp.Slug.from(slug).right.get, Cfp.Name(name), Markdown(description), Info(by.id, now))

    def talk(by: User, slug: String, title: String, status: Talk.Status = Talk.Status.Public, speakers: Seq[User] = Seq(), duration: Int = 10, slides: Option[Slides] = None, video: Option[Video] = None, description: String = "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."): Talk =
      Talk(Talk.Id.generate(), Talk.Slug.from(slug).right.get, Talk.Title(title), Duration(duration, MINUTES), status, Markdown(description), NonEmptyList.of(by.id) ++ speakers.map(_.id).toList, slides, video, Info(by.id, now))

    def proposal(talk: Talk, cfp: Cfp, status: Proposal.Status = Proposal.Status.Pending): Proposal =
      Proposal(Proposal.Id.generate(), talk.id, cfp.id, None, talk.title, talk.duration, status, talk.description, talk.speakers, talk.slides, talk.video, talk.info)

    def event(group: Group, slug: String, name: String, date: String, by: User, talks: Seq[Proposal] = Seq(), venue: Option[GMapPlace] = None, description: Option[String] = None): Event =
      Event(Event.Id.generate(), group.id, Event.Slug.from(slug).right.get, Event.Name(name), LocalDateTime.parse(s"${date}T19:00:00"), description.map(Markdown), venue, talks.map(_.id), Info(by.id, now))

    val userDemo = user("demo", "demo@mail.com", "Demo", "User")
    val userSpeaker = user("speaker", "speaker@mail.com", "Speaker", "User")
    val userOrga = user("orga", "orga@mail.com", "Orga", "User")
    val userEmpty = user("empty", "empty@mail.com", "Empty", "User")

    val group1 = group("ht-paris", "HumanTalks Paris", userDemo, Seq(userOrga))
    val group2 = group("paris-js", "Paris.Js", userOrga)
    val group3 = group("data-gov", "Data governance", userDemo)
    val group4 = group("big-group", "Big Group", userOrga)

    val cfp1 = cfp(group1, "ht-paris", "HumanTalks Paris", "Les HumanTalks Paris c'est 4 talks de 10 min...", userDemo)
    val cfp2 = cfp(group2, "paris-js", "Paris.Js", "Submit your talk to exchange with the Paris JS community", userOrga)

    val talk1 = talk(userDemo, "why-fp", "Why FP", status = Talk.Status.Private)
    val talk2 = talk(userDemo, "scala-best-practices", "Scala Best Practices", speakers = Seq(userSpeaker),
      slides = Some(Slides.from("https://docs.google.com/presentation/d/1wWRKbxz81AzhBJJqc505yUkileRPn5b-bNH1Th852f4").right.get),
      video = Some(Video.from("https://www.youtube.com/watch?v=Tm-qyMukBq4").right.get),
      description =
        """I have seen a lot of people struggleing with Scala because they were lost in all the feature and did not know which one to use and *not to use*.
          |This talk is for everyone to discuss about **best practices**:
          |- do not throw
          |- never use null
          |- go functional
        """.stripMargin.trim)
    val talk3 = talk(userDemo, "nodejs-news", "NodeJs news", status = Talk.Status.Draft)
    val talk4 = talk(userSpeaker, "scalajs-react", "ScalaJS + React = <3", status = Talk.Status.Draft, speakers = Seq(userDemo), duration = 50)
    val talk5 = talk(userSpeaker, "gagner-1-million", "Gagner 1 Million au BlackJack avec Akka", status = Talk.Status.Private, duration = 15)
    val talk6 = talk(userSpeaker, "demarrer-avec-spark", "7 conseils pour demarrer avec Spark", duration = 45)
    val talk7 = talk(userSpeaker, "big-talk", "Big Talk")

    val proposal1 = proposal(talk1, cfp1, status = Proposal.Status.Accepted)
    val proposal2 = proposal(talk2, cfp1)
    val proposal3 = proposal(talk2, cfp2, status = Proposal.Status.Accepted)
    val proposal4 = proposal(talk3, cfp1, status = Proposal.Status.Rejected)

    val event1 = event(group1, "2019-01", "HumanTalks Paris Janvier 2019", "2019-01-08", userDemo, venue = None, description = Some("desc"))
    val event2 = event(group1, "2019-02", "HumanTalks Paris Fevrier 2019", "2019-02-12", userOrga, talks = Seq(proposal1))
    val event3 = event(group1, "2019-03", "HumanTalks Paris Mars 2019", "2019-03-12", userDemo, venue = None, description = Some("desc"))
    val event4 = event(group2, "2019-04", "Paris.Js Avril", "2019-04-01", userOrga, talks = Seq(proposal3))
    val event5 = event(group3, "2019-03", "Nouveaux modeles de gouvenance", "2019-03-15", userDemo)

    val generated = (1 to 25).toList.map { i =>
      val groupId = Group.Id.generate()
      val cfpId = Cfp.Id.generate()
      val g = Group(groupId, Group.Slug.from(s"z-group-$i").right.get, Group.Name(s"Z Group $i"), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userOrga.id), Info(userOrga.id, now))
      val c = Cfp(cfpId, groupId, Cfp.Slug.from(s"z-cfp-$i").right.get, Cfp.Name(s"Z CFP $i"), Markdown("Only your best talks !"), Info(userOrga.id, now))
      val e = Event(Event.Id.generate(), group4.id, Event.Slug.from(s"z-event-$i").right.get, Event.Name(s"Z Event $i"), LocalDateTime.parse("2019-03-12T19:00:00"), None, None, Seq(), Info(userOrga.id, now))
      val t = Talk(Talk.Id.generate(), Talk.Slug.from(s"z-talk-$i").right.get, Talk.Title(s"Z Talk $i"), Duration(10, MINUTES), Talk.Status.Draft, Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userSpeaker.id), None, None, Info(userSpeaker.id, now))
      val p = Proposal(Proposal.Id.generate(), talk7.id, cfpId, None, Talk.Title(s"Z Proposal $i"), Duration(10, MINUTES), Proposal.Status.Pending, Markdown("temporary description"), NonEmptyList.of(userSpeaker.id), None, None, Info(userSpeaker.id, now))
      (g, c, e, t, p)
    }
    for {
      _ <- run(Queries.insertMany(UserTable.insert)(NonEmptyList.of(userDemo, userSpeaker, userOrga, userEmpty)))
      _ <- run(UserTable.insertCredentials(User.Credentials("credentials", "demo@mail.com", "bcrypt", "$2a$10$5r9NrHNAtujdA.qPcQHDm.xPxxTL/TAXU85RnP.7rDd3DTVPLCCjC", None))) // pwd: demo
      _ <- run(UserTable.insertLoginRef(User.LoginRef("credentials", "demo@mail.com", userDemo.id)))
      _ <- run(Queries.insertMany(GroupTable.insert)(NonEmptyList.of(group1, group2, group3, group4) ++ generated.map(_._1)))
      _ <- run(Queries.insertMany(CfpTable.insert)(NonEmptyList.of(cfp1, cfp2) ++ generated.map(_._2)))
      _ <- run(Queries.insertMany(TalkTable.insert)(NonEmptyList.of(talk1, talk2, talk3, talk4, talk5, talk6, talk7) ++ generated.map(_._4)))
      _ <- run(Queries.insertMany(ProposalTable.insert)(NonEmptyList.of(proposal1, proposal2, proposal3, proposal4) ++ generated.map(_._5)))
      _ <- run(Queries.insertMany(EventTable.insert)(NonEmptyList.of(event1, event2, event3, event4, event5) ++ generated.map(_._3)))
    } yield Done
  }


  override def createUser(slug: User.Slug, firstName: String, lastName: String, email: Email, now: Instant): IO[User] =
    run(UserTable.insert, User(User.Id.generate(), slug, firstName, lastName, email, None, now, now))

  override def updateUser(user: User, now: Instant): IO[User] =
    run(UserTable.update(user.copy(updated = now))).map(_ => user)

  override def createLoginRef(login: User.Login, user: User.Id): IO[Done] =
    run(UserTable.insertLoginRef, User.LoginRef(login, user)).map(_ => Done)

  override def createCredentials(credentials: User.Credentials): IO[User.Credentials] =
    run(UserTable.insertCredentials, credentials)

  override def updateCredentials(login: User.Login)(pass: User.Password): IO[Done] =
    run(UserTable.updateCredentials(login)(pass))

  override def deleteCredentials(login: User.Login): IO[Done] =
    run(UserTable.deleteCredentials(login))

  override def getCredentials(login: User.Login): IO[Option[User.Credentials]] =
    run(UserTable.selectCredentials(login).option)

  override def getUser(login: User.Login): IO[Option[User]] = run(UserTable.selectOne(login).option)

  override def getUser(credentials: User.Credentials): IO[Option[User]] = run(UserTable.selectOne(credentials.login).option)

  override def getUser(email: Email): IO[Option[User]] = run(UserTable.selectOne(email).option)

  override def getUser(slug: User.Slug): IO[Option[User]] = run(UserTable.selectOne(slug).option)

  override def getUsers(ids: Seq[User.Id]): IO[Seq[User]] = runIn(UserTable.selectAll)(ids)


  override def createAccountValidationRequest(email: Email, user: User.Id, now: Instant): IO[AccountValidationRequest] =
    run(UserRequestTable.AccountValidation.insert, AccountValidationRequest(email, user, now))

  override def getPendingAccountValidationRequest(id: UserRequest.Id, now: Instant): IO[Option[AccountValidationRequest]] =
    run(UserRequestTable.AccountValidation.selectPending(id, now).option)

  override def getPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]] =
    run(UserRequestTable.AccountValidation.selectPending(id, now).option)

  override def validateAccount(id: UserRequest.Id, user: User.Id, now: Instant): IO[Done] = for {
    _ <- run(UserTable.validateEmail(user, now))
    _ <- run(UserRequestTable.AccountValidation.validate(id, now))
  } yield Done

  override def createPasswordResetRequest(email: Email, now: Instant): IO[PasswordResetRequest] =
    run(UserRequestTable.ResetPassword.insert, PasswordResetRequest(email, now))

  override def getPendingPasswordResetRequest(id: UserRequest.Id, now: Instant): IO[Option[PasswordResetRequest]] =
    run(UserRequestTable.ResetPassword.selectPending(id, now).option)

  override def getPendingPasswordResetRequest(email: Email, now: Instant): IO[Option[PasswordResetRequest]] =
    run(UserRequestTable.ResetPassword.selectPending(email, now).option)

  override def resetPassword(passwordReset: PasswordResetRequest, credentials: User.Credentials, now: Instant): IO[Done] = for {
    _ <- run(UserRequestTable.ResetPassword.accept(passwordReset.id, now))
    _ <- run(UserTable.updateCredentials(credentials.login)(credentials.pass))
  } yield Done


  override def createGroup(data: Group.Data, by: User.Id, now: Instant): IO[Group] =
    run(GroupTable.insert, Group(Group.Id.generate(), data.slug, data.name, data.description, NonEmptyList.of(by), Info(by, now)))

  override def getGroup(user: User.Id, slug: Group.Slug): IO[Option[Group]] = run(GroupTable.selectOne(user, slug).option)

  override def getGroups(user: User.Id, params: Page.Params): IO[Page[Group]] = run(Queries.selectPage(GroupTable.selectPage(user, _), params))


  override def createEvent(group: Group.Id, data: Event.Data, by: User.Id, now: Instant): IO[Event] =
    run(EventTable.insert, Event(Event.Id.generate(), group, data.slug, data.name, data.start, None, data.venue, Seq(), Info(by, now)))

  override def updateEvent(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != event) {
      getEvent(group, data.slug).flatMap {
        case None => run(EventTable.update(group, event)(data, by, now))
        case _ => IO.raiseError(CustomException(s"You already have an event with slug ${data.slug}"))
      }
    } else {
      run(EventTable.update(group, event)(data, by, now))
    }
  }

  override def updateEventTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): IO[Done] =
    run(EventTable.updateTalks(group, event)(talks, by, now))

  override def getEvent(group: Group.Id, event: Event.Slug): IO[Option[Event]] = run(EventTable.selectOne(group, event).option)

  override def getEvents(group: Group.Id, params: Page.Params): IO[Page[Event]] = run(Queries.selectPage(EventTable.selectPage(group, _), params))

  override def getEvents(ids: Seq[Event.Id]): IO[Seq[Event]] = runIn[Event.Id, Event](EventTable.selectAll)(ids)

  override def getEventsAfter(group: Group.Id, now: Instant, params: Page.Params): IO[Page[Event]] =
    run(Queries.selectPage(EventTable.selectAllAfter(group, now.truncatedTo(ChronoUnit.DAYS), _), params))


  override def createCfp(group: Group.Id, data: Cfp.Data, by: User.Id, now: Instant): IO[Cfp] =
    run(CfpTable.insert, Cfp(Cfp.Id.generate(), group, data.slug, data.name, data.description, Info(by, now)))

  override def getCfp(slug: Cfp.Slug): IO[Option[Cfp]] = run(CfpTable.selectOne(slug).option)

  override def getCfp(id: Cfp.Id): IO[Option[Cfp]] = run(CfpTable.selectOne(id).option)

  override def getCfp(id: Group.Id): IO[Option[Cfp]] = run(CfpTable.selectOne(id).option)

  override def getCfpAvailables(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]] = run(Queries.selectPage(CfpTable.selectPage(talk, _), params))


  override def createTalk(user: User.Id, data: Talk.Data, now: Instant): IO[Talk] =
    getTalk(user, data.slug).flatMap {
      case None => run(TalkTable.insert, Talk(Talk.Id.generate(), data.slug, data.title, data.duration, Talk.Status.Draft, data.description, NonEmptyList.one(user), data.slides, data.video, Info(user, now)))
      case _ => IO.raiseError(CustomException(s"You already have a talk with slug ${data.slug}"))
    }

  override def updateTalk(user: User.Id, slug: Talk.Slug)(data: Talk.Data, now: Instant): IO[Done] = {
    if (data.slug != slug) {
      // FIXME: should also check for other speakers !!!
      getTalk(user, data.slug).flatMap {
        case None => run(TalkTable.update(user, slug)(data, now))
        case _ => IO.raiseError(CustomException(s"You already have a talk with slug ${data.slug}"))
      }
    } else {
      run(TalkTable.update(user, slug)(data, now))
    }
  }

  override def updateTalkStatus(user: User.Id, slug: Talk.Slug)(status: Talk.Status): IO[Done] = run(TalkTable.updateStatus(user, slug)(status))

  override def updateTalkSlides(user: User.Id, slug: Talk.Slug)(slides: Slides, now: Instant): IO[Done] = run(TalkTable.updateSlides(user, slug)(slides, now))

  override def updateTalkVideo(user: User.Id, slug: Talk.Slug)(video: Video, now: Instant): IO[Done] = run(TalkTable.updateVideo(user, slug)(video, now))

  override def getTalk(user: User.Id, slug: Talk.Slug): IO[Option[Talk]] = run(TalkTable.selectOne(user, slug).option)

  override def getTalks(user: User.Id, params: Page.Params): IO[Page[Talk]] = run(Queries.selectPage(TalkTable.selectPage(user, _), params))

  override def getTalks(ids: Seq[Talk.Id]): IO[Seq[Talk]] = runIn(TalkTable.selectAll)(ids)


  override def createProposal(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): IO[Proposal] =
    run(ProposalTable.insert, Proposal(Proposal.Id.generate(), talk, cfp, None, data.title, data.duration, Proposal.Status.Pending, data.description, speakers, data.slides, data.video, Info(by, now)))

  override def updateProposalStatus(id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): IO[Done] =
    run(ProposalTable.updateStatus(id)(status, event))

  override def updateProposalSlides(id: Proposal.Id)(slides: Slides, now: Instant, user: User.Id): IO[Done] = run(ProposalTable.updateSlides(id)(slides, now, user))

  override def updateProposalVideo(id: Proposal.Id)(video: Video, now: Instant, user: User.Id): IO[Done] = run(ProposalTable.updateVideo(id)(video, now, user))

  override def getProposal(id: Proposal.Id): IO[Option[Proposal]] = run(ProposalTable.selectOne(id).option)

  override def getProposal(talk: Talk.Id, cfp: Cfp.Id): IO[Option[Proposal]] = run(ProposalTable.selectOne(talk, cfp).option)

  override def getProposals(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]] = run(Queries.selectPage(ProposalTable.selectPage(talk, _), params))

  override def getProposals(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(ProposalTable.selectPage(cfp, _), params))

  override def getProposals(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(ProposalTable.selectPage(cfp, status, _), params))

  override def getProposals(ids: Seq[Proposal.Id]): IO[Seq[Proposal]] = runIn(ProposalTable.selectAll)(ids)


  private def run[A](i: A => doobie.Update0, v: A): IO[A] =
    i(v).run.transact(xa).flatMap {
      case 1 => IO.pure(v)
      case code => IO.raiseError(CustomException(s"Failed to insert $v (code: $code)"))
    }

  private def run(i: => doobie.Update0): IO[Done] =
    i.run.transact(xa).flatMap {
      case 1 => IO.pure(Done)
      case code => IO.raiseError(CustomException(s"Failed to update $i (code: $code)"))
    }

  private def run[A](v: doobie.ConnectionIO[A]): IO[A] =
    v.transact(xa)

  private def runIn[Id, A](selectAll: NonEmptyList[Id] => doobie.Query0[A])(ids: Seq[Id]): IO[Seq[A]] =
    NonEmptyList.fromList(ids.toList)
      .map(nel => run(selectAll(nel).to[List]))
      .getOrElse(IO.pure(Seq()))
}
