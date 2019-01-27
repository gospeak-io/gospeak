package fr.gospeak.web.testingutils

import fr.gospeak.core.domain.User
import org.scalatest.{FunSpec, Matchers}
import play.api.i18n.{Lang, Messages}
import play.api.mvc.{AnyContentAsEmpty, Flash}
import play.api.test.FakeRequest

trait TwirlSpec extends FunSpec with Matchers {
  protected implicit val user: Option[User] = None
  protected implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  protected implicit val flash: Flash = req.flash
  implicit val messages: Messages = new Messages {
    override def lang: Lang = ???

    override def apply(key: String, args: Any*): String = key

    override def apply(keys: Seq[String], args: Any*): String = ???

    override def translate(key: String, args: Seq[Any]): Option[String] = ???

    override def isDefinedAt(key: String): Boolean = ???
  }
}
