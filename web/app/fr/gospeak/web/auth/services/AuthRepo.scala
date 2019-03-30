package fr.gospeak.web.auth.services

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.User._
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.Done
import fr.gospeak.web.auth.domain.AuthUser

import scala.concurrent.Future

class AuthRepo(db: GospeakDb) extends DelegableAuthInfoDAO[PasswordInfo] with IdentityService[AuthUser] {
  override def retrieve(loginInfo: LoginInfo): Future[Option[AuthUser]] =
    db.getUser(toDomain(loginInfo)).map(_.map(u => AuthUser(loginInfo, u))).unsafeToFuture()

  override def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] =
    db.getCredentials(toDomain(loginInfo)).map(_.map(toSilhouette)).unsafeToFuture()

  override def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    db.createCredentials(toDomain(loginInfo, authInfo)).map(toSilhouette).unsafeToFuture()

  override def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    db.updateCredentials(toDomain(loginInfo))(toDomain(authInfo)).map(_ => authInfo).unsafeToFuture()

  // add or update
  override def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    db.getCredentials(toDomain(loginInfo)).flatMap { opt =>
      opt.map(_ => db.updateCredentials(toDomain(loginInfo))(toDomain(authInfo)))
        .getOrElse(db.createCredentials(toDomain(loginInfo, authInfo)).map(_ => Done))
    }.map(_ => authInfo).unsafeToFuture()

  override def remove(loginInfo: LoginInfo): Future[Unit] =
    db.deleteCredentials(toDomain(loginInfo)).map(_ => ()).unsafeToFuture()

  private def toDomain(loginInfo: LoginInfo): User.Login = User.Login(ProviderId(loginInfo.providerID), ProviderKey(loginInfo.providerKey))

  private def toDomain(authInfo: PasswordInfo): User.Password = User.Password(Hasher(authInfo.hasher), PasswordValue(authInfo.password), authInfo.salt.map(Salt))

  private def toDomain(loginInfo: LoginInfo, authInfo: PasswordInfo): User.Credentials = User.Credentials(toDomain(loginInfo), toDomain(authInfo))

  private def toSilhouette(p: User.Password): PasswordInfo = PasswordInfo(p.hasher.value, p.password.value, p.salt.map(_.value))

  private def toSilhouette(c: User.Credentials): PasswordInfo = toSilhouette(c.pass)
}
