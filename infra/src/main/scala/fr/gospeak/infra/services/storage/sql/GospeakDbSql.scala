package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{Done, Email, Info, Page}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.infra.services.storage.sql.tables._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.infra.utils.{DoobieUtils, FlywayUtils}

class GospeakDbSql(conf: DbSqlConf) extends GospeakDb {
  private val flyway = FlywayUtils.build(conf)
  private[sql] val xa = DoobieUtils.transactor(conf)

  private val user1 = User.Id.generate()
  private val user2 = User.Id.generate()
  private val user3 = User.Id.generate()

  private val users = NonEmptyList.of(
    User(user1, "Demo", "User", Email("demo@mail.com"), Instant.now(), Instant.now()),
    User(user2, "Speaker", "User", Email("speaker@mail.com"), Instant.now(), Instant.now()),
    User(user3, "Orga", "User", Email("orga@mail.com"), Instant.now(), Instant.now()),
    User(User.Id.generate(), "Empty", "User", Email("empty@mail.com"), Instant.now(), Instant.now()))

  def createTables(): IO[Int] = IO(flyway.migrate())

  def dropTables(): IO[Done] = IO(flyway.clean()).map(_ => Done)

  def insertMockData(): IO[Done] = {
    import fr.gospeak.infra.utils.DoobieUtils.Mappings._
    val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
    val group1 = Group.Id.generate()
    val group2 = Group.Id.generate()
    val group3 = Group.Id.generate()
    val event1 = Event.Id.generate()
    val event2 = Event.Id.generate()
    val event3 = Event.Id.generate()
    val event4 = Event.Id.generate()
    val talk1 = Talk.Id.generate()
    val talk2 = Talk.Id.generate()
    val talk3 = Talk.Id.generate()
    val talk4 = Talk.Id.generate()
    val talk5 = Talk.Id.generate()
    val talk6 = Talk.Id.generate()
    val proposal1 = Proposal.Id.generate()
    val proposal2 = Proposal.Id.generate()
    val proposal3 = Proposal.Id.generate()
    val proposal4 = Proposal.Id.generate()
    val proposal5 = Proposal.Id.generate()
    val groups = NonEmptyList.of(
      Group(group1, Group.Slug("ht-paris"), Group.Name("HumanTalks Paris"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", NonEmptyList.of(user1, user3), Info(user1)),
      Group(group2, Group.Slug("paris-js"), Group.Name("Paris.Js"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", NonEmptyList.of(user3), Info(user3)),
      Group(group3, Group.Slug("data-gov"), Group.Name("Data governance"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", NonEmptyList.of(user1), Info(user1)))
    val events = NonEmptyList.of(
      Event(group1, event1, Event.Slug("2019-03"), Event.Name("HumanTalks Paris Mars 2019"), Some("desc"), Some("Zeenea"), Seq(), Info(user1)),
      Event(group1, event2, Event.Slug("2019-04"), Event.Name("HumanTalks Paris Avril 2019"), None, None, Seq(), Info(user3)),
      Event(group2, event3, Event.Slug("2019-03"), Event.Name("Paris.Js Avril"), None, None, Seq(), Info(user3)),
      Event(group3, event4, Event.Slug("2019-03"), Event.Name("Nouveaux modèles de gouvenance"), None, None, Seq(), Info(user1)))
    val talks = NonEmptyList.of(
      Talk(talk1, Talk.Slug("why-fp"), Talk.Title("Why FP"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", NonEmptyList.of(user1), Info(user1)),
      Talk(talk2, Talk.Slug("scala-best-practices"), Talk.Title("Scala Best Practices"), "Cras sit amet nibh libero, in gravida nulla..", NonEmptyList.of(user1, user2), Info(user1)),
      Talk(talk3, Talk.Slug("nodejs-news"), Talk.Title("NodeJs news"), "Cras sit amet nibh libero, in gravida nulla..", NonEmptyList.of(user1), Info(user1)),
      Talk(talk4, Talk.Slug("scalajs-react"), Talk.Title("ScalaJS + React = ♥"), "Cras sit amet nibh libero, in gravida nulla..", NonEmptyList.of(user2, user1), Info(user2)),
      Talk(talk5, Talk.Slug("gagner-1-million"), Talk.Title("Gagner 1 Million au BlackJack avec Akka"), "Cras sit amet nibh libero, in gravida nulla..", NonEmptyList.of(user2), Info(user2)),
      Talk(talk6, Talk.Slug("demarrer-avec-spark"), Talk.Title("7 conseils pour démarrer avec Spark"), "Cras sit amet nibh libero, in gravida nulla..", NonEmptyList.of(user2), Info(user2)))
    val proposals = NonEmptyList.of(
      Proposal(proposal1, talk1, group1, Proposal.Title("Why FP"), "temporary description", Info(user1)),
      Proposal(proposal2, talk2, group1, Proposal.Title("Scala Best Practices"), "temporary description", Info(user1)),
      Proposal(proposal3, talk2, group2, Proposal.Title("Scala Best Practices"), "temporary description", Info(user1)),
      Proposal(proposal4, talk3, group1, Proposal.Title("NodeJs news"), "temporary description", Info(user1)),
      Proposal(proposal5, talk4, group2, Proposal.Title("ScalaJS + React = ♥"), "temporary description", Info(user2)))
    for {
      _ <- run(Queries.insertMany(UserTable.insert)(users))
      _ <- run(Queries.insertMany(TalkTable.insert)(talks))
      _ <- run(Queries.insertMany(GroupTable.insert)(groups))
      _ <- run(Queries.insertMany(EventTable.insert)(events))
      _ <- run(Queries.insertMany(ProposalTable.insert)(proposals))
    } yield Done
  }

  private var logged: Option[User] = Some(users.head)

  override def login(user: User): IO[Done] = { // TODO mock auth, to remove
    logged = Some(user)
    IO.pure(Done)
  }

  override def logout(): IO[Done] = { // TODO mock auth, to remove
    logged = None
    IO.pure(Done)
  }

  override def userAware(): Option[User] = logged // TODO mock auth, to remove

  override def authed(): User = logged.get // TODO mock auth, to remove

  override def getUser(email: Email): IO[Option[User]] = run(UserTable.selectOne(email).option)

  override def createUser(firstName: String, lastName: String, email: Email): IO[User] =
    run(UserTable.insert, User(User.Id.generate(), firstName, lastName, email, Instant.now(), Instant.now()))

  override def getGroups(user: User.Id, params: Page.Params): IO[Page[Group]] = run(Queries.selectPage(GroupTable.selectPage(user, _), params))

  override def getGroup(id: Group.Id, user: User.Id): IO[Option[Group]] = run(GroupTable.selectOne(id, user).option)

  override def getGroupId(slug: Group.Slug): IO[Option[Group.Id]] = run(GroupTable.slugToId(slug).option)

  override def createGroup(slug: Group.Slug, name: Group.Name, description: String, by: User.Id): IO[Group] =
    run(GroupTable.insert, Group(Group.Id.generate(), slug, name, description, NonEmptyList.of(by), Info(by)))

  override def getEvents(group: Group.Id, params: Page.Params): IO[Page[Event]] = run(Queries.selectPage(EventTable.selectPage(group, _), params))

  override def getEvent(id: Event.Id): IO[Option[Event]] = run(EventTable.selectOne(id).option)

  override def getEventId(group: Group.Id, slug: Event.Slug): IO[Option[Event.Id]] = run(EventTable.slugToId(group, slug).option)

  override def createEvent(group: Group.Id, slug: Event.Slug, name: Event.Name, by: User.Id): IO[Event] =
    run(EventTable.insert, Event(group, Event.Id.generate(), slug, name, None, None, Seq(), Info(by)))

  override def getTalks(user: User.Id, params: Page.Params): IO[Page[Talk]] = run(Queries.selectPage(TalkTable.selectPage(user, _), params))

  override def getTalk(id: Talk.Id, user: User.Id): IO[Option[Talk]] = run(TalkTable.selectOne(id, user).option)

  override def getTalkId(user: User.Id, slug: Talk.Slug): IO[Option[Talk.Id]] = run(TalkTable.slugToId(user, slug).option)

  override def createTalk(slug: Talk.Slug, title: Talk.Title, description: String, by: User.Id): IO[Talk] =
    getTalkId(by, slug).flatMap {
      case None => run(TalkTable.insert, Talk(Talk.Id.generate(), slug, title, description, NonEmptyList.one(by), Info(by)))
      case _ => IO.raiseError(new Exception(s"You already have a talk with slug $slug"))
    }

  override def getProposals(group: Group.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(ProposalTable.selectPage(group, _), params))

  override def getProposals(talk: Talk.Id, params: Page.Params): IO[Page[(Group, Proposal)]] = run(Queries.selectPage(ProposalTable.selectPage(talk, _), params))

  override def getProposal(id: Proposal.Id): IO[Option[Proposal]] = run(ProposalTable.selectOne(id).option)

  override def createProposal(talk: Talk.Id, group: Group.Id, title: Proposal.Title, description: String, by: User.Id): IO[Proposal] =
    run(ProposalTable.insert, Proposal(Proposal.Id.generate(), talk, group, title, description, Info(by)))

  private def run[A](i: A => doobie.Update0, v: A): IO[A] =
    i(v).run.transact(xa).flatMap {
      case 1 => IO.pure(v)
      case code => IO.raiseError(new Exception(s"Failed to insert $v (code: $code)"))
    }

  private def run[A](v: doobie.ConnectionIO[A]): IO[A] =
    v.transact(xa)
}
