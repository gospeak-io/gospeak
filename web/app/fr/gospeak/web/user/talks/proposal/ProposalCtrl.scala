package fr.gospeak.web.user.talks.proposal

import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.talks.TalkCtrl
import fr.gospeak.web.user.talks.proposal.ProposalCtrl._
import fr.gospeak.web.views.domain.Breadcrumb
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def detail(talk: String, proposal: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.detail()(TalkCtrl.header(talk), breadcrumb(UserCtrl.user, talk -> TalkCtrl.talkName, proposal -> proposalName)))
  }
}

object ProposalCtrl {
  val proposalName = "HumanTalks Paris"

  def breadcrumb(user: String, talk: (String, String), proposal: (String, String)): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add(proposal._2 -> routes.ProposalCtrl.detail(talk._1, proposal._1))
}
