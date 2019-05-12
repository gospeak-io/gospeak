package fr.gospeak.core.domain.utils

import java.time.LocalDateTime

import fr.gospeak.core.domain
import fr.gospeak.libs.scalautils.StringUtils._
import fr.gospeak.libs.scalautils.domain.CustomException

/*
  Formatted data for user templates (mustache for example)
 */
sealed trait TemplateData extends Product with Serializable {
  val ref: TemplateData.Ref = TemplateData.Ref.from(getClass.getSimpleName).right.get
}

object TemplateData {

  final class Ref private(val value: String) extends AnyVal

  object Ref {
    def from(in: String): Either[CustomException, Ref] =
      if (in.isEmpty) Left(CustomException("TemplateData.Ref should not be empty"))
      else if (in == classOf[EventCreated].getSimpleName) Right(new Ref(in))
      else if (in == classOf[TalkAdded].getSimpleName) Right(new Ref(in))
      else if (in == classOf[TalkRemoved].getSimpleName) Right(new Ref(in))
      else if (in == classOf[EventPublished].getSimpleName) Right(new Ref(in))
      else if (in == classOf[ProposalCreated].getSimpleName) Right(new Ref(in))
      else if (in == classOf[EventInfo].getSimpleName) Right(new Ref(in))
      else if (in == domain.Group.Settings.Action.Trigger.OnEventCreated.getClassName) Right(new Ref(classOf[EventCreated].getSimpleName))
      else if (in == domain.Group.Settings.Action.Trigger.OnEventAddTalk.getClassName) Right(new Ref(classOf[TalkAdded].getSimpleName))
      else if (in == domain.Group.Settings.Action.Trigger.OnEventRemoveTalk.getClassName) Right(new Ref(classOf[TalkRemoved].getSimpleName))
      else if (in == domain.Group.Settings.Action.Trigger.OnEventPublish.getClassName) Right(new Ref(classOf[EventPublished].getSimpleName))
      else if (in == domain.Group.Settings.Action.Trigger.OnProposalCreated.getClassName) Right(new Ref(classOf[ProposalCreated].getSimpleName))
      else Left(CustomException(s"Unknown TemplateData.Ref '$in'"))
  }


  final case class StrDateTime(year: String, month: String, monthStr: String, day: String, dayStr: String, hour: String, minute: String, second: String)

  final case class User(slug: String, name: String, firstName: String, lastName: String, avatar: String, email: String)

  final case class Group(link: String, slug: String, name: String, description: String, tags: Seq[String])

  final case class Cfp(link: String, slug: String, name: String, description: String, tags: Seq[String])

  final case class Event(link: String, slug: String, name: String, description: String, start: StrDateTime, tags: Seq[String])

  final case class Proposal(link: String, title: String, description: String, slides: Option[String], video: Option[String], tags: Seq[String])


  final case class EventCreated(group: Group, event: Event, user: User) extends TemplateData

  final case class TalkAdded(group: Group, event: Event, cfp: Cfp, proposal: Proposal, user: User) extends TemplateData

  final case class TalkRemoved(group: Group, event: Event, cfp: Cfp, proposal: Proposal, user: User) extends TemplateData

  final case class EventPublished() extends TemplateData

  final case class ProposalCreated(group: Group, cfp: Cfp, proposal: Proposal, user: User) extends TemplateData

  final case class EventInfo(event: Event) extends TemplateData

  object Sample {
    private val host = "https://gospeak.fr"
    private val dateTime = build(LocalDateTime.of(2019, 9, 10, 19, 0, 0))
    private val user = User("john-doe", "John Doe", "John", "Doe", "https://secure.gravatar.com/avatar/fa24c69431e3df73ef30d06860dd6258?size=100&default=wavatar", "john.doe@mail.com")
    private val group = Group(s"$host/u/groups/humantalks-paris", "humantalks-paris", "HumanTalks Paris", "", Seq("tech"))
    private val cfp = Cfp(s"${group.link}/cfps/humantalks-paris", "humantalks-paris", "HumanTalks Paris", "", Seq("tech"))
    private val event = Event(s"${group.link}/events/2019-09", "2019-09", "HumanTalks Paris Septembre", "", dateTime, Seq("IOT", "UX", "Clean Code"))
    private val proposal = Proposal(s"${cfp.link}/proposals/28f26543-1ab8-4749-b0ac-786d1bd76888", "The Scala revolution", "", None, None, Seq("scala", "fp"))

    private val eventCreated = EventCreated(group = group, event = event, user = user)
    private val talkAdded = TalkAdded(group = group, event = event, cfp = cfp, proposal = proposal, user = user)
    private val talkRemoved = TalkRemoved(group = group, event = event, cfp = cfp, proposal = proposal, user = user)
    private val eventPublished = EventPublished()
    private val proposalCreated = ProposalCreated(group, cfp, proposal, user)
    private val eventInfo = EventInfo(event)

    val all: Seq[TemplateData] = Seq(eventCreated, talkAdded, talkRemoved, eventPublished, proposalCreated, eventInfo)
    private val map = all.map(d => (d.ref, d)).toMap

    def fromRef(ref: Ref): Option[TemplateData] = map.get(ref)
  }

  private def build(u: domain.User): User = User(slug = u.slug.value, name = u.name.value, firstName = u.firstName, lastName = u.lastName, avatar = u.avatar.url.value, email = u.email.value)

  private def build(g: domain.Group, link: String): Group = Group(link = link, slug = g.slug.value, name = g.name.value, description = g.description.value, tags = g.tags.map(_.value))

  private def build(c: domain.Cfp, link: String): Cfp = Cfp(link = link, slug = c.slug.value, name = c.name.value, description = c.description.value, tags = c.tags.map(_.value))

  private def build(e: domain.Event, link: String): Event = Event(link = link, slug = e.slug.value, name = e.name.value, description = e.description.value, start = build(e.start), tags = e.tags.map(_.value))

  private def build(p: domain.Proposal, link: String): Proposal = Proposal(link = link, title = p.title.value, description = p.description.value, slides = p.slides.map(_.value), video = p.video.map(_.value), tags = p.tags.map(_.value))

  private def build(d: LocalDateTime): StrDateTime = StrDateTime(
    year = leftPad(d.getYear.toString, 4, '0'),
    month = leftPad(d.getMonthValue.toString, 2, '0'),
    monthStr = d.getMonth.name().toLowerCase.capitalize,
    day = leftPad(d.getDayOfMonth.toString, 2, '0'),
    dayStr = d.getDayOfWeek.name().toLowerCase.capitalize,
    hour = leftPad(d.getHour.toString, 2, '0'),
    minute = leftPad(d.getMinute.toString, 2, '0'),
    second = leftPad(d.getSecond.toString, 2, '0'))

  def eventCreated(msg: GospeakMessage.EventCreated): EventCreated = EventCreated(build(msg.group, msg.groupLink), build(msg.event, msg.eventLink), build(msg.user))

  def talkAdded(msg: GospeakMessage.TalkAdded): TalkAdded = TalkAdded(build(msg.group, msg.groupLink), build(msg.event, msg.eventLink), build(msg.cfp, msg.cfpLink), build(msg.proposal, msg.proposalLink), build(msg.user))

  def talkRemoved(msg: GospeakMessage.TalkRemoved): TalkRemoved = TalkRemoved(build(msg.group, msg.groupLink), build(msg.event, msg.eventLink), build(msg.cfp, msg.cfpLink), build(msg.proposal, msg.proposalLink), build(msg.user))

  def eventPublished(msg: GospeakMessage.EventPublished): EventPublished = EventPublished()

  def proposalCreated(msg: GospeakMessage.ProposalCreated): ProposalCreated = ProposalCreated(build(msg.group, msg.groupLink), build(msg.cfp, msg.cfpLink), build(msg.proposal, msg.proposalLink), build(msg.user))

  object EventCreated {
    val ref: Ref = Ref.from(classOf[EventCreated].getSimpleName).right.get
  }

  object TalkAdded {
    val ref: Ref = Ref.from(classOf[TalkAdded].getSimpleName).right.get
  }

  object TalkRemoved {
    val ref: Ref = Ref.from(classOf[TalkRemoved].getSimpleName).right.get
  }

  object EventPublished {
    val ref: Ref = Ref.from(classOf[EventPublished].getSimpleName).right.get
  }

  object ProposalCreated {
    val ref: Ref = Ref.from(classOf[ProposalCreated].getSimpleName).right.get
  }

  object EventInfo {
    val ref: Ref = Ref.from(classOf[EventInfo].getSimpleName).right.get
  }

}
