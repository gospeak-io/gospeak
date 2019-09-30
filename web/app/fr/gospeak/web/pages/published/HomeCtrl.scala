package fr.gospeak.web.pages.published

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.infra.services.GravatarSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain._
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.HomeCtrl._
import fr.gospeak.web.utils.UICtrl
import org.joda.time.DateTime
import play.api.mvc._

import scala.concurrent.duration._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv]) extends UICtrl(cc, silhouette) {

  import silhouette._

  def index(): Action[AnyContent] = UserAwareAction { implicit req =>
    val b = breadcrumb()
    Ok(html.index()(b))
  }

  private val now = Instant.now()
  private val dt = new DateTime()
  private val ldt = LocalDateTime.now()
  private val email = EmailAddress.from("john.doe@mail.com").get
  private val user = User(
    id = User.Id.generate(),
    slug = User.Slug.from("john-doe").get,
    firstName = "John",
    lastName = "Doe",
    email = email,
    emailValidated = None,
    avatar = GravatarSrv.getAvatar(email),
    profile = User.emptyProfile,
    created = now,
    updated = now)
  private val identity = AuthUser(
    loginInfo = LoginInfo(providerID = "credentials", providerKey = email.value),
    user = user,
    groups = Seq())
  private val authenticator = CookieAuthenticator("cookie", identity.loginInfo, dt, dt.plusMinutes(1), None, None, None)
  private val group = Group(
    id = Group.Id.generate(),
    slug = Group.Slug.from("group-slug").get,
    name = Group.Name("A group"),
    contact = Some(EmailAddress.from("contact@gospeak.fr").get),
    description = Markdown(
      """This is an **awesome** group, you should come and see us.
        |
        |We do:
        |- beer
        |- pizzas ^^
      """.stripMargin),
    owners = NonEmptyList.of(user.id),
    tags = Seq("tag").map(Tag(_)),
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
    description = MustacheMarkdownTmpl(""),
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

  def styleguide(params: Page.Params): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    implicit val secured = SecuredRequest[CookieEnv, AnyContent](identity, authenticator, req)
    implicit val userAware = UserAwareRequest[CookieEnv, AnyContent](Some(identity), Some(authenticator), req)
    implicit val messages = req.messages
    val proposalFull = Proposal.Full(proposal, cfp, group, talk, Some(event))
    Ok(html.styleguide(user, group, cfp, event, talk, proposal, proposalFull, Instant.now(), params))
  }
}

object HomeCtrl {
  def breadcrumb(): Breadcrumb =
    Breadcrumb("Home" -> routes.HomeCtrl.index())
}
