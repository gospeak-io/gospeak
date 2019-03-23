package fr.gospeak.web.auth.services

import java.time.Instant

import cats.effect.IO
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.User._
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.{Done, Email}
import fr.gospeak.web.auth.domain.AuthUser
import play.api.mvc.{AnyContent, Request}

import scala.concurrent.Future

class AuthRepo(db: GospeakDb) extends DelegableAuthInfoDAO[PasswordInfo] with IdentityService[AuthUser] {
  private var logged: Option[User] = None

  override def retrieve(loginInfo: LoginInfo): Future[Option[AuthUser]] = {
    println(s"AuthRepo.retrieve($loginInfo)")
    db.getUser(toDomain(loginInfo))
      .map(_.map(u => AuthUser(loginInfo, u))).unsafeToFuture()
  }

  def createUser(loginInfo: LoginInfo, slug: User.Slug, firstName: String, lastName: String, email: Email, now: Instant): Future[AuthUser] = {
    println(s"AuthRepo.createUser($loginInfo, $slug, $firstName, $lastName, $email, $now)")
    (for {
      user <- db.createUser(slug, firstName, lastName, email, now)
      _ <- db.createLoginRef(toDomain(loginInfo), user.id)
    } yield AuthUser(loginInfo, user)).unsafeToFuture()
  }

  override def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] = {
    println(s"AuthRepo.find($loginInfo)")
    db.getCredentials(toDomain(loginInfo))
      .map(_.map(toSilhouette)).unsafeToFuture()
  }

  override def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = {
    println(s"AuthRepo.add($loginInfo, $authInfo)")
    db.createCredentials(toDomain(loginInfo, authInfo))
      .map(toSilhouette).unsafeToFuture()
  }

  override def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = {
    println(s"AuthRepo.update($loginInfo, $authInfo)")
    db.updateCredentials(toDomain(loginInfo))(toDomain(authInfo))
      .map(_ => authInfo).unsafeToFuture()
  }

  // add or update
  override def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = {
    println(s"AuthRepo.save($loginInfo, $authInfo)")
    db.getCredentials(toDomain(loginInfo)).flatMap { opt =>
      opt.map(_ => db.updateCredentials(toDomain(loginInfo))(toDomain(authInfo)))
        .getOrElse(db.createCredentials(toDomain(loginInfo, authInfo)).map(_ => Done))
    }.map(_ => authInfo).unsafeToFuture()
  }

  override def remove(loginInfo: LoginInfo): Future[Unit] = {
    println(s"AuthRepo.remove($loginInfo)")
    db.deleteCredentials(toDomain(loginInfo))
      .map(_ => ()).unsafeToFuture()
  }

  private def toDomain(loginInfo: LoginInfo): User.Login = User.Login(ProviderId(loginInfo.providerID), ProviderKey(loginInfo.providerKey))

  private def toDomain(authInfo: PasswordInfo): User.Password = User.Password(Hasher(authInfo.hasher), PasswordValue(authInfo.password), authInfo.salt.map(Salt))

  private def toDomain(loginInfo: LoginInfo, authInfo: PasswordInfo): User.Credentials = User.Credentials(toDomain(loginInfo), toDomain(authInfo))

  private def toSilhouette(p: User.Password): PasswordInfo = PasswordInfo(p.hasher.value, p.password.value, p.salt.map(_.value))

  private def toSilhouette(c: User.Credentials): PasswordInfo = toSilhouette(c.pass)

  // OLD

  def login(user: User): IO[Done] = {
    logged = Some(user)
    IO.pure(Done)
  }

  def logout(): IO[Done] = {
    logged = None
    IO.pure(Done)
  }

  def userAware()(implicit req: Request[AnyContent]): Option[User] = {
    val user = req.queryString.get("user")
      .flatMap(_.headOption)
      .map { u => if (u.contains("@")) u else u + "@mail.com" }
      .map(Email.from(_).right.get)
      .flatMap(db.getUser(_).unsafeRunSync())
    user.foreach(login(_).unsafeRunSync())
    user.orElse(logged)
  }

  def authed()(implicit req: Request[AnyContent]): User =
    userAware().get
}
