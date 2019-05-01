package fr.gospeak.infra.services.storage.sql

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.infra.services.GravatarSrv
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.infra.utils.{DoobieUtils, FlywayUtils}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain._
import fr.gospeak.libs.scalautils.utils.StringUtils
import fr.gospeak.migration.MongoRepo

import scala.concurrent.duration._

class GospeakDbSql(conf: DatabaseConf) extends GospeakDb {
  private val flyway = FlywayUtils.build(conf)
  private[sql] val xa: doobie.Transactor[IO] = DoobieUtils.transactor(conf)

  def migrate(): IO[Int] = IO(flyway.migrate())

  def dropTables(): IO[Done] = IO(flyway.clean()).map(_ => Done)

  val user = new UserRepoSql(xa)
  val userRequest = new UserRequestRepoSql(xa)
  val group = new GroupRepoSql(xa)
  val cfp = new CfpRepoSql(xa)
  val event = new EventRepoSql(xa)
  val talk = new TalkRepoSql(xa)
  val proposal = new ProposalRepoSql(xa)
  val partner = new PartnerRepoSql(xa)
  val venue = new VenueRepoSql(xa)

  def insertHTData(mongoUri: String): IO[Done] = {
    val dbName = mongoUri.split('?').head.split('/').last
    IO.fromFuture(IO(MongoRepo.create(mongoUri, dbName).toFuture)).bracket { mongo =>
      for {
        htUsers <- IO.fromFuture(IO(mongo.loadPersons()))
        htTalks <- IO.fromFuture(IO(mongo.loadTalks()))
        htEvents <- IO.fromFuture(IO(mongo.loadEvents()))
        htPartners <- IO.fromFuture(IO(mongo.loadPartners()))

        initDate = Instant.ofEpochMilli(htUsers.map(_.meta.created).min)
        users = htUsers.filter { u =>
          htTalks.exists(t => t.data.speakers.contains(u.id) || t.meta.createdBy == u.id || t.meta.updatedBy == u.id) ||
            u.auth.exists(a => a.role == "Organizer" || a.role == "Admin")
        }.map(_.toUser)
        lkn = users.find(_.email.value == "loicknuchel@gmail.com").get
        orga = htUsers.filter(_.auth.exists(a => a.role == "Organizer" || a.role == "Admin")).map(_.toUser)
        group = Group(Group.Id.generate(), Group.Slug.from("humantalks-paris").get, Group.Name("HumanTalks Paris"), Markdown(
          """Description des HumanTalks
            |
            |TODO
          """.stripMargin.trim), NonEmptyList.fromListUnsafe(orga.map(_.id)), public = true, Seq(), Info(lkn.id, initDate))
        cfp = Cfp(Cfp.Id.generate(), group.id, Cfp.Slug.from("humantalks-paris").get, Cfp.Name("HumanTalks Paris"), None, None, Markdown(
          """Les HumanTalks sont des événements pour les développeurs de tous horizons et qui ont lieu partout en France.
            |
            |Le principe est simple: 4 talks de 10 minutes tous les 2ème mardi du mois pour partager autour du développement logiciel au sens large: code bien sûr, mais aussi design, organisation, agilité...
            |
            |Nous acceuillons très volontiers des speakers débutant qui voudraient partager.
          """.stripMargin.trim), Seq(), Info(lkn.id, initDate))
        talks = htTalks.map(_.toTalk).map(t => t.copy(info = t.info.copy(
          createdBy = users.find(_.id == t.info.createdBy).map(_.id).getOrElse(t.speakers.head),
          updatedBy = users.find(_.id == t.info.updatedBy).map(_.id).getOrElse(t.speakers.head)
        )))
        proposals = htTalks.map(_.toProposal.copy(cfp = cfp.id)).map(p => p.copy(info = p.info.copy(
          createdBy = users.find(_.id == p.info.createdBy).map(_.id).getOrElse(p.speakers.head),
          updatedBy = users.find(_.id == p.info.updatedBy).map(_.id).getOrElse(p.speakers.head)
        )))
        events = htEvents.map(e => e.toEvent.copy(group = group.id, cfp = Some(cfp.id), talks = e.data.talks.map(t => htTalks.find(_.id == t).get.toProposal.id))).map(e => e.copy(info = e.info.copy(
          createdBy = users.find(_.id == e.info.createdBy).map(_.id).getOrElse(lkn.id),
          updatedBy = users.find(_.id == e.info.updatedBy).map(_.id).getOrElse(lkn.id)
        )))

        _ <- run(Queries.insertMany(UserRepoSql.insert)(NonEmptyList.fromListUnsafe(users)))
        _ <- run(UserRepoSql.insertCredentials(User.Credentials("credentials", lkn.email.value, "bcrypt", "$2a$10$5r9NrHNAtujdA.qPcQHDm.xPxxTL/TAXU85RnP.7rDd3DTVPLCCjC", None))) // pwd: demo
        _ <- run(UserRepoSql.insertLoginRef(User.LoginRef("credentials", lkn.email.value, lkn.id)))
        _ <- run(GroupRepoSql.insert(group))
        _ <- run(CfpRepoSql.insert(cfp))
        _ <- run(Queries.insertMany(TalkRepoSql.insert)(NonEmptyList.fromListUnsafe(talks)))
        _ <- run(Queries.insertMany(ProposalRepoSql.insert)(NonEmptyList.fromListUnsafe(proposals)))
        _ <- run(Queries.insertMany(EventRepoSql.insert)(NonEmptyList.fromListUnsafe(events)))
        _ <- IO(events.map(e => addTalk(cfp, e, proposals.filter(p => e.talks.contains(p.id)), e.info.createdBy, e.info.updated)).map(_.unsafeRunSync()))
      } yield Done
    } { mongo => IO(mongo.close()) }
  }

  def insertMockData(): IO[Done] = {
    val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
    val now = Instant.now()
    val gravatarSrv = new GravatarSrv()

    def user(slug: String, email: String, firstName: String, lastName: String): User = {
      val emailAddr = EmailAddress.from(email).get
      val avatar = gravatarSrv.getAvatar(emailAddr)
      User(User.Id.generate(), User.Slug.from(slug).get, firstName, lastName, emailAddr, Some(now), avatar, public = true, now, now)
    }

    def group(slug: String, name: String, tags: Seq[String], by: User, owners: Seq[User] = Seq()): Group =
      Group(Group.Id.generate(), Group.Slug.from(slug).get, Group.Name(name), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(by.id) ++ owners.map(_.id).toList, public = true, tags.map(Tag(_)), Info(by.id, now))

    def cfp(group: Group, slug: String, name: String, start: Option[String], end: Option[String], description: String, tags: Seq[String], by: User): Cfp =
      Cfp(Cfp.Id.generate(), group.id, Cfp.Slug.from(slug).get, Cfp.Name(name), start.map(d => LocalDateTime.parse(d + "T00:00:00")), end.map(d => LocalDateTime.parse(d + "T00:00:00")), Markdown(description), tags.map(Tag(_)), Info(by.id, now))

    def talk(by: User, slug: String, title: String, status: Talk.Status = Talk.Status.Public, speakers: Seq[User] = Seq(), duration: Int = 10, slides: Option[Slides] = None, video: Option[Video] = None, description: String = "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", tags: Seq[String] = Seq()): Talk =
      Talk(Talk.Id.generate(), Talk.Slug.from(slug).get, status, Talk.Title(title), Duration(duration, MINUTES), Markdown(description), NonEmptyList.of(by.id) ++ speakers.map(_.id).toList, slides, video, tags.map(Tag(_)), Info(by.id, now))

    def proposal(talk: Talk, cfp: Cfp, status: Proposal.Status = Proposal.Status.Pending): Proposal =
      Proposal(Proposal.Id.generate(), talk.id, cfp.id, None, status, talk.title, talk.duration, talk.description, talk.speakers, talk.slides, talk.video, talk.tags, talk.info)

    def event(group: Group, cfp: Option[Cfp], slug: String, name: String, date: String, by: User, venue: Option[Venue] = None, description: String = "", tags: Seq[String] = Seq()): Event =
      Event(Event.Id.generate(), group.id, cfp.map(_.id), Event.Slug.from(slug).get, Event.Name(name), LocalDateTime.parse(s"${date}T19:00:00"), Markdown(description), venue.map(_.id), Seq(), tags.map(Tag(_)), Info(by.id, now))

    def partner(g: Group, name: String, description: String, logo: Int, by: User): Partner =
      Partner(Partner.Id.generate(), g.id, Partner.Slug.from(StringUtils.slugify(name)).get, Partner.Name(name), Markdown(description), Url.from(s"https://www.freelogodesign.org/Content/img/logo-ex-$logo.png").get, Info(by.id, now))

    def venue(partner: Partner, address: GMapPlace, by: User, description: String = "", roomSize: Option[Int] = None): Venue =
      Venue(Venue.Id.generate(), partner.id, address, Markdown(description), roomSize, Info(by.id, now))

    val userDemo = user("demo", "demo@mail.com", "Demo", "User")
    val userSpeaker = user("speaker", "speaker@mail.com", "Speaker", "User")
    val userOrga = user("orga", "orga@mail.com", "Orga", "User")
    val userEmpty = user("empty", "empty@mail.com", "Empty", "User")
    val users = NonEmptyList.of(userDemo, userSpeaker, userOrga, userEmpty)

    val talk1 = talk(userDemo, "why-fp", "Why FP", status = Talk.Status.Private, tags = Seq("FP"))
    val talk2 = talk(userDemo, "scala-best-practices", "Scala Best Practices", speakers = Seq(userSpeaker),
      slides = Some(Slides.from("https://docs.google.com/presentation/d/1wWRKbxz81AzhBJJqc505yUkileRPn5b-bNH1Th852f4").get),
      video = Some(Video.from("https://www.youtube.com/watch?v=Tm-qyMukBq4").get),
      description =
        """I have seen a lot of people struggleing with Scala because they were lost in all the feature and did not know which one to use and *not to use*.
          |This talk is for everyone to discuss about **best practices**:
          |- do not throw
          |- never use null
          |- go functional
        """.stripMargin.trim)
    val talk3 = talk(userDemo, "nodejs-news", "NodeJs news", status = Talk.Status.Draft)
    val talk4 = talk(userSpeaker, "scalajs-react", "ScalaJS + React = <3", status = Talk.Status.Draft, speakers = Seq(userDemo), duration = 50, tags = Seq("Scala"))
    val talk5 = talk(userSpeaker, "gagner-1-million", "Gagner 1 Million au BlackJack avec Akka", status = Talk.Status.Private, duration = 15)
    val talk6 = talk(userSpeaker, "demarrer-avec-spark", "7 conseils pour demarrer avec Spark", duration = 45)
    val talk7 = talk(userSpeaker, "big-talk", "Big Talk")
    val talks = NonEmptyList.of(talk1, talk2, talk3, talk4, talk5, talk6, talk7)

    val group1 = group("ht-paris", "HumanTalks Paris", Seq("tech"), userDemo, Seq(userOrga))
    val group2 = group("paris-js", "Paris.Js", Seq("JavaScript"), userOrga)
    val group3 = group("data-gov", "Data governance", Seq(), userDemo)
    val group4 = group("big-group", "Big Group", Seq("BigData"), userOrga)
    val groups = NonEmptyList.of(group1, group2, group3, group4)

    val cfp1 = cfp(group1, "ht-paris", "HumanTalks Paris", None, None, "Les HumanTalks Paris c'est 4 talks de 10 min...", Seq("tag1", "tag2"), userDemo)
    val cfp2 = cfp(group1, "ht-paris-day-1", "HumanTalks Paris Day - Edition 1", None, Some("2018-07-01"), "Les HumanTalks Paris c'est 4 talks de 10 min...", Seq(), userDemo)
    val cfp3 = cfp(group1, "ht-paris-day-2", "HumanTalks Paris Day - Edition 2", Some("2019-03-01"), Some("2019-07-01"), "Les HumanTalks Paris c'est 4 talks de 10 min...", Seq(), userDemo)
    val cfp4 = cfp(group2, "paris-js", "Paris.Js", None, Some("2019-05-21"), "Submit your talk to exchange with the Paris JS community", Seq(), userOrga)
    val cfps = NonEmptyList.of(cfp1, cfp2, cfp3, cfp4)

    val proposal1 = proposal(talk1, cfp1)
    val proposal2 = proposal(talk2, cfp1)
    val proposal3 = proposal(talk2, cfp4)
    val proposal4 = proposal(talk3, cfp1, status = Proposal.Status.Rejected)
    val proposals = NonEmptyList.of(proposal1, proposal2, proposal3, proposal4)

    val partner1 = partner(group1, "Zeenea", "", 1, userDemo)
    val partner2 = partner(group1, "Criteo", "", 2, userDemo)
    val partner3 = partner(group1, "Nexeo", "", 3, userDemo)
    val partner4 = partner(group1, "Google", "", 4, userDemo)
    val partners = NonEmptyList.of(partner1, partner2, partner3, partner4)

    val zeeneaPlace = GMapPlace(
      id = "ChIJ0wnrwMdv5kcRuOvv_dXYoy4",
      name = "Zeenea Data Catalog",
      streetNo = Some("48"),
      street = Some("Rue de Ponthieu"),
      postalCode = Some("75008"),
      locality = Some("Paris"),
      country = "France",
      formatted = "48 Rue de Ponthieu, 75008 Paris, France",
      input = "Zeenea Data Catalog, Rue de Ponthieu, Paris, France",
      lat = 48.8716827,
      lng = 2.3070390000000316,
      url = "https://maps.google.com/?cid=3360768160548514744",
      website = Some("https://www.zeenea.com/"),
      phone = None,
      utcOffset = 120)
    val venue1 = venue(partner1, zeeneaPlace, userDemo)
    val venues = NonEmptyList.of(venue1)

    val event1 = event(group1, Some(cfp2), "2018-06", "HumanTalks Day #1", "2018-06-01", userDemo, venue = None, description = "desc")
    val event2 = event(group1, None, "2019-01", "HumanTalks Paris Janvier 2019", "2019-01-08", userDemo, venue = None, description = "desc")
    val event3 = event(group1, Some(cfp1), "2019-02", "HumanTalks Paris Fevrier 2019", "2019-02-12", userOrga)
    val event4 = event(group1, Some(cfp1), "2019-06", "HumanTalks Paris Juin 2019", "2019-06-12", userDemo, venue = Some(venue1), description = "desc")
    val event5 = event(group2, Some(cfp4), "2019-04", "Paris.Js Avril", "2019-04-01", userOrga)
    val event6 = event(group3, None, "2019-03", "Nouveaux modeles de gouvenance", "2019-03-15", userDemo, tags = Seq("Data Gouv"))
    val events = NonEmptyList.of(event1, event2, event3, event4, event5, event6)

    val eventTalks = NonEmptyList.of(
      (cfp1, event3, Seq(proposal1), group1.owners.head),
      (cfp4, event5, Seq(proposal3), group2.owners.head))

    val generated = (1 to 25).toList.map { i =>
      val groupId = Group.Id.generate()
      val cfpId = Cfp.Id.generate()
      val g = Group(groupId, Group.Slug.from(s"z-group-$i").get, Group.Name(s"Z Group $i"), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userOrga.id), public = true, Seq(), Info(userOrga.id, now))
      val c = Cfp(cfpId, groupId, Cfp.Slug.from(s"z-cfp-$i").get, Cfp.Name(s"Z CFP $i"), None, None, Markdown("Only your best talks !"), Seq(), Info(userOrga.id, now))
      val e = Event(Event.Id.generate(), group4.id, None, Event.Slug.from(s"z-event-$i").get, Event.Name(s"Z Event $i"), LocalDateTime.parse("2019-03-12T19:00:00"), Markdown(""), None, Seq(), Seq(), Info(userOrga.id, now))
      val t = Talk(Talk.Id.generate(), Talk.Slug.from(s"z-talk-$i").get, Talk.Status.Draft, Talk.Title(s"Z Talk $i"), Duration(10, MINUTES), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userSpeaker.id), None, None, Seq(), Info(userSpeaker.id, now))
      val p = Proposal(Proposal.Id.generate(), talk7.id, cfpId, None, Proposal.Status.Pending, Talk.Title(s"Z Proposal $i"), Duration(10, MINUTES), Markdown("temporary description"), NonEmptyList.of(userSpeaker.id), None, None, Seq(), Info(userSpeaker.id, now))
      (g, c, e, t, p)
    }

    for {
      _ <- run(Queries.insertMany(UserRepoSql.insert)(users))
      _ <- run(UserRepoSql.insertCredentials(User.Credentials("credentials", "demo@mail.com", "bcrypt", "$2a$10$5r9NrHNAtujdA.qPcQHDm.xPxxTL/TAXU85RnP.7rDd3DTVPLCCjC", None))) // pwd: demo
      _ <- run(UserRepoSql.insertLoginRef(User.LoginRef("credentials", "demo@mail.com", userDemo.id)))
      _ <- run(Queries.insertMany(TalkRepoSql.insert)(talks ++ generated.map(_._4)))
      _ <- run(Queries.insertMany(GroupRepoSql.insert)(groups ++ generated.map(_._1)))
      _ <- run(Queries.insertMany(CfpRepoSql.insert)(cfps ++ generated.map(_._2)))
      _ <- run(Queries.insertMany(ProposalRepoSql.insert)(proposals ++ generated.map(_._5)))
      _ <- run(Queries.insertMany(PartnerRepoSql.insert)(partners))
      // _ <- run(Queries.insertMany(VenueRepoSql.insert)(venues)) // fail with: JdbcSQLException: Parameter "#10" is not set :(
      _ <- IO(venues.toList.map(venue => run(Queries.insertOne(VenueRepoSql.insert)(venue)).unsafeRunSync()))
      _ <- run(Queries.insertMany(EventRepoSql.insert)(events ++ generated.map(_._3)))
      _ <- IO(eventTalks.map { case (c, e, p, u) => addTalk(c, e, p, u, now) }.map(_.unsafeRunSync()))
    } yield Done
  }

  private def addTalk(cfp: Cfp, event: Event, proposals: Seq[Proposal], by: User.Id, now: Instant): IO[Done] = for {
    _ <- run(EventRepoSql.updateTalks(event.group, event.slug)(proposals.map(_.id), by, now))
    _ <- IO(proposals.map(p => run(ProposalRepoSql.updateStatus(cfp.slug, p.id)(Proposal.Status.Accepted, Some(event.id)))).map(_.unsafeRunSync()))
  } yield Done

  private def run(i: => doobie.Update0): IO[Done] =
    i.run.transact(xa).flatMap {
      case 1 => IO.pure(Done)
      case code => IO.raiseError(CustomException(s"Failed to update $i (code: $code)"))
    }

  private def run[A](v: doobie.ConnectionIO[A]): IO[A] =
    v.transact(xa)
}
