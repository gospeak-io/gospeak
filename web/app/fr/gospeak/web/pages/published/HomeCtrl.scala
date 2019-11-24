package fr.gospeak.web.pages.published

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{Info, SocialAccounts}
import fr.gospeak.infra.services.GravatarSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain._
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.HomeCtrl._
import fr.gospeak.web.utils.{OrgaReq, UICtrl, UserReq}
import org.joda.time.DateTime
import play.api.mvc._

import scala.concurrent.duration._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               env: ApplicationConf.Env) extends UICtrl(cc, silhouette, env) {
  def index(): Action[AnyContent] = UserAwareActionIO { implicit req =>
    val b = breadcrumb()
    IO.pure(Ok(html.index()(b)))
  }

  def why(): Action[AnyContent] = UserAwareActionIO { implicit req =>
    val b = breadcrumb().add("Why use Gospeak" -> routes.HomeCtrl.why())
    IO.pure(Ok(html.why()(b)))
  }

  private val now = Instant.now()
  private val dt = new DateTime()
  private val ldt = LocalDateTime.now()
  private val email = EmailAddress.from("john.doe@mail.com").get
  private val user = User(
    id = User.Id.generate(),
    slug = User.Slug.from("john-doe").get,
    status = User.Status.Public,
    firstName = "John",
    lastName = "Doe",
    email = email,
    emailValidated = None,
    avatar = GravatarSrv.getAvatar(email),
    bio = None,
    company = None,
    location = None,
    phone = None,
    website = None,
    social = SocialAccounts.fromUrls(),
    createdAt = now,
    updatedAt = now)
  private val identity = AuthUser(
    loginInfo = LoginInfo(providerID = "credentials", providerKey = email.value),
    user = user,
    groups = Seq())
  private val authenticator = CookieAuthenticator("cookie", identity.loginInfo, dt, dt.plusMinutes(1), None, None, None)
  private val group = Group(
    id = Group.Id.generate(),
    slug = Group.Slug.from("group-slug").get,
    name = Group.Name("A group"),
    logo = None,
    banner = None,
    contact = Some(EmailAddress.from("contact@gospeak.fr").get),
    website = None,
    description = Markdown(
      """This is an **awesome** group, you should come and see us.
        |
        |We do:
        |- beer
        |- pizzas ^^
      """.stripMargin),
    location = None,
    owners = NonEmptyList.of(user.id),
    social = SocialAccounts.fromUrls(),
    tags = Seq("tag").map(Tag(_)),
    status = Group.Status.Active,
    info = Info(user.id, now))
  private val cfp = Cfp(
    id = Cfp.Id.generate(),
    group = group.id,
    slug = Cfp.Slug.from("cfp-slug").get,
    name = Cfp.Name("CFP 2019!!!"),
    begin = None,
    close = None,
    description = Markdown(
      """Submit your best talk to amaze our attendees ;)
        |
        |We choose talks every week so don't wait
      """.stripMargin),
    Seq("Scala", "UX").map(Tag(_)),
    info = Info(user.id, now))
  private val event = Event(
    id = Event.Id.generate(),
    group = group.id,
    cfp = Some(cfp.id),
    slug = Event.Slug.from("event-slug").get,
    name = Event.Name("Best Event in April \\o/"),
    start = ldt,
    maxAttendee = Some(100),
    allowRsvp = false,
    description = MustacheMarkdownTmpl(""),
    orgaNotes = Event.Notes("", now, user.id),
    venue = None,
    talks = Seq(),
    tags = Seq("tag").map(Tag(_)),
    published = Some(now),
    refs = Event.ExtRefs(),
    info = Info(user.id, now))
  private val talk = Talk(
    id = Talk.Id.generate(),
    slug = Talk.Slug.from("talk-slug").get,
    title = Talk.Title("FP for the win!"),
    duration = 10.minutes,
    status = Talk.Status.Public,
    description = Markdown(
      """Have you heard about FP?
        |
        |It's the next/actual big thing in tech :D
      """.stripMargin),
    speakers = NonEmptyList.of(user.id),
    slides = None,
    video = None,
    tags = Seq("tag").map(Tag(_)),
    info = Info(user.id, now))
  private val proposal = Proposal(
    id = Proposal.Id.generate(),
    talk = talk.id,
    cfp = cfp.id,
    event = None,
    title = talk.title,
    duration = talk.duration,
    status = Proposal.Status.Pending,
    description = talk.description,
    speakers = talk.speakers,
    slides = talk.slides,
    video = talk.video,
    tags = Seq("tag").map(Tag(_)),
    info = Info(user.id, now))

  def styleguide(params: Page.Params): Action[AnyContent] = UserAwareActionIO { implicit req =>
    implicit val userReq: UserReq[AnyContent] = req.secured(identity, authenticator)
    implicit val orgaReq: OrgaReq[AnyContent] = userReq.orga(group)
    val proposalFull = Proposal.Full(proposal, cfp, group, talk, Some(event), 0L, 0L, 0L)
    IO.pure(Ok(html.styleguide(user, group, cfp, event, talk, proposal, proposalFull, params)))
  }
}

object HomeCtrl {
  def breadcrumb(): Breadcrumb =
    Breadcrumb("Home" -> routes.HomeCtrl.index())
}
