package fr.gospeak.infra.services.storage.sql

import java.time.{Instant, LocalDate, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain.Contact.{FirstName, LastName}
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import fr.gospeak.core.domain.utils.TemplateData.EventInfo
import fr.gospeak.core.domain.utils._
import fr.gospeak.core.services.cloudinary.CloudinarySrv
import fr.gospeak.core.services.slack.domain.SlackAction
import fr.gospeak.core.services.storage.{DatabaseConf, GospeakDb}
import fr.gospeak.core.{ApplicationConf, GospeakConf}
import fr.gospeak.infra.services.GravatarSrv
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.{DoobieUtils, FlywayUtils}
import fr.gospeak.infra.utils.HttpClient
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}
import fr.gospeak.libs.scalautils.domain.TimePeriod._
import fr.gospeak.libs.scalautils.domain._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.control.NonFatal

class GospeakDbSql(dbConf: DatabaseConf,
                   gsConf: GospeakConf,
                   cloudinarySrv: CloudinarySrv // FIXME[cloudinary-migration] to remove
                  ) extends GospeakDb {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val flyway = FlywayUtils.build(dbConf)
  private[sql] val xa: doobie.Transactor[IO] = DoobieUtils.transactor(dbConf)

  def checkEnv(appEnv: ApplicationConf.Env): IO[Int] = {
    import doobie.implicits._
    def createTable(): IO[Int] = {
      fr0"CREATE TABLE env (name VARCHAR(10) NOT NULL PRIMARY KEY);".update.run.transact(xa)
        .flatMap(_ => fr0"INSERT INTO env (name) VALUES ($appEnv);".update.run.transact(xa))
    }

    fr0"SELECT name FROM env".query[ApplicationConf.Env].unique.transact(xa).flatMap { dbEnv =>
      if (dbEnv == appEnv) {
        IO.pure(0)
      } else {
        IO.raiseError(new IllegalStateException(s"Can't boot $appEnv app on $dbEnv db"))
      }
    }.recoverWith {
      case e: org.h2.jdbc.JdbcSQLSyntaxErrorException if e.getMessage.contains("Table \"env\" not found") => createTable()
      case e: org.postgresql.util.PSQLException if e.getMessage.startsWith("ERROR: relation \"env\" does not exist") => createTable()
      case NonFatal(e) => IO.raiseError(new IllegalStateException(s"Can't check db env due to unknown error ${e.getClass.getSimpleName}: ${e.getMessage}", e))
    }
  }

  def migrate(): IO[Int] = IO(flyway.migrate())

  def dropTables(): IO[Done] = IO(flyway.clean()).map(_ => Done)

  // FIXME[cloudinary-migration] to remove after migration
  def migrateImages(): IO[Done] = {
    val gravatarSrv = new GravatarSrv()
    (for {
      cfps <- externalCfp.listAll()
      _ = logger.info(s"${cfps.length} cfps found")
      (cfpsToImport, cfpsToReset) <- cfps
        .collect { case c if c.logo.exists(l => !l.isCloudinary && !l.isGravatar) => (c, c.logo.get) }
        .map { case (c, l) => HttpClient.get(l.value).map(r => (c, l, r)) }.sequence
        .map(_.partition(_._3.isOk))
      _ = cfpsToReset.foreach { case (c, _, _) => logger.warn(s"Can't reset external cfp ${c.name.value} (${c.id.value})") }
      _ <- cfpsToImport.map { case (c, l, _) => cloudinarySrv.uploadExternalCfpLogo(c, l).flatMap(externalCfp.editLogo(c, _)) }.sequence
      _ = logger.info(s"imported ${cfpsToImport.length} cfps")

      users <- user.listAll()
      _ = logger.info(s"${users.length} users found")
      (usersToImport, usersToReset) <- users
        .filter(u => !u.avatar.isCloudinary && !u.avatar.isGravatar)
        .map(u => HttpClient.get(u.avatar.value).map(r => (u, r))).sequence
        .map(_.partition(_._2.isOk))
      _ <- usersToReset.map { case (u, _) => user.editAvatar(u, gravatarSrv.getAvatar(u.email)) }.sequence
      _ <- usersToImport.map { case (u, _) => cloudinarySrv.uploadAvatar(u).flatMap(user.editAvatar(u, _)) }.sequence
      _ = logger.info(s"imported ${usersToImport.length} users and reseted ${usersToReset.length} users")

      groups <- group.listAll()
      _ = logger.info(s"${groups.length} groups found")
      (groupLogosToImport, groupLogosToReset) <- groups
        .collect { case c if c.logo.exists(l => !l.isCloudinary && !l.isGravatar) => (c, c.logo.get) }
        .map { case (c, l) => HttpClient.get(l.value).map(r => (c, l, r)) }.sequence
        .map(_.partition(_._3.isOk))
      _ = groupLogosToReset.foreach { case (g, _, _) => logger.warn(s"Can't reset group ${g.name.value} logo (${g.id.value})") }
      _ <- groupLogosToImport.map { case (g, l, _) => cloudinarySrv.uploadGroupLogo(g, l).flatMap(group.editLogo(g, _)) }.sequence
      _ = logger.info(s"imported ${groupLogosToImport.length} group logos")

      (groupBannersToImport, groupBannersToReset) <- groups
        .collect { case c if c.banner.exists(l => !l.isCloudinary && !l.isGravatar) => (c, c.banner.get) }
        .map { case (c, b) => HttpClient.get(b.value).map(r => (c, b, r)) }.sequence
        .map(_.partition(_._3.isOk))
      _ = groupBannersToReset.foreach { case (g, _, _) => logger.warn(s"Can't reset group ${g.name.value} banner (${g.id.value})") }
      _ <- groupBannersToImport.map { case (g, b, _) => cloudinarySrv.uploadGroupBanner(g, b).flatMap(group.editBanner(g, _)) }.sequence
      _ = logger.info(s"imported ${groupBannersToImport.length} group banners")

      partners <- partner.listAll()
      _ = logger.info(s"${partners.length} partners found")
      (partnersToImport, partnersToReset) <- partners
        .filter { case (p, _) => !p.logo.isCloudinary && !p.logo.isGravatar }
        .map { case (p, g) => HttpClient.get(p.logo.value).map(r => (p, g, r)) }.sequence
        .map(_.partition(_._3.isOk))
      _ = partnersToReset.foreach { case (p, _, _) => logger.warn(s"Can't reset partner ${p.name.value} logo (${p.id.value})") }
      _ <- partnersToImport.map { case (p, g, _) => cloudinarySrv.uploadPartnerLogo(g, p).flatMap(partner.editLogo(p, _)) }.sequence
      _ = logger.info(s"imported ${partnersToImport.length} partners")
    } yield Done).recover { case NonFatal(e) => logger.error(s"migrateImages error ${e.getClass.getSimpleName}: ${e.getMessage}"); Done }
  }

  override val user = new UserRepoSql(xa)
  override val talk = new TalkRepoSql(xa)
  override val group = new GroupRepoSql(xa)
  override val groupSettings = new GroupSettingsRepoSql(xa, gsConf)
  override val cfp = new CfpRepoSql(xa)
  override val partner = new PartnerRepoSql(xa)
  override val contact = new ContactRepoSql(xa)
  override val venue = new VenueRepoSql(xa, partner, contact)
  override val sponsorPack = new SponsorPackRepoSql(xa)
  override val sponsor = new SponsorRepoSql(xa)
  override val event = new EventRepoSql(xa)
  override val proposal = new ProposalRepoSql(xa)
  override val comment = new CommentRepoSql(xa)
  override val externalCfp = new ExternalCfpRepoSql(xa)
  override val userRequest = new UserRequestRepoSql(xa, group, talk, proposal)

  def insertMockData(): IO[Done] = {
    val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
    var n = Instant.now()

    def now: Instant = { // to not have the same date everywhere
      n = n.plusSeconds(1)
      n
    }

    val gravatarSrv = new GravatarSrv()

    def user(slug: String, email: String, firstName: String, lastName: String, status: User.Status = User.Status.Undefined, emailValidated: Option[Instant] = Some(now), emailValidationBeforeLogin: Boolean = false, avatar: Option[String] = None, bio: Option[Markdown] = None, company: Option[String] = None, location: Option[String] = None, phone: Option[String] = None, website: Option[Url] = None, social: SocialAccounts = SocialAccounts.fromUrls()): User = {
      val emailAddr = EmailAddress.from(email).get
      val avatarObj = avatar.map(Url.from(_).get).map(Avatar).getOrElse(gravatarSrv.getAvatar(emailAddr))
      User(id = User.Id.generate(), slug = User.Slug.from(slug).get, status = status, firstName = firstName, lastName = lastName, email = emailAddr, emailValidated = emailValidated, emailValidationBeforeLogin = emailValidationBeforeLogin, avatar = avatarObj, bio = bio, company = company, location = location, phone = phone, website = website, social = social, createdAt = now, updatedAt = now)
    }

    def group(slug: String, name: String, tags: Seq[String], by: User, location: Option[GMapPlace] = None, owners: Seq[User] = Seq(), social: SocialAccounts = SocialAccounts.fromUrls(), email: Option[String] = None): Group =
      Group(Group.Id.generate(), Group.Slug.from(slug).get, Group.Name(name), None, None, email.map(EmailAddress.from(_).get), None, description = Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), location, owners = NonEmptyList.of(by.id) ++ owners.map(_.id).toList, social, tags = tags.map(Tag(_)), Group.Status.Active, info = Info(by.id, now))

    def cfp(group: Group, slug: String, name: String, start: Option[String], end: Option[String], description: String, tags: Seq[String], by: User): Cfp =
      Cfp(Cfp.Id.generate(), group.id, Cfp.Slug.from(slug).get, Cfp.Name(name), start.map(d => LocalDateTime.parse(d + "T00:00:00")), end.map(d => LocalDateTime.parse(d + "T00:00:00")), Markdown(description), tags.map(Tag(_)), Info(by.id, now))

    def talk(by: User, slug: String, title: String, status: Talk.Status = Talk.Status.Public, speakers: Seq[User] = Seq(), duration: Int = 10, slides: Option[Slides] = None, video: Option[Video] = None, description: String = "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", tags: Seq[String] = Seq()): Talk =
      Talk(Talk.Id.generate(), Talk.Slug.from(slug).get, status, Talk.Title(title), Duration(duration, MINUTES), Markdown(description), NonEmptyList.of(by.id) ++ speakers.map(_.id).toList, slides, video, tags.map(Tag(_)), Info(by.id, now))

    def proposal(talk: Talk, cfp: Cfp, status: Proposal.Status = Proposal.Status.Pending, orgaTags: Seq[String] = Seq()): Proposal =
      Proposal(Proposal.Id.generate(), talk.id, cfp.id, None, status, talk.title, talk.duration, talk.description, talk.speakers, talk.slides, talk.video, talk.tags, orgaTags.map(Tag(_)), talk.info)

    def event(group: Group, cfp: Option[Cfp], slug: String, name: String, date: String, by: User, maxAttendee: Option[Int], allowRsvp: Boolean = false, venue: Option[Venue] = None, description: MustacheMarkdownTmpl[EventInfo] = MustacheMarkdownTmpl[EventInfo](""), tags: Seq[String] = Seq(), published: Boolean = true): Event =
      Event(Event.Id.generate(), group.id, cfp.map(_.id), Event.Slug.from(slug).get, Event.Name(name), LocalDateTime.parse(s"${date}T19:00:00"), maxAttendee, allowRsvp, description, Event.Notes("", now, by.id), venue.map(_.id), Seq(), tags.map(Tag(_)), if (published) Some(Instant.parse(date + "T06:06:24.074Z")) else None, Event.ExtRefs(), Info(by.id, now))

    def partner(g: Group, name: String, notes: String, description: Option[String], logo: Int, by: User, social: SocialAccounts = SocialAccounts.fromUrls()): Partner =
      Partner(Partner.Id.generate(), g.id, Partner.Slug.from(StringUtils.slugify(name)).get, Partner.Name(name), Markdown(notes), description.map(Markdown), Url.from(s"https://www.freelogodesign.org/Content/img/logo-ex-$logo.png").map(Logo).get, social, Info(by.id, now))

    def contact(partner: Partner, email: String, firstName: String, lastName: String, by: User, description: String = ""): Contact =
      Contact(Contact.Id.generate(), partner.id, FirstName(firstName), LastName(lastName), EmailAddress.from(email).get, Markdown(description), Info(by.id, now))

    def venue(partner: Partner, address: GMapPlace, by: User, description: String = "", contact: Option[Contact] = None, roomSize: Option[Int] = None): Venue =
      Venue(Venue.Id.generate(), partner.id, contact.map(_.id), address, Markdown(description), roomSize, Venue.ExtRefs(), Info(by.id, now))

    def sponsorPack(group: Group, name: String, price: Int, by: User, description: String = "", active: Boolean = true): SponsorPack =
      SponsorPack(SponsorPack.Id.generate(), group.id, SponsorPack.Slug.from(StringUtils.slugify(name)).get, SponsorPack.Name(name), Markdown(description), Price(price, Price.Currency.EUR), 1.year, active, Info(by.id, now))

    def sponsor(group: Group, partner: Partner, pack: SponsorPack, by: User, start: String, finish: String, contact: Option[Contact] = None): Sponsor =
      Sponsor(Sponsor.Id.generate(), group.id, partner.id, pack.id, contact.map(_.id), LocalDate.parse(start), LocalDate.parse(finish), Some(LocalDate.parse(start)), Price(1500, Price.Currency.EUR), Info(by.id, now))

    def member(group: Group, user: User): Group.Member = Group.Member(group.id, Group.Member.Role.Member, None, now, None, user)

    def rsvp(event: Event, user: User, answer: Event.Rsvp.Answer = Event.Rsvp.Answer.Yes): Event.Rsvp = Event.Rsvp(event.id, answer, now, user)

    def cfpExt(name: String, url: String, logo: Option[String] = None, begin: Option[String] = None, close: Option[String] = None, start: Option[String] = None, finish: Option[String] = None, eventUrl: Option[String] = None, address: Option[GMapPlace] = None, ticketsUrl: Option[String] = None, videosUrl: Option[String] = None, twitterAccount: Option[String] = None, twitterHashtag: Option[String] = None, tags: Seq[String] = Seq(), description: String = "", by: User): ExternalCfp =
      ExternalCfp(ExternalCfp.Id.generate(), ExternalCfp.Name(name), logo.map(Url.from(_).get).map(Logo), Markdown(description), begin.map(d => LocalDateTime.parse(d + "T00:00:00")), close.map(d => LocalDateTime.parse(d + "T00:00:00")), Url.from(url).get, ExternalCfp.Event(start.map(d => LocalDateTime.parse(d + "T00:00:00")), finish.map(d => LocalDateTime.parse(d + "T00:00:00")), eventUrl.map(Url.from(_).get), address, ticketsUrl.map(Url.from(_).get), videosUrl.map(Url.from(_).get), twitterAccount.map(a => Url.from("https://twitter.com/" + a).get).map(TwitterAccount), twitterHashtag.map(TwitterHashtag.from(_).get)), tags.map(Tag(_)), Info(by.id, now))

    val groupDefaultSettings = gsConf.defaultGroupSettings

    val parisPlace = GMapPlace(
      id = "ChIJD7fiBh9u5kcRYJSMaMOCCwQ",
      name = "Paris",
      streetNo = None,
      street = None,
      postalCode = None,
      locality = Some("Paris"),
      country = "France",
      formatted = "Paris, France",
      input = "Paris, France",
      geo = Geo(48.85661400000001, 2.3522219000000177),
      url = "https://maps.google.com/?q=Paris,+France&ftid=0x47e66e1f06e2b70f:0x40b82c3688c9460",
      website = Some("http://www.paris.fr/"),
      phone = None,
      utcOffset = 60)
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
      utcOffset = 60)
    val palaisDesCongres = GMapPlace(
      id = "ChIJKW6JQItv5kcRbVSjqnRf9jA",
      name = "Palais Des Congrès",
      streetNo = None,
      street = None,
      postalCode = Some("75017"),
      locality = Some("Paris"),
      country = "France",
      formatted = "75017 Paris, France",
      input = "Palais Des Congrès, Paris, France",
      geo = Geo(48.8786842, 2.2831416000000218),
      url = "https://maps.google.com/?cid=3528112312775038061",
      website = None,
      phone = None,
      utcOffset = 60)

    val social = SocialAccounts.fromStrings(
      facebook = Some("https://www.facebook.com/GoSpeak-116563639716599"),
      instagram = Some("https://www.instagram.com/gospeak"),
      twitter = Some("https://twitter.com/HumanTalks"),
      linkedIn = Some("https://www.linkedin.com/in/loicknuchel"),
      youtube = Some("https://www.youtube.com/channel/UCKFAwlgWiAB4vUpgnS63qog"),
      meetup = Some("https://www.meetup.com/fr-FR/HumanTalks-Paris"),
      eventbrite = Some("https://www.eventbrite.com/o/the-family-15751873639"),
      slack = Some("https://humantalks.slack.com"),
      discord = Some("https://discordapp.com/invite/3MMu9cR"),
      github = Some("https://github.com/gospeak-io")).get

    val userDemo = user(
      slug = "demo",
      email = "demo@mail.com",
      firstName = "Demo",
      lastName = "User",
      status = User.Status.Public,
      avatar = Some("https://image.flaticon.com/icons/png/512/168/168728.png"),
      bio = Some(Markdown("Entrepreneur, functional programmer, OSS contributor, speaker, author.\nWork hard, stay positive, and live fearlessly.")),
      company = Some("Zeenea"),
      location = Some("Paris"),
      phone = Some("06 66 66 66 66"),
      website = Some(Url.from("https://humantalks.com").get),
      social = social)
    val userSpeaker = user("speaker", "speaker@mail.com", "Speaker", "User")
    val userOrga = user("orga", "orga@mail.com", "Orga", "User")
    val userEmpty = user("empty", "empty@mail.com", "Empty", "User", emailValidated = None, avatar = Some("https://broken.com/icon.png"))
    val lkn = user("exist", "exist@mail.com", "Exist", "User", emailValidated = None, emailValidationBeforeLogin = true)
    val users = Seq(userDemo, userSpeaker, userOrga, userEmpty, lkn)

    val credentials = users.filterNot(_.slug.value == "exist").map(u => User.Credentials("credentials", u.email.value, "bcrypt", "$2a$10$5r9NrHNAtujdA.qPcQHDm.xPxxTL/TAXU85RnP.7rDd3DTVPLCCjC", None)) // pwd: demo
    val loginRefs = users.filterNot(_.slug.value == "exist").map(u => User.LoginRef("credentials", u.email.value, u.id))

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
        """.stripMargin.trim, tags = Seq("tech", "scala", "beginner"))
    val talk3 = talk(userDemo, "nodejs-news", "NodeJs news", status = Talk.Status.Draft)
    val talk4 = talk(userSpeaker, "scalajs-react", "ScalaJS + React = <3", status = Talk.Status.Draft, speakers = Seq(userDemo), duration = 50, tags = Seq("Scala"))
    val talk5 = talk(userSpeaker, "gagner-1-million", "Gagner 1 Million au BlackJack avec Akka", status = Talk.Status.Private, duration = 15)
    val talk6 = talk(userSpeaker, "demarrer-avec-spark", "7 conseils pour demarrer avec Spark", duration = 45)
    val talk7 = talk(userSpeaker, "big-talk", "Big Talk")
    val talks = Seq(talk1, talk2, talk3, talk4, talk5, talk6, talk7)

    val humanTalks = group("ht-paris", "HumanTalks Paris", Seq("tech"), userDemo, location = Some(parisPlace), owners = Seq(userOrga), email = Some("paris@humantalks.com"), social = social)
    val parisJs = group("paris-js", "Paris.Js", Seq("JavaScript"), userOrga)
    val dataGov = group("data-gov", "Data governance", Seq(), userDemo)
    val bigGroup = group("big-group", "Big Group", Seq("BigData"), userOrga)
    val groups = Seq(humanTalks, parisJs, dataGov, bigGroup)

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
    val cfps = Seq(cfp1, cfp2, cfp3, cfp4)

    val proposal1 = proposal(talk1, cfp1)
    val proposal2 = proposal(talk2, cfp1, orgaTags = Seq("accepted", "1st round"))
    val proposal3 = proposal(talk2, cfp4)
    val proposal4 = proposal(talk3, cfp1, status = Proposal.Status.Declined)
    val proposal5 = proposal(talk4, cfp3)
    val proposals = Seq(proposal1, proposal2, proposal3, proposal4, proposal5)

    val zeeneaHT = partner(humanTalks, "Zeenea", "Recrute des devs Scala et Angular", Some("A startup building a data catalog"), 1, userDemo, social = social)
    val criteo = partner(humanTalks, "Criteo", "", None, 2, userDemo)
    val nexeo = partner(humanTalks, "Nexeo", "", None, 3, userDemo)
    val google = partner(humanTalks, "Google", "", None, 4, userDemo)
    // FIXME strange behaviour: if variable is name as social (facebook, eventbrite...), the compilation fails... WTF
    // if works when I remove the named params in SocialAccounts.fromStrings
    val zeeneaDG = partner(dataGov, "Zeenea", "Recrute des devs Scala et Angular", Some("A startup building a data catalog"), 1, userDemo, social = social)
    val voodoo = partner(dataGov, "Voodoo", "", None, 2, userDemo)
    val partners = Seq(zeeneaHT, criteo, nexeo, google, zeeneaDG, voodoo)

    val zeeneaHTNina = contact(zeeneaHT, "nina@zeenea.com", "Nina", "Truc", userDemo, description = "Awesome partner, **very friendly**")
    val zeeneaHTJean = contact(zeeneaHT, "jean@zeenea.com", "Jean", "Machin", userDemo)
    val criteoClaude = contact(criteo, "claude@criteo.com", "Claude", "Bidule", userDemo)
    val zeeneaDGNina = contact(zeeneaDG, "nina@zeenea.com", "Nina", "Truc", userDemo)
    val contacts = Seq(zeeneaHTNina, zeeneaHTJean, criteoClaude, zeeneaDGNina)

    val venue1 = venue(zeeneaHT, zeeneaPlace, userDemo, contact = Some(zeeneaHTNina), roomSize = Some(80), description = "Super accueil, à refaire à l'occasion")
    val venue2 = venue(zeeneaDG, zeeneaPlace, userDemo, contact = Some(zeeneaDGNina), roomSize = Some(80))
    val venue3 = venue(voodoo, palaisDesCongres, userDemo)
    val venues = Seq(venue1, venue2, venue3)

    val event1 = event(humanTalks, Some(cfp2), "2018-06", "HumanTalks Day #1", "2018-06-01", userDemo, Some(100), venue = None, description = groupDefaultSettings.event.description)
    val event2 = event(humanTalks, None, "2019-01", "HumanTalks Paris Janvier 2019", "2019-01-08", userDemo, Some(120), venue = None, description = groupDefaultSettings.event.description)
    val event3 = event(humanTalks, Some(cfp1), "2019-02", "HumanTalks Paris Fevrier 2019", "2019-02-12", userOrga, Some(50), venue = Some(venue1))
    val event4 = event(humanTalks, Some(cfp1), "2019-12", "HumanTalks Paris Decembre 2019", "2019-12-10", userDemo, Some(100), venue = Some(venue1), description = groupDefaultSettings.event.description, allowRsvp = true)
    val event7 = event(humanTalks, Some(cfp1), "2020-01", "HumanTalks Paris Janvier 2020", "2020-01-14", userDemo, Some(100), venue = Some(venue1), description = groupDefaultSettings.event.description, published = false)
    val event8 = event(humanTalks, Some(cfp1), "2020-04", "HumanTalks Paris Avril 2020", "2020-04-14", userDemo, Some(100), venue = None, description = groupDefaultSettings.event.description, published = false)
    val event5 = event(parisJs, Some(cfp4), "2019-04", "Paris.Js Avril", "2019-04-01", userOrga, None)
    val event6 = event(dataGov, None, "2019-03", "Nouveaux modeles de gouvenance", "2019-03-15", userDemo, Some(100), venue = Some(venue2), tags = Seq("Data Gouv"))
    val event9 = event(dataGov, None, "2019-04", "Nouveaux modeles de gouvenance", "2019-04-15", userDemo, Some(100), venue = Some(venue3), tags = Seq("Data Gouv"))
    val events = Seq(event1, event2, event3, event4, event5, event6, event7, event8, event9)

    val eventTalks = Seq(
      (cfp1, event4, Seq(proposal1), humanTalks.owners.head),
      (cfp1, event8, Seq(proposal2, proposal4), humanTalks.owners.head),
      (cfp4, event5, Seq(proposal3), parisJs.owners.head))

    val base = sponsorPack(humanTalks, "Base", 500, userDemo, "Description of the pack")
    val premium = sponsorPack(humanTalks, "Premium", 1500, userDemo)
    val old = sponsorPack(humanTalks, "Old", 100, userDemo, active = false)
    val packs = Seq(base, premium, old)

    val sponsor1 = sponsor(humanTalks, zeeneaHT, base, userDemo, "2018-01-01", "2019-01-01", Some(zeeneaHTJean))
    val sponsor2 = sponsor(humanTalks, zeeneaHT, premium, userDemo, "2019-01-01", "2020-01-01")
    val sponsor3 = sponsor(humanTalks, nexeo, base, userDemo, "2018-01-01", "2019-01-01")
    val sponsors = Seq(sponsor1, sponsor2, sponsor3)

    val groupMembers = Seq(
      member(humanTalks, userDemo))

    val eventRsvps = Seq(rsvp(event4, userDemo))

    val devoxx2020 = cfpExt("Devoxx France 2020", "https://cfp.devoxx.fr", Some("https://cfp.devoxx.fr/favicon.ico"), Some("2019-11-01"), Some("2020-01-16"), Some("2020-04-15"), Some("2020-04-17"), Some("https://devoxx.fr"), Some(palaisDesCongres), Some("https://www.devoxx.fr/tickets/"), Some("https://www.youtube.com/channel/UCsVPQfo5RZErDL41LoWvk0A"), Some("DevoxxFR"), Some("DevoxxFR"), Seq("Tech", "Java"), "Initialement très orienté Java, Devoxx France est maintenant une conférence généraliste", userDemo)
    val cfpExts = Seq(devoxx2020)

    val generated = (1 to 25).map { i =>
      val groupId = Group.Id.generate()
      val cfpId = Cfp.Id.generate()
      val g = Group(groupId, Group.Slug.from(s"z-group-$i").get, Group.Name(s"Z Group $i"), None, None, None, None, Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), None, NonEmptyList.of(userOrga.id), SocialAccounts.fromUrls(), Seq(), Group.Status.Active, Info(userOrga.id, now))
      val c = Cfp(cfpId, groupId, Cfp.Slug.from(s"z-cfp-$i").get, Cfp.Name(s"Z CFP $i"), None, None, Markdown("Only your best talks !"), Seq(), Info(userOrga.id, now))
      val e = Event(Event.Id.generate(), bigGroup.id, None, Event.Slug.from(s"z-event-$i").get, Event.Name(s"Z Event $i"), LocalDateTime.parse("2019-03-12T19:00:00"), Some(100), allowRsvp = false, MustacheMarkdownTmpl(""), Event.Notes("", now, userOrga.id), None, Seq(), Seq(), Some(now), Event.ExtRefs(), Info(userOrga.id, now))
      val t = Talk(Talk.Id.generate(), Talk.Slug.from(s"z-talk-$i").get, Talk.Status.Draft, Talk.Title(s"Z Talk $i"), Duration(10, MINUTES), Markdown("Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin."), NonEmptyList.of(userSpeaker.id), None, None, Seq(), Info(userSpeaker.id, now))
      val p = Proposal(Proposal.Id.generate(), talk7.id, cfpId, None, Proposal.Status.Pending, Talk.Title(s"Z Proposal $i"), Duration(10, MINUTES), Markdown("temporary description"), NonEmptyList.of(userSpeaker.id), None, None, Seq(), Seq(), Info(userSpeaker.id, now))
      val pa = Partner(Partner.Id.generate(), bigGroup.id, Partner.Slug.from(s"z-partner-$i").get, Partner.Name(s"Z Partner $i"), Markdown(""), None, Url.from(s"https://www.freelogodesign.org/Content/img/logo-ex-3.png").map(Logo).get, SocialAccounts.fromUrls(), Info(userOrga.id, now))
      (g, c, e, t, p, pa)
    }

    for {
      _ <- users.map(UserRepoSql.insert(_).run(xa)).sequence
      _ <- credentials.map(UserRepoSql.insertCredentials(_).run(xa)).sequence
      _ <- loginRefs.map(UserRepoSql.insertLoginRef(_).run(xa)).sequence
      _ <- (talks ++ generated.map(_._4)).map(TalkRepoSql.insert(_).run(xa)).sequence
      _ <- (groups ++ generated.map(_._1)).map(GroupRepoSql.insert(_).run(xa)).sequence
      _ <- (cfps ++ generated.map(_._2)).map(CfpRepoSql.insert(_).run(xa)).sequence
      _ <- (proposals ++ generated.map(_._5)).map(ProposalRepoSql.insert(_).run(xa)).sequence
      _ <- (partners ++ generated.map(_._6)).map(PartnerRepoSql.insert(_).run(xa)).sequence
      _ <- contacts.map(ContactRepoSql.insert(_).run(xa)).sequence
      _ <- venues.map(VenueRepoSql.insert(_).run(xa)).sequence
      _ <- packs.map(SponsorPackRepoSql.insert(_).run(xa)).sequence
      _ <- sponsors.map(SponsorRepoSql.insert(_).run(xa)).sequence
      _ <- (events ++ generated.map(_._3)).map(EventRepoSql.insert(_).run(xa)).sequence
      _ <- eventTalks.map { case (c, e, p, u) => addTalk(c, e, p, u, now) }.sequence
      _ <- groupSettings.set(humanTalksSettings)(new OrgaCtx(now, userDemo, humanTalks))
      _ <- groupMembers.map(GroupRepoSql.insertMember(_).run(xa)).sequence
      _ <- eventRsvps.map(EventRepoSql.insertRsvp(_).run(xa)).sequence
      _ <- cfpExts.map(ExternalCfpRepoSql.insert(_).run(xa)).sequence
    } yield Done
  }

  private def addTalk(cfp: Cfp, event: Event, proposals: Seq[Proposal], by: User.Id, now: Instant): IO[Done] = for {
    _ <- EventRepoSql.updateTalks(event.group, event.slug)(proposals.map(_.id), by, now).run(xa)
    _ <- proposals.map(p => ProposalRepoSql.updateStatus(cfp.slug, p.id)(Proposal.Status.Accepted, Some(event.id)).run(xa)).sequence
  } yield Done

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
}
