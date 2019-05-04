package fr.gospeak.core.services

import cats.effect.IO
import fr.gospeak.core.domain.GospeakMessage
import fr.gospeak.core.domain.GospeakMessage.ProposalMessage.ProposalCreated

class MessageHandler() {
  def handle(msg: GospeakMessage): IO[Unit] = msg match {
    case m: ProposalCreated => handleCreateProposal(m)
    case _ => IO.pure(println(s"handle($msg)"))
  }

  private def handleCreateProposal(msg: ProposalCreated): IO[Unit] = {
    IO.pure(println(s"handleCreateProposal($msg)"))
  }
}
