package fr.gospeak.web.testingutils

import java.util.Locale

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import fr.gospeak.core.domain.User
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.routes
import org.scalatest.{FunSpec, Matchers}
import play.api.i18n.{Lang, Messages}
import play.api.mvc.{AnyContentAsEmpty, Flash}
import play.api.test.FakeRequest

trait TwirlSpec extends FunSpec with Matchers with RandomDataGenerator {
  protected implicit val user: User = random[User]
  protected implicit val userOpt: Option[User] = Some(user)
  protected implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  protected implicit val flash: Flash = req.flash
  implicit val messages: Messages = new Messages {
    override def lang: Lang = Lang(Locale.ENGLISH)

    override def apply(key: String, args: Any*): String = key

    override def apply(keys: Seq[String], args: Any*): String = keys.headOption.getOrElse("")

    override def translate(key: String, args: Seq[Any]): Option[String] = Some(key)

    override def isDefinedAt(key: String): Boolean = false
  }
  protected val h = HeaderInfo(NavLink("Gospeak", routes.HomeCtrl.index()), Seq(), Seq())
  protected val b = Breadcrumb(Seq())
}
