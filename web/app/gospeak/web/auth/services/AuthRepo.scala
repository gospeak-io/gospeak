package fr.gospeak.web.auth.services

import cats.data.OptionT
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import gospeak.core.domain.User
import gospeak.core.domain.User._
import gospeak.core.services.storage.{AuthGroupRepo, AuthUserRepo}
import fr.gospeak.web.auth.domain.AuthUser
import gospeak.libs.scala.domain.Done

import scala.concurrent.Future
import scala.reflect.ClassTag

// TODO merge it with AuthSrv
class AuthRepo(userRepo: AuthUserRepo, groupRepo: AuthGroupRepo) extends DelegableAuthInfoDAO[PasswordInfo] with IdentityService[AuthUser] {
  override val classTag: ClassTag[PasswordInfo] = scala.reflect.classTag[PasswordInfo]

  override def retrieve(loginInfo: LoginInfo): Future[Option[AuthUser]] = (for {
    user <- OptionT(userRepo.find(toDomain(loginInfo)))
    groups <- OptionT.liftF(groupRepo.list(user.id))
  } yield AuthUser(loginInfo, user, groups)).value.unsafeToFuture()

  override def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] =
    userRepo.findCredentials(toDomain(loginInfo)).map(_.map(toSilhouette)).unsafeToFuture()

  override def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    userRepo.createCredentials(toDomain(loginInfo, authInfo)).map(toSilhouette).unsafeToFuture()

  override def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    userRepo.editCredentials(toDomain(loginInfo))(toDomain(authInfo)).map(_ => authInfo).unsafeToFuture()

  // add or update
  override def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    userRepo.findCredentials(toDomain(loginInfo)).flatMap { opt =>
      opt.map(_ => userRepo.editCredentials(toDomain(loginInfo))(toDomain(authInfo)))
        .getOrElse(userRepo.createCredentials(toDomain(loginInfo, authInfo)).map(_ => Done))
    }.map(_ => authInfo).unsafeToFuture()

  override def remove(loginInfo: LoginInfo): Future[Unit] =
    userRepo.removeCredentials(toDomain(loginInfo)).map(_ => ()).unsafeToFuture()

  private def toDomain(loginInfo: LoginInfo): User.Login = User.Login(ProviderId(loginInfo.providerID), ProviderKey(loginInfo.providerKey))

  private def toDomain(authInfo: PasswordInfo): User.Password = User.Password(Hasher(authInfo.hasher), PasswordValue(authInfo.password), authInfo.salt.map(Salt))

  private def toDomain(loginInfo: LoginInfo, authInfo: PasswordInfo): User.Credentials = User.Credentials(toDomain(loginInfo), toDomain(authInfo))

  private def toSilhouette(p: User.Password): PasswordInfo = PasswordInfo(p.hasher.value, p.password.value, p.salt.map(_.value))

  private def toSilhouette(c: User.Credentials): PasswordInfo = toSilhouette(c.pass)
}
