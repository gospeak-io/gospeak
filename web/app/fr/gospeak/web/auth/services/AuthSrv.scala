package fr.gospeak.web.auth.services

import java.time.Instant

import cats.effect.IO
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.{PasswordHasherRegistry, PasswordInfo}
import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.User._
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.auth.AuthForms.SignupData
import fr.gospeak.web.auth.domain.AuthUser
import fr.gospeak.web.auth.exceptions.{DuplicateIdentityException, DuplicateSlugException}

class AuthSrv(authRepo: AuthRepo,
              db: GospeakDb,
              passwordHasherRegistry: PasswordHasherRegistry) {
  def createIdentity(loginInfo: LoginInfo, data: SignupData, now: Instant): IO[AuthUser] = {
    val login = toDomain(loginInfo)
    val password = toDomain(passwordHasherRegistry.current.hash(data.password.decode))
    val credentials = User.Credentials(login, password)
    for {
      loginOpt <- db.getUser(login)
      _ <- loginOpt.swap.toIO(DuplicateIdentityException(loginInfo)) // fail if login already exist
      emailOpt <- db.getUser(data.email)
      slugOpt <- db.getUser(data.slug)
      _ <- slugOpt.forall(s => emailOpt.exists(_.id == s.id)).toIO(DuplicateSlugException(data.slug)) // fail if slug exists for a different user from email
      user <- emailOpt.map { user =>
        db.updateUser(user.copy(slug = data.slug, firstName = data.firstName, lastName = data.lastName, email = data.email), now)
      }.getOrElse {
        db.createUser(data.slug, data.firstName, data.lastName, data.email, now)
      }
      _ <- db.createLoginRef(login, user.id)
      _ <- db.createCredentials(credentials)
    } yield AuthUser(loginInfo, user)
  }

  private def toDomain(loginInfo: LoginInfo): User.Login = User.Login(ProviderId(loginInfo.providerID), ProviderKey(loginInfo.providerKey))

  private def toDomain(authInfo: PasswordInfo): User.Password = User.Password(Hasher(authInfo.hasher), PasswordValue(authInfo.password), authInfo.salt.map(Salt))
}
