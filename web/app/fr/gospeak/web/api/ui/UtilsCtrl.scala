package fr.gospeak.web.api.ui

import fr.gospeak.libs.scalautils.domain.Markdown
import fr.gospeak.web.utils.{ApiCtrl, MarkdownUtils}
import play.api.mvc.{Action, ControllerComponents, Request}

class UtilsCtrl(cc: ControllerComponents) extends ApiCtrl(cc) {
  def markdownToHtml(): Action[String] = Action(parse.text) { implicit req: Request[String] =>
    val md = Markdown(req.body)
    val html = MarkdownUtils.render(md)
    Ok(html.value)
  }
}
