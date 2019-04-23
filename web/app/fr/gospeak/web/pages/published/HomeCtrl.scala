package fr.gospeak.web.pages.published

import java.time.Instant

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import fr.gospeak.core.domain.User
import fr.gospeak.infra.services.GravatarSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Page}
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.utils.UICtrl
import org.joda.time.DateTime
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv]) extends UICtrl(cc, silhouette) {

  import silhouette._

  def index(): Action[AnyContent] = UserAwareAction { implicit req =>
    Ok(html.index())
  }

  def styleguide(params: Page.Params): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    val dt = new DateTime()
    val now = Instant.now()
    val email = EmailAddress.from("john.doe@mail.com").get
    val user = User(
      id = User.Id.generate(),
      slug = User.Slug.from("john-doe").get,
      firstName = "John",
      lastName = "Doe",
      email = email,
      emailValidated = None,
      avatar = GravatarSrv.getAvatar(email),
      public = true,
      created = now,
      updated = now)
    val identity = AuthUser(
      loginInfo = LoginInfo(providerID = "credentials", providerKey = email.value),
      user = user,
      groups = Seq())
    val authenticator = CookieAuthenticator("cookie", identity.loginInfo, dt, dt.plusMinutes(1), None, None, None)
    implicit val secured = SecuredRequest[CookieEnv, AnyContent](identity, authenticator, req)
    implicit val userAware = UserAwareRequest[CookieEnv, AnyContent](Some(identity), Some(authenticator), req)
    implicit val messages = req.messages
    Ok(html.styleguide(params))
  }
}
