package fr.gospeak.web.utils

import java.time.{Instant, LocalDate, LocalDateTime}

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.Constants
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.libs.scalautils.domain.EmailAddress
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import play.api.data.{Field, Form, FormError}
import play.api.i18n.Messages
import play.api.mvc._

import scala.util.matching.Regex

sealed class BasicReq[A](protected val request: Request[A],
                         protected val messages: Messages,
                         val now: Instant,
                         val env: ApplicationConf.Env) extends WrappedRequest[A](request) {
  def nowLDT: LocalDateTime = LocalDateTime.ofInstant(now, Constants.defaultZoneId)

  def nowLD: LocalDate = nowLDT.toLocalDate

  def format(key: String, args: Seq[Any]): String = translate(key, args)

  def format(t: (String, Seq[Any])): String = translate(t._1, t._2)

  def format(e: FormError): String = translate(e.message, e.args)

  def formatErrors(f: Field): String = format(f.errors)

  def formatErrors(f: Form[_]): String = format(f.errors)

  def format(errors: Seq[FormError]): String = errors.map(format).distinct.mkString(", ")

  private def translate(key: String, args: Seq[Any]): String = messages.translate(key, args.map(formatArg)) match {
    case Some(text) => text
    case None => key // TODO add translation error handling
  }

  private def formatArg(arg: Any): String = arg match {
    case r: Regex => r.toString
    case f: Function0[_] => f().toString
    case arg => arg.toString
  }
}

object BasicReq {
  def apply[A](req: Request[A], messages: Messages, env: ApplicationConf.Env): BasicReq[A] =
    new BasicReq(req, messages, Instant.now(), env)
}


class UserAwareReq[A](override protected val request: Request[A],
                      override protected val messages: Messages,
                      override val now: Instant,
                      override val env: ApplicationConf.Env,
                      val underlying: UserAwareRequest[CookieEnv, A],
                      val user: Option[User]) extends BasicReq[A](request, messages, now, env) {
  def basic: BasicReq[A] = this

  def secured: Option[UserReq[A]] = for {
    identity <- underlying.identity
    authenticator <- underlying.authenticator
  } yield new UserReq(request, messages, now, env, SecuredRequest(identity, authenticator, request), identity.user, identity.groups)

  def secured(i: AuthUser, authenticator: CookieAuthenticator): UserReq[A] =
    new UserReq(request, messages, now, env, SecuredRequest(underlying.identity.getOrElse(i), underlying.authenticator.getOrElse(authenticator), request), user.getOrElse(i.user), i.groups)
}

object UserAwareReq {
  def apply[A](req: UserAwareRequest[CookieEnv, A], messages: Messages, env: ApplicationConf.Env): UserAwareReq[A] =
    new UserAwareReq(req.request, messages, Instant.now(), env, req, req.identity.map(_.user))
}


class UserReq[A](override protected val request: Request[A],
                 override protected val messages: Messages,
                 override val now: Instant,
                 override val env: ApplicationConf.Env,
                 val underlying: SecuredRequest[CookieEnv, A],
                 val user: User,
                 val groups: Seq[Group]) extends BasicReq[A](request, messages, now, env) {
  def basic: BasicReq[A] = this

  def userAware: UserAwareReq[A] = new UserAwareReq(request, messages, now, env, UserAwareRequest(Some(underlying.identity), Some(underlying.authenticator), request), Some(user))

  def orga(group: Group): OrgaReq[A] = new OrgaReq[A](request, messages, now, env, underlying, user, groups, group)
}

object UserReq {
  def apply[A](req: SecuredRequest[CookieEnv, A], messages: Messages, env: ApplicationConf.Env): UserReq[A] =
    new UserReq(req.request, messages, Instant.now(), env, req, req.identity.user, req.identity.groups)
}


class OrgaReq[A](override protected val request: Request[A],
                 override protected val messages: Messages,
                 override val now: Instant,
                 override val env: ApplicationConf.Env,
                 override val underlying: SecuredRequest[CookieEnv, A],
                 override val user: User,
                 override val groups: Seq[Group],
                 val group: Group) extends UserReq[A](request, messages, now, env, underlying, user, groups) {
  def senders: Seq[EmailAddress.Contact] = group.senders(user)
}
