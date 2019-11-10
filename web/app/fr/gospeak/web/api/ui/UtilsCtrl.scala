package fr.gospeak.web.api.ui

import cats.effect.IO
import fr.gospeak.core.ApplicationConf
import fr.gospeak.infra.services.EmbedSrv
import fr.gospeak.libs.scalautils.domain.{Markdown, Url}
import fr.gospeak.web.utils.{ApiCtrl, MarkdownUtils}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class UtilsCtrl(cc: ControllerComponents,
                env: ApplicationConf.Env) extends ApiCtrl(cc, env) {
  def markdownToHtml(): Action[String] = ActionIO(parse.text) { implicit req =>
    val md = Markdown(req.body)
    val html = MarkdownUtils.render(md)
    IO.pure(Ok(html.value))
  }

  def embed(url: Url): Action[AnyContent] = ActionIO { implicit req =>
    EmbedSrv.embedCode(url)
      .map { code => Ok(code.value) }
  }
}
