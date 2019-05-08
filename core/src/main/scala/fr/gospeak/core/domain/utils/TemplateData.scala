package fr.gospeak.core.domain.utils

import fr.gospeak.core.domain

/*
  Formatted data for user templates (mustache for example)
 */
sealed trait TemplateData

object TemplateData {

  final case class User(slug: String,
                        name: String,
                        firstName: String,
                        lastName: String,
                        avatar: String,
                        email: String)

  object User {
    def apply(u: domain.User): User = new User(
      slug = u.slug.value,
      name = u.name.value,
      firstName = u.firstName,
      lastName = u.lastName,
      avatar = u.avatar.url.value,
      email = u.email.value)
  }

  final case class Cfp(slug: String,
                       name: String,
                       description: String,
                       tags: Seq[String])

  object Cfp {
    def apply(c: domain.Cfp): Cfp = new Cfp(
      slug = c.slug.value,
      name = c.name.value,
      description = c.description.value,
      tags = c.tags.map(_.value))
  }

  final case class Proposal(title: String,
                            description: String,
                            slides: Option[String],
                            video: Option[String],
                            tags: Seq[String])

  object Proposal {
    def apply(p: domain.Proposal): Proposal = new Proposal(
      title = p.title.value,
      description = p.description.value,
      slides = p.slides.map(_.value),
      video = p.video.map(_.value),
      tags = p.tags.map(_.value))
  }

  final case class ProposalCreated(cfp: Cfp,
                                   proposal: Proposal,
                                   user: User) extends TemplateData

  object ProposalCreated {
    def apply(msg: GospeakMessage.ProposalCreated): ProposalCreated = new ProposalCreated(
      cfp = Cfp(msg.cfp),
      proposal = Proposal(msg.proposal),
      user = User(msg.user))
  }

}
