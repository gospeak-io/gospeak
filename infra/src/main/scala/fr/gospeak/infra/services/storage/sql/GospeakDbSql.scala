package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.infra.services.storage.sql.tables._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.infra.utils.{DoobieUtils, FlywayUtils}
import fr.gospeak.libs.scalautils.CustomException
import fr.gospeak.libs.scalautils.domain.{Done, Email, Markdown, Page}

import scala.concurrent.duration._

class GospeakDbSql(conf: DbSqlConf) extends GospeakDb {
  private val flyway = FlywayUtils.build(conf)
  private[sql] val xa = DoobieUtils.transactor(conf)

  private val userDemo = User.Id.generate()
  private val userSpeaker = User.Id.generate()
  private val userOrga = User.Id.generate()

  private val users = NonEmptyList.of(
    User(userDemo, "Demo", "User", Email.from("demo@mail.com").get, Instant.now(), Instant.now()),
    User(userSpeaker, "Speaker", "User", Email.from("speaker@mail.com").get, Instant.now(), Instant.now()),
    User(userOrga, "Orga", "User", Email.from("orga@mail.com").get, Instant.now(), Instant.now()),
    User(User.Id.generate(), "Empty", "User", Email.from("empty@mail.com").get, Instant.now(), Instant.now()))

  def createTables(): IO[Int] = IO(flyway.migrate())

  def dropTables(): IO[Done] = IO(flyway.clean()).map(_ => Done)

  def insertMockData(): IO[Done] = {
    val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
    val group1 = Group.Id.generate()
    val group2 = Group.Id.generate()
    val group3 = Group.Id.generate()
    val group4 = Group.Id.generate()
    val cfp1 = Cfp.Id.generate()
    val cfp2 = Cfp.Id.generate()
    val cfp3 = Cfp.Id.generate()
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
    val talk7 = Talk.Id.generate()
    val proposal1 = Proposal.Id.generate()
    val proposal2 = Proposal.Id.generate()
    val proposal3 = Proposal.Id.generate()
    val proposal4 = Proposal.Id.generate()
    val proposal5 = Proposal.Id.generate()
    val groups = NonEmptyList.of(
      Group(group1, Group.Slug.from("ht-paris").get, Group.Name("HumanTalks Paris"), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userDemo, userOrga), Info(userDemo)),
      Group(group2, Group.Slug.from("paris-js").get, Group.Name("Paris.Js"), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userOrga), Info(userOrga)),
      Group(group3, Group.Slug.from("data-gov").get, Group.Name("Data governance"), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userDemo), Info(userDemo)),
      Group(group4, Group.Slug.from("big-group").get, Group.Name("Big Group"), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userOrga), Info(userOrga)))
    val cfps = NonEmptyList.of(
      Cfp(cfp1, Cfp.Slug.from("ht-paris").get, Cfp.Name("HumanTalks Paris"), Markdown("Les HumanTalks Paris c'est 4 talks de 10 min..."), group1, Info(userDemo)),
      Cfp(cfp2, Cfp.Slug.from("paris-js").get, Cfp.Name("Paris.Js"), Markdown("Submit your talk to exchange with the Paris JS community"), group2, Info(userOrga)),
      Cfp(cfp3, Cfp.Slug.from("data-gov").get, Cfp.Name("Data governance"), Markdown("Everything about Data governance"), group3, Info(userDemo)))
    val events = NonEmptyList.of(
      Event(group1, event1, Event.Slug.from("2019-03").get, Event.Name("HumanTalks Paris Mars 2019"), Some("desc"), Some("Zeenea"), Seq(), Info(userDemo)),
      Event(group1, event2, Event.Slug.from("2019-04").get, Event.Name("HumanTalks Paris Avril 2019"), None, None, Seq(), Info(userOrga)),
      Event(group2, event3, Event.Slug.from("2019-03").get, Event.Name("Paris.Js Avril"), None, None, Seq(), Info(userOrga)),
      Event(group3, event4, Event.Slug.from("2019-03").get, Event.Name("Nouveaux modeles de gouvenance"), None, None, Seq(), Info(userDemo)))
    val talks = NonEmptyList.of(
      Talk(talk1, Talk.Slug.from("why-fp").get, Talk.Title("Why FP"), Duration.apply(10, MINUTES), Talk.Status.Private, Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userDemo), Info(userDemo)),
      Talk(talk2, Talk.Slug.from("scala-best-practices").get, Talk.Title("Scala Best Practices"), Duration.apply(10, MINUTES), Talk.Status.Public, Markdown("Cras sit amet nibh libero, in gravida nulla.."), NonEmptyList.of(userDemo, userSpeaker), Info(userDemo)),
      Talk(talk3, Talk.Slug.from("nodejs-news").get, Talk.Title("NodeJs news"), Duration.apply(10, MINUTES), Talk.Status.Draft, Markdown("Cras sit amet nibh libero, in gravida nulla.."), NonEmptyList.of(userDemo), Info(userDemo)),
      Talk(talk4, Talk.Slug.from("scalajs-react").get, Talk.Title("ScalaJS + React = <3"), Duration.apply(50, MINUTES), Talk.Status.Archived, Markdown("Cras sit amet nibh libero, in gravida nulla.."), NonEmptyList.of(userSpeaker, userDemo), Info(userSpeaker)),
      Talk(talk5, Talk.Slug.from("gagner-1-million").get, Talk.Title("Gagner 1 Million au BlackJack avec Akka"), Duration.apply(15, MINUTES), Talk.Status.Private, Markdown("Cras sit amet nibh libero, in gravida nulla.."), NonEmptyList.of(userSpeaker), Info(userSpeaker)),
      Talk(talk6, Talk.Slug.from("demarrer-avec-spark").get, Talk.Title("7 conseils pour demarrer avec Spark"), Duration.apply(45, MINUTES), Talk.Status.Public, Markdown("Cras sit amet nibh libero, in gravida nulla.."), NonEmptyList.of(userSpeaker), Info(userSpeaker)),
      Talk(talk7, Talk.Slug.from("big-talk").get, Talk.Title("Big Talk"), Duration.apply(10, MINUTES), Talk.Status.Public, Markdown("Cras sit amet nibh libero, in gravida nulla.."), NonEmptyList.of(userSpeaker), Info(userSpeaker)))
    val proposals = NonEmptyList.of(
      Proposal(proposal1, talk1, cfp1, None, Talk.Title("Why FP"), Proposal.Status.Pending, Markdown("temporary description"), Info(userDemo)),
      Proposal(proposal2, talk2, cfp1, None, Talk.Title("Scala Best Practices"), Proposal.Status.Pending, Markdown("temporary description"), Info(userDemo)),
      Proposal(proposal3, talk2, cfp2, None, Talk.Title("Scala Best Practices"), Proposal.Status.Accepted, Markdown("temporary description"), Info(userDemo)),
      Proposal(proposal4, talk2, cfp3, None, Talk.Title("Scala Best Practices"), Proposal.Status.Rejected, Markdown("temporary description"), Info(userDemo)),
      Proposal(proposal5, talk3, cfp1, None, Talk.Title("NodeJs news"), Proposal.Status.Pending, Markdown("temporary description"), Info(userDemo)))
    val generated = (1 to 25).toList.map { i =>
      val groupId = Group.Id.generate()
      val cfpId = Cfp.Id.generate()
      val g = Group(groupId, Group.Slug.from(s"z-group-$i").get, Group.Name(s"Z Group $i"), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userOrga), Info(userOrga))
      val c = Cfp(cfpId, Cfp.Slug.from(s"z-cfp-$i").get, Cfp.Name(s"Z CFP $i"), Markdown("Only your best talks !"), groupId, Info(userOrga))
      val e = Event(group4, Event.Id.generate(), Event.Slug.from(s"z-event-$i").get, Event.Name(s"Z Event $i"), None, None, Seq(), Info(userOrga))
      val t = Talk(Talk.Id.generate(), Talk.Slug.from(s"z-talk-$i").get, Talk.Title(s"Z Talk $i"), Duration.apply(10, MINUTES), Talk.Status.Draft, Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userSpeaker), Info(userSpeaker))
      val p = Proposal(Proposal.Id.generate(), talk7, cfpId, None, Talk.Title(s"Z Proposal $i"), Proposal.Status.Pending, Markdown("temporary description"), Info(userSpeaker))
      (g, c, e, t, p)
    }
    for {
      _ <- run(Queries.insertMany(UserTable.insert)(users))
      _ <- run(Queries.insertMany(GroupTable.insert)(groups ++ generated.map(_._1)))
      _ <- run(Queries.insertMany(CfpTable.insert)(cfps ++ generated.map(_._2)))
      _ <- run(Queries.insertMany(EventTable.insert)(events ++ generated.map(_._3)))
      _ <- run(Queries.insertMany(TalkTable.insert)(talks ++ generated.map(_._4)))
      _ <- run(Queries.insertMany(ProposalTable.insert)(proposals ++ generated.map(_._5)))
    } yield Done
  }

  override def createUser(firstName: String, lastName: String, email: Email): IO[User] =
    run(UserTable.insert, User(User.Id.generate(), firstName, lastName, email, Instant.now(), Instant.now()))

  override def getUser(email: Email): IO[Option[User]] = run(UserTable.selectOne(email).option)

  override def getUsers(ids: NonEmptyList[User.Id]): IO[Seq[User]] = run(UserTable.selectAll(ids).to[List])

  override def createGroup(slug: Group.Slug, name: Group.Name, description: Markdown, by: User.Id): IO[Group] =
    run(GroupTable.insert, Group(Group.Id.generate(), slug, name, description, NonEmptyList.of(by), Info(by)))

  override def getGroup(user: User.Id, slug: Group.Slug): IO[Option[Group]] = run(GroupTable.selectOne(user, slug).option)

  override def getGroups(user: User.Id, params: Page.Params): IO[Page[Group]] = run(Queries.selectPage(GroupTable.selectPage(user, _), params))

  override def createEvent(group: Group.Id, slug: Event.Slug, name: Event.Name, by: User.Id): IO[Event] =
    run(EventTable.insert, Event(group, Event.Id.generate(), slug, name, None, None, Seq(), Info(by)))

  override def getEvent(group: Group.Id, event: Event.Slug): IO[Option[Event]] = run(EventTable.selectOne(group, event).option)

  override def getEvents(group: Group.Id, params: Page.Params): IO[Page[Event]] = run(Queries.selectPage(EventTable.selectPage(group, _), params))

  override def createCfp(slug: Cfp.Slug, name: Cfp.Name, description: Markdown, group: Group.Id, by: User.Id): IO[Cfp] =
    run(CfpTable.insert, Cfp(Cfp.Id.generate(), slug, name, description, group, Info(by)))

  override def getCfp(slug: Cfp.Slug): IO[Option[Cfp]] = run(CfpTable.selectOne(slug).option)

  override def getCfp(id: Cfp.Id): IO[Option[Cfp]] = run(CfpTable.selectOne(id).option)

  override def getCfp(id: Group.Id): IO[Option[Cfp]] = run(CfpTable.selectOne(id).option)

  override def getCfpAvailables(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]] = run(Queries.selectPage(CfpTable.selectPage(talk, _), params))

  override def createTalk(data: Talk.Data, by: User.Id): IO[Talk] =
    getTalk(by, data.slug).flatMap {
      case None => run(TalkTable.insert, Talk(Talk.Id.generate(), data.slug, data.title, data.duration, Talk.Status.Draft, data.description, NonEmptyList.one(by), Info(by)))
      case _ => IO.raiseError(CustomException(s"You already have a talk with slug ${data.slug}"))
    }

  override def getTalk(user: User.Id, slug: Talk.Slug): IO[Option[Talk]] = run(TalkTable.selectOne(user, slug).option)

  override def getTalks(user: User.Id, params: Page.Params): IO[Page[Talk]] = run(Queries.selectPage(TalkTable.selectPage(user, _), params))

  override def updateTalk(user: User.Id, slug: Talk.Slug)(data: Talk.Data): IO[Done] = {
    if(data.slug != slug) {
      // FIXME: should also check for other speakers !!!
      getTalk(user, data.slug).flatMap {
        case None => run(TalkTable.updateAll(user, slug)(data, Instant.now()))
        case _ => IO.raiseError(CustomException(s"You already have a talk with slug ${data.slug}"))
      }
    } else {
      run(TalkTable.updateAll(user, slug)(data, Instant.now()))
    }
  }

  override def updateTalkStatus(user: User.Id, slug: Talk.Slug)(status: Talk.Status): IO[Done] = run(TalkTable.updateStatus(user, slug)(status))

  override def createProposal(talk: Talk.Id, cfp: Cfp.Id, title: Talk.Title, description: Markdown, by: User.Id): IO[Proposal] =
    run(ProposalTable.insert, Proposal(Proposal.Id.generate(), talk, cfp, None, title, Proposal.Status.Pending, description, Info(by)))

  override def getProposal(id: Proposal.Id): IO[Option[Proposal]] = run(ProposalTable.selectOne(id).option)

  override def getProposal(talk: Talk.Id, cfp: Cfp.Id): IO[Option[Proposal]] = run(ProposalTable.selectOne(talk, cfp).option)

  override def getProposals(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(ProposalTable.selectPage(cfp, _), params))

  override def getProposals(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]] = run(Queries.selectPage(ProposalTable.selectPage(talk, _), params))

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
}
