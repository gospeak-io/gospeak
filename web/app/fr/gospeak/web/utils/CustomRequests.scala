package fr.gospeak.web.utils

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import play.api.data.{Field, Form, FormError}
import play.api.i18n.Messages
import play.api.mvc._

import scala.util.matching.Regex

sealed trait GsRequest[A] extends WrappedRequest[A] {
  protected val request: Request[A]
  protected val messages: Messages
  val now: Instant

  def nowLDT: LocalDateTime = LocalDateTime.ofInstant(now, ZoneId.systemDefault) // Constants.defaultZoneId)

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


class BasicReq[A](protected val request: Request[A],
                  protected val messages: Messages,
                  val now: Instant) extends WrappedRequest[A](request) with GsRequest[A]

object BasicReq {
  def apply[A](req: Request[A], messages: Messages): BasicReq[A] =
    new BasicReq(req, messages, Instant.now())
}


class UserAwareReq[A](protected val request: Request[A],
                      protected val messages: Messages,
                      val underlying: UserAwareRequest[CookieEnv, A],
                      val now: Instant,
                      val user: Option[User]) extends WrappedRequest[A](request) with GsRequest[A] {
  def basic: BasicReq[A] = new BasicReq(request, messages, now)

  def secured: Option[SecuredReq[A]] = for {
    identity <- underlying.identity
    authenticator <- underlying.authenticator
  } yield new SecuredReq(request, messages, SecuredRequest(identity, authenticator, request), now, identity.user, identity.groups)

  def secured(i: AuthUser, authenticator: CookieAuthenticator): SecuredReq[A] =
    new SecuredReq(request, messages, SecuredRequest(underlying.identity.getOrElse(i), underlying.authenticator.getOrElse(authenticator), request), now, user.getOrElse(i.user), i.groups)
}

object UserAwareReq {
  def apply[A](req: UserAwareRequest[CookieEnv, A], messages: Messages): UserAwareReq[A] =
    new UserAwareReq(req.request, messages, req, Instant.now(), req.identity.map(_.user))
}


class SecuredReq[A](protected val request: Request[A],
                    protected val messages: Messages,
                    val underlying: SecuredRequest[CookieEnv, A],
                    val now: Instant,
                    val user: User,
                    val groups: Seq[Group]) extends WrappedRequest[A](request) with GsRequest[A] {
  def basic: BasicReq[A] = new BasicReq(request, messages, now)

  def userAware: UserAwareReq[A] = new UserAwareReq(request, messages, UserAwareRequest(Some(underlying.identity), Some(underlying.authenticator), request), now, Some(user))
}

object SecuredReq {
  def apply[A](req: SecuredRequest[CookieEnv, A], messages: Messages): SecuredReq[A] =
    new SecuredReq(req.request, messages, req, Instant.now(), req.identity.user, req.identity.groups)
}
