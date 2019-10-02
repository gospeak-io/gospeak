package fr.gospeak.infra.services.storage.sql

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.GospeakConf
import fr.gospeak.core.domain.Contact.{FirstName, LastName}
import fr.gospeak.core.domain.User.Profile
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.TemplateData.EventInfo
import fr.gospeak.core.domain.utils.{Info, TemplateData}
import fr.gospeak.core.services.slack.domain.SlackAction
import fr.gospeak.core.services.storage.GospeakDb
import fr.gospeak.infra.services.GravatarSrv
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.infra.utils.{DoobieUtils, FlywayUtils}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}
import fr.gospeak.libs.scalautils.domain.TimePeriod._
import fr.gospeak.libs.scalautils.domain._
import fr.gospeak.migration.MongoRepo

import scala.concurrent.duration._

class GospeakDbSql(dbConf: DatabaseConf, gsConf: GospeakConf) extends GospeakDb {
  private val flyway = FlywayUtils.build(dbConf)
  private[sql] val xa: doobie.Transactor[IO] = DoobieUtils.transactor(dbConf)

  def migrate(): IO[Int] = IO(flyway.migrate())

  def dropTables(): IO[Done] = IO(flyway.clean()).map(_ => Done)

  override val user = new UserRepoSql(xa)
  override val talk = new TalkRepoSql(xa)
  override val group = new GroupRepoSql(xa)
  override val groupSettings = new GroupSettingsRepoSql(xa, gsConf)
  override val cfp = new CfpRepoSql(xa)
  override val partner = new PartnerRepoSql(xa)
  override val venue = new VenueRepoSql(xa)
  override val sponsorPack = new SponsorPackRepoSql(xa)
  override val sponsor = new SponsorRepoSql(xa)
  override val event = new EventRepoSql(xa)
  override val proposal = new ProposalRepoSql(xa)
  override val contact = new ContactRepoSql(xa)
  override val userRequest = new UserRequestRepoSql(group, talk, proposal, xa)

  /*
    TODO:
      - partner twitter account
      - still miss venue and sponsor contact (link to partner contact)
      - group settings (hardcoded)
   */
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
          u.auth.exists(_.isOrga) ||
            htTalks.exists(t => t.data.speakers.contains(u.id) || t.meta.createdBy == u.id || t.meta.updatedBy == u.id)
        }.map(_.toUser)
        orgas = htUsers.filter(_.auth.exists(_.isOrga)).map(_.toUser)
        lkn = orgas.find(_.email.value == "loicknuchel@gmail.com").get
        groupHt = Group(Group.Id.generate(), Group.Slug.from("humantalks-paris").get, Group.Name("HumanTalks Paris"), Some(EmailAddress.from("paris@humantalks.com").get), Markdown(
          """HumanTalks Paris, c'est un événement mensuel pour les développeuses et développeurs.
            |Chaque mois, 4 speakeuses et/ou speakeurs se succèdent pour présenter en 10 minutes un sujet de leur choix (language, méthode de programmation, projet perso, design...).
            |Quelque soit votre niveau vous pouvez proposer un talk aux organisateurs du HumanTalks de Paris. L'accès est gratuit !
            |
            |Les talks sont filmés et retransmis sur [notre chaine YouTube](https://www.youtube.com/channel/UCKFAwlgWiAB4vUpgnS63qog)
          """.stripMargin.trim), NonEmptyList.fromListUnsafe(orgas.map(_.id)), tags = Seq(), info = Info(lkn.id, initDate))
        cfpHt = Cfp(Cfp.Id.generate(), groupHt.id, Cfp.Slug.from("humantalks-paris").get, Cfp.Name("HumanTalks Paris"), None, None, Markdown(
          """Les HumanTalks sont des événements pour les développeuses et développeurs de tous horizons et qui ont lieu partout en France.
            |
            |Le principe est simple: 4 talks de 10 minutes tous les 2ème mardi du mois pour partager autour du développement logiciel au sens large: code bien sûr, mais aussi design, organisation, agilité...
            |
            |Nous acceuillons très volontiers des speakers débutant qui voudraient partager.
          """.stripMargin.trim), tags = Seq(), info = Info(lkn.id, initDate))
        talks = htTalks.map(_.toTalk).map(e => e.copy(info = e.info.copy(
          createdBy = users.find(_.id == e.info.createdBy).map(_.id).getOrElse(e.speakers.head),
          updatedBy = users.find(_.id == e.info.updatedBy).map(_.id).getOrElse(e.speakers.head)
        )))
        proposals = htTalks.map(_.toProposal(cfpHt.id)).map(e => e.copy(info = e.info.copy(
          createdBy = users.find(_.id == e.info.createdBy).map(_.id).getOrElse(e.speakers.head),
          updatedBy = users.find(_.id == e.info.updatedBy).map(_.id).getOrElse(e.speakers.head)
        )))
        partners = htPartners.map(_.toPartner(groupHt.id)).map(e => e.copy(info = e.info.copy(
          createdBy = users.find(_.id == e.info.createdBy).map(_.id).getOrElse(lkn.id),
          updatedBy = users.find(_.id == e.info.updatedBy).map(_.id).getOrElse(lkn.id)
        )))
        contacts = htPartners.flatMap(p => htUsers.filter(u => p.containsContact(u.id)).map(_.toContact(p))).map { case (id, e) =>
          (id, e.copy(info = e.info.copy(
            createdBy = users.find(_.id == e.info.createdBy).map(_.id).getOrElse(lkn.id),
            updatedBy = users.find(_.id == e.info.updatedBy).map(_.id).getOrElse(lkn.id)
          )))
        }
        standardPack = SponsorPack(SponsorPack.Id.generate(), groupHt.id, SponsorPack.Slug.from("standard").get, SponsorPack.Name("Standard"), Markdown(""), Price(500, Price.Currency.EUR), 1.year, active = false, Info(lkn.id, initDate))
        premiumPack = SponsorPack(SponsorPack.Id.generate(), groupHt.id, SponsorPack.Slug.from("premium").get, SponsorPack.Name("Premium"), Markdown(""), Price(1500, Price.Currency.EUR), 1.year, active = false, Info(lkn.id, initDate))
        bronzePack = SponsorPack(SponsorPack.Id.generate(), groupHt.id, SponsorPack.Slug.from("bronze").get, SponsorPack.Name("Bronze"), Markdown(""), Price(500, Price.Currency.EUR), 1.year, active = true, Info(lkn.id, initDate))
        silverPack = SponsorPack(SponsorPack.Id.generate(), groupHt.id, SponsorPack.Slug.from("silver").get, SponsorPack.Name("Silver"), Markdown(""), Price(1500, Price.Currency.EUR), 1.year, active = true, Info(lkn.id, initDate))
        goldPack = SponsorPack(SponsorPack.Id.generate(), groupHt.id, SponsorPack.Slug.from("gold").get, SponsorPack.Name("Gold"), Markdown(""), Price(2500, Price.Currency.EUR), 1.year, active = true, Info(lkn.id, initDate))
        platiniumPack = SponsorPack(SponsorPack.Id.generate(), groupHt.id, SponsorPack.Slug.from("platinium").get, SponsorPack.Name("Platinium"), Markdown(""), Price(6000, Price.Currency.EUR), 1.year, active = true, Info(lkn.id, initDate))
        sponsorPacks = List(standardPack, premiumPack, bronzePack, silverPack, goldPack, platiniumPack)
        sponsors = htPartners.flatMap(_.toSponsors(groupHt.id, sponsorPacks, contacts)).map(e => e.copy(info = e.info.copy(
          createdBy = users.find(_.id == e.info.createdBy).map(_.id).getOrElse(lkn.id),
          updatedBy = users.find(_.id == e.info.updatedBy).map(_.id).getOrElse(lkn.id)
        )))
        venues = htPartners.flatMap(_.toVenue(contacts)).map(e => e.copy(info = e.info.copy(
          createdBy = users.find(_.id == e.info.createdBy).map(_.id).getOrElse(lkn.id),
          updatedBy = users.find(_.id == e.info.updatedBy).map(_.id).getOrElse(lkn.id)
        )))
        events = htEvents.map(e => e.toEvent(groupHt.id, cfpHt.id, venues, proposals)).map(e => e.copy(info = e.info.copy(
          createdBy = users.find(_.id == e.info.createdBy).map(_.id).getOrElse(lkn.id),
          updatedBy = users.find(_.id == e.info.updatedBy).map(_.id).getOrElse(lkn.id)
        )))

        _ <- run(Queries.insertMany(UserRepoSql.insert)(NonEmptyList.fromListUnsafe(users)))
        // _ <- run(UserRepoSql.insertCredentials(User.Credentials("credentials", lkn.email.value, "bcrypt", "$2a$10$5r9NrHNAtujdA.qPcQHDm.xPxxTL/TAXU85RnP.7rDd3DTVPLCCjC", None))) // pwd: demo
        // _ <- run(UserRepoSql.insertLoginRef(User.LoginRef("credentials", lkn.email.value, lkn.id)))
        // _ <- run(UserRepoSql.validateAccount(lkn.email, lkn.created))
        _ <- run(GroupRepoSql.insert(groupHt))
        _ <- run(CfpRepoSql.insert(cfpHt))
        _ <- run(Queries.insertMany(TalkRepoSql.insert)(NonEmptyList.fromListUnsafe(talks)))
        _ <- run(Queries.insertMany(ProposalRepoSql.insert)(NonEmptyList.fromListUnsafe(proposals)))
        _ <- run(Queries.insertMany(PartnerRepoSql.insert)(NonEmptyList.fromListUnsafe(partners)))
        _ <- run(Queries.insertMany(ContactRepoSql.insert)(NonEmptyList.fromListUnsafe(contacts.map(_._2))))
        _ <- run(Queries.insertMany(SponsorPackRepoSql.insert)(NonEmptyList.fromListUnsafe(sponsorPacks)))
        _ <- run(Queries.insertMany(SponsorRepoSql.insert)(NonEmptyList.fromListUnsafe(sponsors)))
        // _ <- run(Queries.insertMany(VenueRepoSql.insert)(NonEmptyList.fromListUnsafe(venues))) // fail with: JdbcSQLException: Parameter "#10" is not set :(
        _ <- IO(venues.map(venue => run(Queries.insertOne(VenueRepoSql.insert)(venue)).unsafeRunSync()))
        _ <- run(Queries.insertMany(EventRepoSql.insert)(NonEmptyList.fromListUnsafe(events)))
        _ <- IO(events.map(event => addTalk(cfpHt, event, proposals.filter(p => event.talks.contains(p.id)), event.info.createdBy, event.info.updated)).map(_.unsafeRunSync()))
      } yield Done
    } { mongo => IO(mongo.close()) }
  }

  def insertMockData(conf: GospeakConf): IO[Done] = {
    val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
    var n = Instant.now()

    def now: Instant = { // to not have the same date everywhere
      n = n.plusSeconds(1)
      n
    }

    val gravatarSrv = new GravatarSrv()

    def user(slug: String, email: String, firstName: String, lastName: String, profile: Profile = User.emptyProfile): User = {
      val emailAddr = EmailAddress.from(email).get
      val avatar = gravatarSrv.getAvatar(emailAddr)
      User(User.Id.generate(), User.Slug.from(slug).get, firstName, lastName, emailAddr, Some(now), avatar, profile, now, now)
    }

    def group(slug: String, name: String, tags: Seq[String], by: User, owners: Seq[User] = Seq(), email: Option[String] = None): Group =
      Group(Group.Id.generate(), Group.Slug.from(slug).get, Group.Name(name), email.map(EmailAddress.from(_).get), description = Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), owners = NonEmptyList.of(by.id) ++ owners.map(_.id).toList, tags = tags.map(Tag(_)), info = Info(by.id, now))

    def cfp(group: Group, slug: String, name: String, start: Option[String], end: Option[String], description: String, tags: Seq[String], by: User): Cfp =
      Cfp(Cfp.Id.generate(), group.id, Cfp.Slug.from(slug).get, Cfp.Name(name), start.map(d => LocalDateTime.parse(d + "T00:00:00")), end.map(d => LocalDateTime.parse(d + "T00:00:00")), Markdown(description), tags.map(Tag(_)), Info(by.id, now))

    def talk(by: User, slug: String, title: String, status: Talk.Status = Talk.Status.Public, speakers: Seq[User] = Seq(), duration: Int = 10, slides: Option[Slides] = None, video: Option[Video] = None, description: String = "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", tags: Seq[String] = Seq()): Talk =
      Talk(Talk.Id.generate(), Talk.Slug.from(slug).get, status, Talk.Title(title), Duration(duration, MINUTES), Markdown(description), NonEmptyList.of(by.id) ++ speakers.map(_.id).toList, slides, video, tags.map(Tag(_)), Info(by.id, now))

    def proposal(talk: Talk, cfp: Cfp, status: Proposal.Status = Proposal.Status.Pending): Proposal =
      Proposal(Proposal.Id.generate(), talk.id, cfp.id, None, status, talk.title, talk.duration, talk.description, talk.speakers, talk.slides, talk.video, talk.tags, talk.info)

    def event(group: Group, cfp: Option[Cfp], slug: String, name: String, date: String, by: User, venue: Option[Venue] = None, description: MustacheMarkdownTmpl[EventInfo] = MustacheMarkdownTmpl[EventInfo](""), tags: Seq[String] = Seq()): Event =
      Event(Event.Id.generate(), group.id, cfp.map(_.id), Event.Slug.from(slug).get, Event.Name(name), LocalDateTime.parse(s"${date}T19:00:00"), description, venue.map(_.id), Seq(), tags.map(Tag(_)), Some(now).filter(_.isAfter(Instant.parse(date + "T06:06:24.074Z"))), Event.ExtRefs(), Info(by.id, now))

    def partner(g: Group, name: String, notes: String, description: Option[String], logo: Int, by: User): Partner =
      Partner(Partner.Id.generate(), g.id, Partner.Slug.from(StringUtils.slugify(name)).get, Partner.Name(name), Markdown(notes), description.map(Markdown), Url.from(s"https://www.freelogodesign.org/Content/img/logo-ex-$logo.png").get, None, Info(by.id, now))

    def contact(partner: Partner, email: String, firstName: String, lastName: String, by: User, description: String = ""): Contact =
      Contact(Contact.Id.generate(), partner.id, FirstName(firstName), LastName(lastName), EmailAddress.from(email).get, Markdown(description), Info(by.id, now))

    def venue(partner: Partner, address: GMapPlace, by: User, description: String = "", contact: Option[Contact] = None, roomSize: Option[Int] = None): Venue =
      Venue(Venue.Id.generate(), partner.id, contact.map(_.id), address, Markdown(description), roomSize, Venue.ExtRefs(), Info(by.id, now))

    def sponsorPack(group: Group, name: String, price: Int, by: User, description: String = "", active: Boolean = true): SponsorPack =
      SponsorPack(SponsorPack.Id.generate(), group.id, SponsorPack.Slug.from(StringUtils.slugify(name)).get, SponsorPack.Name(name), Markdown(description), Price(price, Price.Currency.EUR), 1.year, active, Info(by.id, now))

    def sponsor(group: Group, partner: Partner, pack: SponsorPack, by: User, start: String, finish: String, contact: Option[Contact] = None): Sponsor =
      Sponsor(Sponsor.Id.generate(), group.id, partner.id, pack.id, contact.map(_.id), LocalDate.parse(start), LocalDate.parse(finish), Some(LocalDate.parse(start)), Price(1500, Price.Currency.EUR), Info(by.id, now))

    val groupDefaultSettings = conf.defaultGroupSettings

    val userDemoProfil = User.Profile(User.Profile.Status.Public, Some(Markdown("Entrepreneur, functional programmer, OSS contributor, speaker, author.\nWork hard, stay positive, and live fearlessly.")),
      Some("Zeenea"), Some("Paris"), Some(Url.from("https://twitter.com/HumanTalks").get), Some(Url.from("https://www.linkedin.com/in/loicknuchel").get), None, Some(Url.from("https://humantalks.com").get))
    val userDemo = user("demo", "demo@mail.com", "Demo", "User", userDemoProfil)
    val userSpeaker = user("speaker", "speaker@mail.com", "Speaker", "User")
    val userOrga = user("orga", "orga@mail.com", "Orga", "User")
    val userEmpty = user("empty", "empty@mail.com", "Empty", "User")
    val users = NonEmptyList.of(userDemo, userSpeaker, userOrga, userEmpty)

    val credentials = users.map(u => User.Credentials("credentials", u.email.value, "bcrypt", "$2a$10$5r9NrHNAtujdA.qPcQHDm.xPxxTL/TAXU85RnP.7rDd3DTVPLCCjC", None)) // pwd: demo
    val loginRefs = users.map(u => User.LoginRef("credentials", u.email.value, u.id))

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

    val humanTalks = group("ht-paris", "HumanTalks Paris", Seq("tech"), userDemo, Seq(userOrga), Some("paris@humantalks.com"))
    val parisJs = group("paris-js", "Paris.Js", Seq("JavaScript"), userOrga)
    val dataGov = group("data-gov", "Data governance", Seq(), userDemo)
    val bigGroup = group("big-group", "Big Group", Seq("BigData"), userOrga)
    val groups = NonEmptyList.of(humanTalks, parisJs, dataGov, bigGroup)

    val humanTalksSettings = groupDefaultSettings.copy(
      actions = Map(
        Group.Settings.Action.Trigger.OnEventCreated -> Seq(
          Group.Settings.Action.Slack(SlackAction.PostMessage(
            MustacheMarkdownTmpl("{{event.start.year}}_{{event.start.month}}"),
            MustacheMarkdownTmpl("Meetup [{{event.name}}]({{event.link}}) créé !"),
            createdChannelIfNotExist = true,
            inviteEverybody = true)))),
      event = groupDefaultSettings.event.copy(
        templates = Map(
          "ROTI" -> MustacheTextTmpl[TemplateData.EventInfo](humanTalksRoti))))

    val cfp1 = cfp(humanTalks, "ht-paris", "HumanTalks Paris", None, None, "Les HumanTalks Paris c'est 4 talks de 10 min...", Seq("tag1", "tag2"), userDemo)
    val cfp2 = cfp(humanTalks, "ht-paris-day-1", "HumanTalks Paris Day - Edition 1", None, Some("2018-07-01"), "Les HumanTalks Paris c'est 4 talks de 10 min...", Seq(), userDemo)
    val cfp3 = cfp(humanTalks, "ht-paris-day-2", "HumanTalks Paris Day - Edition 2", Some("2019-03-01"), Some("2019-12-31"), "Les HumanTalks Paris c'est 4 talks de 10 min...", Seq(), userDemo)
    val cfp4 = cfp(parisJs, "paris-js", "Paris.Js", None, Some("2019-05-21"), "Submit your talk to exchange with the Paris JS community", Seq(), userOrga)
    val cfps = NonEmptyList.of(cfp1, cfp2, cfp3, cfp4)

    val proposal1 = proposal(talk1, cfp1)
    val proposal2 = proposal(talk2, cfp1)
    val proposal3 = proposal(talk2, cfp4)
    val proposal4 = proposal(talk3, cfp1, status = Proposal.Status.Declined)
    val proposal5 = proposal(talk4, cfp3)
    val proposals = NonEmptyList.of(proposal1, proposal2, proposal3, proposal4, proposal5)

    val zeenea = partner(humanTalks, "Zeenea", "Recrute des devs Scala et Angular", Some("A startup building a data catalog"), 1, userDemo)
    val criteo = partner(humanTalks, "Criteo", "", None, 2, userDemo)
    val nexeo = partner(humanTalks, "Nexeo", "", None, 3, userDemo)
    val google = partner(humanTalks, "Google", "", None, 4, userDemo)
    val partners = NonEmptyList.of(zeenea, criteo, nexeo, google)

    val zeeneaNina = contact(zeenea, "nina@zeenea.com", "Nina", "Truc", userDemo)
    val zeeneaJean = contact(zeenea, "jean@zeenea.com", "Jean", "Machin", userDemo)
    val criteoClaude = contact(criteo, "claude@criteo.com", "Claude", "Bidule", userDemo)
    val contacts = NonEmptyList.of(zeeneaNina, zeeneaJean, criteoClaude)

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
      geo = Geo(48.8716827, 2.3070390000000316),
      url = "https://maps.google.com/?cid=3360768160548514744",
      website = Some("https://www.zeenea.com/"),
      phone = None,
      utcOffset = 120,
      timezone = ZoneId.of("Europe/Paris"))
    val venue1 = venue(zeenea, zeeneaPlace, userDemo, contact = Some(zeeneaNina), roomSize = Some(80))
    val venues = NonEmptyList.of(venue1)

    val event1 = event(humanTalks, Some(cfp2), "2018-06", "HumanTalks Day #1", "2018-06-01", userDemo, venue = None, description = groupDefaultSettings.event.description)
    val event2 = event(humanTalks, None, "2019-01", "HumanTalks Paris Janvier 2019", "2019-01-08", userDemo, venue = None, description = groupDefaultSettings.event.description)
    val event3 = event(humanTalks, Some(cfp1), "2019-02", "HumanTalks Paris Fevrier 2019", "2019-02-12", userOrga, venue = Some(venue1))
    val event4 = event(humanTalks, Some(cfp1), "2019-11", "HumanTalks Paris Novembre 2019", "2019-11-12", userDemo, venue = Some(venue1), description = groupDefaultSettings.event.description)
    val event5 = event(parisJs, Some(cfp4), "2019-04", "Paris.Js Avril", "2019-04-01", userOrga)
    val event6 = event(dataGov, None, "2019-03", "Nouveaux modeles de gouvenance", "2019-03-15", userDemo, tags = Seq("Data Gouv"))
    val events = NonEmptyList.of(event1, event2, event3, event4, event5, event6)

    val eventTalks = NonEmptyList.of(
      (cfp1, event4, Seq(proposal1), humanTalks.owners.head),
      (cfp1, event3, Seq(proposal2), humanTalks.owners.head),
      (cfp4, event5, Seq(proposal3), parisJs.owners.head))

    val base = sponsorPack(humanTalks, "Base", 500, userDemo, "Description of the pack")
    val premium = sponsorPack(humanTalks, "Premium", 1500, userDemo)
    val old = sponsorPack(humanTalks, "Old", 100, userDemo, active = false)
    val packs = NonEmptyList.of(base, premium, old)

    val sponsor1 = sponsor(humanTalks, zeenea, base, userDemo, "2018-01-01", "2019-01-01", Some(zeeneaJean))
    val sponsor2 = sponsor(humanTalks, zeenea, premium, userDemo, "2019-01-01", "2020-01-01")
    val sponsor3 = sponsor(humanTalks, nexeo, base, userDemo, "2018-01-01", "2019-01-01")
    val sponsors = NonEmptyList.of(sponsor1, sponsor2, sponsor3)

    val generated = (1 to 25).toList.map { i =>
      val groupId = Group.Id.generate()
      val cfpId = Cfp.Id.generate()
      val g = Group(groupId, Group.Slug.from(s"z-group-$i").get, Group.Name(s"Z Group $i"), None, Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userOrga.id), Seq(), Info(userOrga.id, now))
      val c = Cfp(cfpId, groupId, Cfp.Slug.from(s"z-cfp-$i").get, Cfp.Name(s"Z CFP $i"), None, None, Markdown("Only your best talks !"), Seq(), Info(userOrga.id, now))
      val e = Event(Event.Id.generate(), bigGroup.id, None, Event.Slug.from(s"z-event-$i").get, Event.Name(s"Z Event $i"), LocalDateTime.parse("2019-03-12T19:00:00"), MustacheMarkdownTmpl(""), None, Seq(), Seq(), Some(now), Event.ExtRefs(), Info(userOrga.id, now))
      val t = Talk(Talk.Id.generate(), Talk.Slug.from(s"z-talk-$i").get, Talk.Status.Draft, Talk.Title(s"Z Talk $i"), Duration(10, MINUTES), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userSpeaker.id), None, None, Seq(), Info(userSpeaker.id, now))
      val p = Proposal(Proposal.Id.generate(), talk7.id, cfpId, None, Proposal.Status.Pending, Talk.Title(s"Z Proposal $i"), Duration(10, MINUTES), Markdown("temporary description"), NonEmptyList.of(userSpeaker.id), None, None, Seq(), Info(userSpeaker.id, now))
      (g, c, e, t, p)
    }

    for {
      _ <- run(Queries.insertMany(UserRepoSql.insert)(users))
      _ <- run(Queries.insertMany(UserRepoSql.insertCredentials)(credentials))
      _ <- run(Queries.insertMany(UserRepoSql.insertLoginRef)(loginRefs))
      _ <- run(Queries.insertMany(TalkRepoSql.insert)(talks ++ generated.map(_._4)))
      _ <- run(Queries.insertMany(GroupRepoSql.insert)(groups ++ generated.map(_._1)))
      _ <- run(Queries.insertMany(CfpRepoSql.insert)(cfps ++ generated.map(_._2)))
      _ <- run(Queries.insertMany(ProposalRepoSql.insert)(proposals ++ generated.map(_._5)))
      _ <- run(Queries.insertMany(PartnerRepoSql.insert)(partners))
      _ <- run(Queries.insertMany(ContactRepoSql.insert)(contacts))
      // _ <- run(Queries.insertMany(VenueRepoSql.insert)(venues)) // fail with: JdbcSQLException: Parameter "#10" is not set :(
      _ <- IO(venues.toList.map(venue => run(Queries.insertOne(VenueRepoSql.insert)(venue)).unsafeRunSync()))
      _ <- run(Queries.insertMany(SponsorPackRepoSql.insert)(packs))
      _ <- run(Queries.insertMany(SponsorRepoSql.insert)(sponsors))
      _ <- run(Queries.insertMany(EventRepoSql.insert)(events ++ generated.map(_._3)))
      _ <- IO(eventTalks.map { case (c, e, p, u) => addTalk(c, e, p, u, now) }.map(_.unsafeRunSync()))
      _ <- groupSettings.set(humanTalks.id, humanTalksSettings, userDemo.id, now)
    } yield Done
  }

  private val humanTalksRoti =
    s"""<!doctype html>
       |<html lang="en">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name='viewport' content='width=device-width, initial-scale=1' />
       |  <title>{{event.name}} ROTI</title>
       |  <style>
       |    body {
       |      font-family: helvetica;
       |      display: flex;
       |      flex-direction:column;
       |      height: 100vh;
       |      max-height: calc(100vh - 20px);
       |      padding: 10px;
       |      margin: 0;
       |    }
       |    h1 {
       |      text-align: center;
       |      font-size: 3rem;
       |      font-weight: normal;
       |      flex-shrink: 0;
       |      margin: 20px 0;
       |      margin-left: -20px;
       |      font-weight: bold;
       |      color: #790257;
       |    }
       |    .grid {
       |      display: grid;
       |      height: calc(100% - 95px);
       |      grid-template-columns: 20% repeat(3, 1fr);
       |      grid-template-rows: 75px repeat(5, 1fr);
       |      border: 2px solid #790257;
       |      border-top: none;
       |      flex-grow: 1;
       |      border-radius: 15px;
       |      overflow: hidden;
       |    }
       |    .grid > :nth-child(4n + 1) {
       |      border-left: none;
       |    }
       |    .grid > :nth-child(4n) {
       |      border-right: none;
       |    }
       |    .grid > :nth-last-child(1),
       |    .grid > :nth-last-child(2),
       |    .grid > :nth-last-child(3),
       |    .grid > :nth-last-child(4) {
       |      border-bottom: none;
       |    }
       |    .grid > :nth-child(1),
       |    .grid > :nth-child(2),
       |    .grid > :nth-child(3),
       |    .grid > :nth-child(4) {
       |      border-top: none;
       |    }
       |    .grid > * {
       |      border: 1px solid #790257;
       |      display: flex;
       |      align-items: center;
       |      align-content: center;
       |      flex-wrap: wrap;
       |      position: relative;
       |    }
       |    .good, .average, .bad {
       |      justify-content: center;
       |    }
       |    .title {
       |      padding: 10px;
       |      font-size: 1.2rem;
       |    }
       |    .title > span {
       |      max-width: 100%;
       |    }
       |    .talker {
       |      width: calc(100% - 50px);
       |      font-size: 1rem;
       |      font-style: italic;
       |      color: darkgrey;
       |    }
       |    .logo {
       |      width: 100px;
       |      transform: rotate(-15deg);
       |      display: block;
       |      margin-top: -40px;
       |    }
       |    .date {
       |      display: flex;
       |      align-items: center;
       |      justify-content: center;
       |      font-size: 1.5rem;
       |      color: darkgrey;
       |      text-align: center;
       |      font-style: italic;
       |      text-transform: capitalize;
       |    }
       |    .main-title {
       |      display: flex;
       |      align-items: center;
       |      justify-content: center;
       |      height: 80px;
       |      padding-top: 10px;
       |    }
       |    .title-cell.date {
       |      justify-content: space-around;
       |    }
       |    .title-cell {
       |      -webkit-print-color-adjust: exact;
       |      background-color: #790257;
       |      border-left-color: white;
       |      border-right-color: white;
       |      color: white;
       |    }
       |    .title-cell:first-child {
       |        border-left-color: #790257;
       |    }
       |    .title-cell:nth-child(4) {
       |        border-right-color: #790257;
       |    }
       |    .avatar {
       |      position: absolute;
       |      right: 5px;
       |      bottom: 5px;
       |    }
       |    .avatar > img {
       |      display: block;
       |      margin-left: auto;
       |      border-radius: 50%;
       |      width: 50px;
       |    }
       |    .sponsor {
       |        display: block;
       |        margin: 0 auto;
       |        margin-top: 10px;
       |        text-align: center;
       |    }
       |    .sponsor > img {
       |        height: 50px;
       |    }
       |    .url {
       |      font-size: 0.8rem;
       |      font-weight: bold;
       |      font-style: italic;
       |      text-align: right;
       |      margin-top: 5px;
       |      margin-right: 15px;
       |    }
       |    @media print {
       |      body {
       |        margin: 0;
       |      }
       |    }
       |  </style>
       |</head>
       |<body>
       |  <div class="main-title">
       |    <h1>Donnez votre avis !</h1>
       |  </div>
       |  <div class="grid">
       |    <div class="date title-cell">juillet<br/>2019</div>
       |    <div class="good title-cell"><svg xmlns="http://www.w3.org/2000/svg" width="70" height="70" viewBox="0 0 5.8208332 5.8208335"><g transform="translate(-11.33-290.78)"><circle r="5.5" cy="542.35" cx="488.27" stroke="white" fill="none" stroke-width="0.75" transform="matrix(.43294 0 0 .43294-197.15 58.882)"/><g transform="translate(-12.12 3.336)" fill="none" fill-rule="evenodd" stroke="white" stroke-linecap="round" stroke-width=".3"><path d="m24.674 290.11c.003-.572.683-.632.815-.107"/><path d="m27.24 289.86c.003-.572.683-.632.815-.107"/><path d="m25.03 290.76c.755.849 1.97.693 2.676-.081"/></g></g></svg></div>
       |    <div class="average title-cell"><svg xmlns="http://www.w3.org/2000/svg" width="70" height="70" viewBox="0 0 5.8208332 5.8208335"><g transform="matrix(-.43294 0 0 .43294 209.54-231.57)"><circle cx="469.83" cy="542.55" r="5.5" transform="translate(7.44-.975)" stroke="white" fill="none"stroke-width="0.75"/><g transform="translate(-10.87 -1)" fill="white"><circle cx="485.13" cy="542.3" r=".9"/><circle cx="491.15" cy="542.3" r=".9"/></g><path d="m475.38 544.35c0 0 .19-.614 1.402-.55.267.014.678.165.938.393.653.573 1.452.309 1.452.309" fill="none" fill-rule="evenodd" stroke="white" stroke-linecap="round" stroke-width=".75"/></g></svg></div>
       |    <div class="bad title-cell"><svg xmlns="http://www.w3.org/2000/svg" width="70" height="70" viewBox="0 0 5.8208332 5.8208335"><g transform="matrix(.29032 0 0 .29032 2.065-82.47)"><g transform="matrix(1.49127 0 0 1.49127-725.23-514.71)"><circle r="5.5" cy="542.35" cx="488.27" stroke="white" fill="none"stroke-width="0.75"/><g fill="white"><circle cx="485.18" cy="542.3" r=".9"/><circle cx="491.35" cy="542.3" r=".9"/></g></g><path d="m1.455 298.59c.476-1.323 2.476-1.323 2.91 0" fill="none" fill-rule="evenodd" stroke="white" stroke-linejoin="round" stroke-linecap="round" stroke-width="1.25"/></g></svg></div>
       |    {{#talks}}
       |    <div class="title">
       |      <span>{{title}}</span>
       |      {{#speakers}}<span class="talker">Par {{name}}</span>{{/speakers}}
       |      {{#speakers}}{{#-first}}<span class="avatar"><img src="{{avatar}}"/></span>{{/-first}}{{/speakers}}
       |    </div>
       |    <div class="good"></div>
       |    <div class="average"></div>
       |    <div class="bad"></div>
       |    {{/talks}}
       |    {{#venue}}
       |    <div class="title">
       |      <span>La salle et le buffet</span>
       |      <span class="sponsor"><img src="{{logoUrl}}"/></span>
       |    </div>
       |    <div class="good"></div>
       |    <div class="average"></div>
       |    <div class="bad"></div>
       |    {{/venue}}
       |  </div>
       |  <div class="url">http://humantalks.com/paris</div>
       |</body>
       |</html>
    """.stripMargin

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
