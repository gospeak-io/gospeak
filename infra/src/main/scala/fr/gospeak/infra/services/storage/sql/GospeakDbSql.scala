package fr.gospeak.infra.services.storage.sql

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.infra.services.GravatarSrv
import fr.gospeak.infra.services.storage.sql.tables._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.infra.utils.{DoobieUtils, FlywayUtils}
import fr.gospeak.libs.scalautils.domain._

import scala.concurrent.duration._

class GospeakDbSql(conf: DbSqlConf) extends GospeakDb {
  private val flyway = FlywayUtils.build(conf)
  private[sql] val xa: doobie.Transactor[IO] = DoobieUtils.transactor(conf)

  def createTables(): IO[Int] = IO(flyway.migrate())

  def dropTables(): IO[Done] = IO(flyway.clean()).map(_ => Done)

  def insertMockData(): IO[Done] = {
    val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
    val now = Instant.now()
    val gravatarSrv = new GravatarSrv()

    def user(slug: String, email: String, firstName: String, lastName: String): User = {
      val emailAddr = EmailAddress.from(email).right.get
      val avatar = gravatarSrv.getAvatar(emailAddr)
      User(User.Id.generate(), User.Slug.from(slug).right.get, firstName, lastName, emailAddr, None, avatar, now, now)
    }

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
    val event3 = event(group1, "2019-06", "HumanTalks Paris Juin 2019", "2019-06-12", userDemo, venue = None, description = Some("desc"))
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

  val user = new UserRepoSql(xa)
  val userRequest = new UserRequestRepoSql(xa)
  val group = new GroupRepoSql(xa)
  val cfp = new CfpRepoSql(xa)
  val event = new EventRepoSql(xa)
  val talk = new TalkRepoSql(xa)
  val proposal = new ProposalRepoSql(xa)

  private def run(i: => doobie.Update0): IO[Done] =
    i.run.transact(xa).flatMap {
      case 1 => IO.pure(Done)
      case code => IO.raiseError(CustomException(s"Failed to update $i (code: $code)"))
    }

  private def run[A](v: doobie.ConnectionIO[A]): IO[A] =
    v.transact(xa)
}
