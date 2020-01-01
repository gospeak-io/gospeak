package fr.gospeak.web.utils

import java.time.{Instant, LocalDate, LocalDateTime}

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import fr.gospeak.core.domain.utils._
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.libs.scalautils.domain.EmailAddress
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import play.api.data.{Field, Form, FormError}
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.JsonValidationError
import play.api.mvc._

import scala.collection.Seq
import scala.util.matching.Regex

sealed class BasicReq[A] protected(protected val request: Request[A],
                                   protected val messages: Messages,
                                   val now: Instant,
                                   val conf: AppConf) extends WrappedRequest[A](request) with BasicCtx {
  override def withBody[B](body: B): BasicReq[B] = new BasicReq(request.withBody(body), messages, now, conf)

  def nowLDT: LocalDateTime = LocalDateTime.ofInstant(now, Constants.defaultZoneId)

  def nowLD: LocalDate = nowLDT.toLocalDate

  def format(key: String, args: Seq[Any]): String = translate(key, args)

  def format(t: (String, Seq[Any])): String = translate(t._1, t._2)

  def format(e: FormError): String = translate(e.message, e.args)

  def format(e: JsonValidationError): String = translate(e.message, e.args)

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
  def from[A](conf: AppConf, messagesApi: MessagesApi, r: Request[A]): BasicReq[A] =
    new BasicReq[A](r, messagesApi.preferred(r), Instant.now(), conf)
}


final class UserAwareReq[A] protected(override val request: Request[A],
                                      override val messages: Messages,
                                      override val now: Instant,
                                      override val conf: AppConf,
                                      val underlying: UserAwareRequest[CookieEnv, A],
                                      val user: Option[User],
                                      val groups: Option[Seq[Group]]) extends BasicReq[A](request, messages, now, conf) with UserAwareCtx {
  override def withBody[B](body: B): UserAwareReq[B] = new UserAwareReq(
    request = request.withBody(body),
    messages = messages,
    now = now,
    conf = conf,
    underlying = UserAwareRequest(underlying.identity, underlying.authenticator, underlying.request.withBody(body)),
    user = user,
    groups = groups)

  def secured: Option[UserReq[A]] = for {
    identity <- underlying.identity
    authenticator <- underlying.authenticator
  } yield UserReq.from(this, identity, authenticator)

  def secured(identity: AuthUser, authenticator: CookieAuthenticator): UserReq[A] = UserReq.from(this, identity, authenticator)
}

object UserAwareReq {
  def from[A](conf: AppConf, messagesApi: MessagesApi, r: UserAwareRequest[CookieEnv, A]): UserAwareReq[A] =
    new UserAwareReq[A](
      request = r.request,
      messages = messagesApi.preferred(r.request),
      now = Instant.now(),
      conf = conf,
      underlying = r,
      user = r.identity.map(_.user),
      groups = r.identity.map(_.groups))

  def from[A](r: UserReq[A]): UserAwareReq[A] =
    new UserAwareReq[A](r.request, r.messages, r.now, r.conf, UserAwareRequest(Some(r.underlying.identity), Some(r.underlying.authenticator), r.request), Some(r.user), Some(r.groups))
}


sealed class UserReq[A] protected(override val request: Request[A],
                                  override val messages: Messages,
                                  override val now: Instant,
                                  override val conf: AppConf,
                                  val underlying: SecuredRequest[CookieEnv, A],
                                  val user: User,
                                  val groups: Seq[Group]) extends BasicReq[A](request, messages, now, conf) with UserCtx {
  override def withBody[B](body: B): UserReq[B] = new UserReq(
    request = request.withBody(body),
    messages = messages,
    now = now,
    conf = conf,
    underlying = SecuredRequest(underlying.identity, underlying.authenticator, underlying.request.withBody(body)),
    user = user,
    groups = groups)

  def userAware: UserAwareReq[A] = UserAwareReq.from(this)

  def orga(group: Group): OrgaReq[A] = OrgaReq.from(this, group)
}

object UserReq {
  def from[A](conf: AppConf, messagesApi: MessagesApi, r: SecuredRequest[CookieEnv, A]): UserReq[A] =
    new UserReq[A](
      request = r.request,
      messages = messagesApi.preferred(r.request),
      now = Instant.now(),
      conf = conf,
      underlying = r,
      user = r.identity.user,
      groups = r.identity.groups)

  def from[A](r: UserAwareReq[A], i: AuthUser, authenticator: CookieAuthenticator): UserReq[A] =
    new UserReq(r.request, r.messages, r.now, r.conf, SecuredRequest(r.underlying.identity.getOrElse(i), r.underlying.authenticator.getOrElse(authenticator), r.request), r.user.getOrElse(i.user), i.groups)
}


final class OrgaReq[A] protected(override val request: Request[A],
                                 override val messages: Messages,
                                 override val now: Instant,
                                 override val conf: AppConf,
                                 override val underlying: SecuredRequest[CookieEnv, A],
                                 override val user: User,
                                 override val groups: Seq[Group],
                                 val group: Group) extends UserReq[A](request, messages, now, conf, underlying, user, groups) with OrgaCtx {
  override def withBody[B](body: B): OrgaReq[B] = new OrgaReq(
    request = request.withBody(body),
    messages = messages,
    now = now,
    conf = conf,
    underlying = SecuredRequest(underlying.identity, underlying.authenticator, underlying.request.withBody(body)),
    user = user,
    groups = groups,
    group = group)

  def senders: Seq[EmailAddress.Contact] = group.senders(user)
}

object OrgaReq {
  def from[A](r: UserReq[A], group: Group): OrgaReq[A] =
    new OrgaReq[A](r.request, r.messages, r.now, r.conf, r.underlying, r.user, r.groups, group)
}
