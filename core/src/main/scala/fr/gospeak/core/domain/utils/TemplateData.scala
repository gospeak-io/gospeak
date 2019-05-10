package fr.gospeak.core.domain.utils

import java.time.LocalDateTime

import fr.gospeak.core.domain

/*
  Formatted data for user templates (mustache for example)
 */
sealed trait TemplateData

object TemplateData {

  final case class User(slug: String, name: String, firstName: String, lastName: String, avatar: String, email: String)

  final case class Group(slug: String, name: String, description: String, tags: Seq[String])

  final case class Cfp(slug: String, name: String, description: String, tags: Seq[String])

  final case class Event(slug: String, name: String, description: String, start: LocalDateTime, tags: Seq[String])

  final case class Proposal(title: String, description: String, slides: Option[String], video: Option[String], tags: Seq[String])


  final case class EventCreated(group: Group, event: Event, user: User) extends TemplateData

  final case class TalkAdded(group: Group, event: Event, cfp: Cfp, proposal: Proposal, user: User) extends TemplateData

  final case class TalkRemoved(group: Group, event: Event, cfp: Cfp, proposal: Proposal, user: User) extends TemplateData

  final case class EventPublished() extends TemplateData

  final case class ProposalCreated(cfp: Cfp, proposal: Proposal, user: User) extends TemplateData

  object Sample {
    private val user = User("john-doe", "John Doe", "John", "Doe", "https://secure.gravatar.com/avatar/fa24c69431e3df73ef30d06860dd6258?size=100&default=wavatar", "john.doe@mail.com")
    private val group = Group("humantalks-paris", "HumanTalks Paris", "", Seq("tech"))
    private val cfp = Cfp("humantalks-paris", "HumanTalks Paris", "", Seq("tech"))
    private val event = Event("2019-09", "HumanTalks Paris Septembre", "", LocalDateTime.of(2019, 9, 10, 19, 0, 0), Seq("IOT", "UX", "Clean Code"))
    private val proposal = Proposal("The Scala revolution", "", None, None, Seq("scala", "fp"))

    val eventCreated = EventCreated(group = Sample.group, event = Sample.event, user = Sample.user)
    val talkAdded = TalkAdded(group = Sample.group, event = Sample.event, cfp = Sample.cfp, proposal = Sample.proposal, user = Sample.user)
    val talkRemoved = TalkRemoved(group = Sample.group, event = Sample.event, cfp = Sample.cfp, proposal = Sample.proposal, user = Sample.user)
    val eventPublished = EventPublished()
    val proposalCreated = ProposalCreated(Sample.cfp, Sample.proposal, Sample.user)
  }

  private def build(u: domain.User): User = User(slug = u.slug.value, name = u.name.value, firstName = u.firstName, lastName = u.lastName, avatar = u.avatar.url.value, email = u.email.value)

  private def build(g: domain.Group): Group = Group(slug = g.slug.value, name = g.name.value, description = g.description.value, tags = g.tags.map(_.value))

  private def build(c: domain.Cfp): Cfp = Cfp(slug = c.slug.value, name = c.name.value, description = c.description.value, tags = c.tags.map(_.value))

  private def build(e: domain.Event): Event = Event(slug = e.slug.value, name = e.name.value, description = e.description.value, start = e.start, tags = e.tags.map(_.value))

  private def build(p: domain.Proposal): Proposal = Proposal(title = p.title.value, description = p.description.value, slides = p.slides.map(_.value), video = p.video.map(_.value), tags = p.tags.map(_.value))

  def eventCreated(msg: GospeakMessage.EventCreated): EventCreated = EventCreated(build(msg.group), build(msg.event), build(msg.user))

  def talkAdded(msg: GospeakMessage.TalkAdded): TalkAdded = TalkAdded(build(msg.group), build(msg.event), build(msg.cfp), build(msg.proposal), build(msg.user))

  def talkRemoved(msg: GospeakMessage.TalkRemoved): TalkRemoved = TalkRemoved(build(msg.group), build(msg.event), build(msg.cfp), build(msg.proposal), build(msg.user))

  def eventPublished(msg: GospeakMessage.EventPublished): EventPublished = EventPublished()

  def proposalCreated(msg: GospeakMessage.ProposalCreated): ProposalCreated = ProposalCreated(build(msg.cfp), build(msg.proposal), build(msg.user))
}
