package gospeak.core.domain.messages

import cats.data.NonEmptyList
import gospeak.core.domain.{Proposal, Talk, User}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Markdown, Slides, Tag, Video}

import scala.concurrent.duration._

final case class MsgProposal(id: Proposal.Id,
                             title: Talk.Title,
                             duration: FiniteDuration,
                             description: Markdown,
                             speakers: NonEmptyList[MsgUser.Embed],
                             slides: Option[Slides],
                             video: Option[Video],
                             tags: Seq[Tag],
                             publicLink: String,
                             orgaLink: String)

object MsgProposal {

  final case class Embed(id: Proposal.Id,
                         title: Talk.Title,
                         duration: FiniteDuration,
                         description: Markdown,
                         speakers: NonEmptyList[MsgUser.Embed],
                         slides: Option[Slides],
                         video: Option[Video],
                         tags: Seq[Tag],
                         publicLink: String,
                         orgaLink: String)

  object Embed {
    def unknown(id: Proposal.Id): Embed = Embed(
      id = id,
      title = Talk.Title("Unknown talk"),
      duration = 0.minute,
      description = Markdown(""),
      speakers = NonEmptyList.of(MsgUser.Embed.unknown(User.Id.from("00000000-0000-0000-0000-000000000000").get)),
      slides = None,
      video = None,
      tags = Seq(),
      publicLink = "",
      orgaLink = "")
  }

}
