package gospeak.core.domain.utils

import java.time.LocalDateTime

import gospeak.core.domain
import gospeak.core.domain.utils.GsMessage.Linked
import gospeak.libs.scala.StringUtils._
import gospeak.libs.scala.domain.CustomException

/*
  Formatted data for user templates (mustache for example)
  WARNING: updating theses classes may break some user templating, be careful!!!
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

  final case class Description(full: String, short1: String, short2: String, short3: String)

  final case class User(slug: String, name: String, firstName: String, lastName: String, avatar: String, email: String)

  final case class Group(link: String, publicLink: Option[String], slug: String, name: String, description: Description, tags: Seq[String])

  final case class Cfp(link: String, publicLink: Option[String], slug: String, name: String, description: Description, tags: Seq[String])

  final case class Event(link: String, publicLink: Option[String], slug: String, name: String, description: Description, start: StrDateTime, tags: Seq[String])

  final case class Proposal(link: String, title: String, description: Description, slides: Option[String], video: Option[String], tags: Seq[String])

  final case class EventVenue(name: String, address: String, logoUrl: String, addressUrl: String)

  final case class TalkSpeaker(link: String, publicLink: Option[String], name: String, avatar: String)

  final case class EventTalk(link: String, publicLink: Option[String], title: String, description: Description, speakers: Seq[TalkSpeaker], tags: Seq[String])


  final case class EventCreated(group: Group, event: Event, user: User) extends TemplateData

  final case class TalkAdded(group: Group, event: Event, cfp: Cfp, proposal: Proposal, user: User) extends TemplateData

  final case class TalkRemoved(group: Group, event: Event, cfp: Cfp, proposal: Proposal, user: User) extends TemplateData

  final case class EventPublished(group: Group, event: Event, user: User) extends TemplateData

  final case class ProposalCreated(group: Group, cfp: Cfp, proposal: Proposal, user: User) extends TemplateData

  final case class EventInfo(group: Group, event: Event, venue: Option[EventVenue], cfp: Option[Cfp], talks: Seq[EventTalk]) extends TemplateData

  object Sample {
    private val description = desc(
      """Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam auctor odio vitae venenatis porta. Quisque cursus dolor augue, nec pharetra dolor ullamcorper id.
        |
        |Donec sed dignissim ligula, eget aliquam ante.
        |
        |Vestibulum nisl mauris, congue eu blandit eget, scelerisque ut eros. In porta ultrices magna non consequat.
      """.stripMargin)
    private val host = "https://gospeak.io"
    private val dateTime = date(LocalDateTime.of(2019, 9, 10, 19, 0, 0))
    private val user = User("john-doe", "John Doe", "John", "Doe", "https://api.adorable.io/avatars/john-doe.png", "john.doe@mail.com")
    private val group = Group(s"$host/u/groups/humantalks-paris", Some(s"$host/groups/humantalks-paris"), "humantalks-paris", "HumanTalks Paris", description, Seq("tech"))
    private val cfp = Cfp(s"${group.link}/cfps/humantalks-paris", Some(s"$host/cfps/humantalks-paris"), "humantalks-paris", "HumanTalks Paris", description, Seq("tech"))
    private val event = Event(s"${group.link}/events/2019-09", group.publicLink.map(l => s"$l/events/2019-09"), "2019-09", "HumanTalks Paris Septembre", description, dateTime, Seq("IOT", "UX", "Clean Code"))
    private val proposal1 = Proposal(s"${cfp.link}/proposals/28f26543-1ab8-4749-b0ac-786d1bd76888", "The Scala revolution", description, None, None, Seq("scala", "fp"))
    private val proposal2 = Proposal(s"${cfp.link}/proposals/28f26543-1ab8-4749-b0ac-786d1bd76666", "Public speaking for everyone", description, None, None, Seq("social", "marketing"))
    private val eventVenue = EventVenue("Zeenea", "48 Rue de Ponthieu, 75008 Paris", "https://dataxday.fr/wp-content/uploads/2018/01/zeenea-logo.png", "https://maps.google.com/?cid=3360768160548514744")
    private val talkSpeaker1 = TalkSpeaker(s"${group.link}/speakers/john-doe", Some(s"$host/speakers/john-doe"), "John Doe", "https://api.adorable.io/avatars/john-doe.png")
    private val talkSpeaker2 = TalkSpeaker(s"${group.link}/speakers/jane-doe", None, "Jane Doe", "https://api.adorable.io/avatars/jane-doe.png")
    private val eventTalk1 = EventTalk(proposal1.link, Some(s"$host/groups/humantalks-paris/talks/28f26543-1ab8-4749-b0ac-786d1bd76888"), proposal1.title, proposal1.description, Seq(talkSpeaker1), proposal1.tags)
    private val eventTalk2 = EventTalk(proposal2.link, Some(s"$host/groups/humantalks-paris/talks/28f26543-1ab8-4749-b0ac-786d1bd76666"), proposal2.title, proposal2.description, Seq(talkSpeaker1, talkSpeaker2), proposal2.tags)

    private val eventCreated = EventCreated(group = group, event = event, user = user)
    private val talkAdded = TalkAdded(group = group, event = event, cfp = cfp, proposal = proposal1, user = user)
    private val talkRemoved = TalkRemoved(group = group, event = event, cfp = cfp, proposal = proposal1, user = user)
    private val eventPublished = EventPublished(group = group, event = event, user = user)
    private val proposalCreated = ProposalCreated(group, cfp, proposal1, user)
    val eventInfo: EventInfo = EventInfo(group, event, Some(eventVenue), Some(cfp), Seq(eventTalk1, eventTalk2))

    private[utils] val all = Seq(eventCreated, talkAdded, talkRemoved, eventPublished, proposalCreated, eventInfo)
    private val map = all.map(d => (d.ref, d)).toMap

    def fromRef(ref: Ref): Option[TemplateData] = map.get(ref)
  }

  private def date(d: LocalDateTime): StrDateTime = StrDateTime(
    year = leftPad(d.getYear.toString, 4, '0'),
    month = leftPad(d.getMonthValue.toString, 2, '0'),
    monthStr = d.getMonth.name().toLowerCase.capitalize,
    day = leftPad(d.getDayOfMonth.toString, 2, '0'),
    dayStr = d.getDayOfWeek.name().toLowerCase.capitalize,
    hour = leftPad(d.getHour.toString, 2, '0'),
    minute = leftPad(d.getMinute.toString, 2, '0'),
    second = leftPad(d.getSecond.toString, 2, '0'))

  private def desc(d: String): Description = Description(
    full = d,
    short1 = d.split("\n").head.take(140),
    short2 = d.split("\n").head.take(280),
    short3 = d.take(280))

  private def user(u: domain.User): User = User(slug = u.slug.value, name = u.name.value, firstName = u.firstName, lastName = u.lastName, avatar = u.avatar.url.value, email = u.email.value)

  private def group(g: Linked[domain.Group]): Group = Group(link = g.link, publicLink = g.publicLink, slug = g.value.slug.value, name = g.value.name.value, description = desc(g.value.description.value), tags = g.value.tags.map(_.value))

  private def cfp(c: Linked[domain.Cfp]): Cfp = Cfp(link = c.link, publicLink = c.publicLink, slug = c.value.slug.value, name = c.value.name.value, description = desc(c.value.description.value), tags = c.value.tags.map(_.value))

  private def event(e: Linked[domain.Event]): Event = Event(link = e.link, publicLink = e.publicLink, slug = e.value.slug.value, name = e.value.name.value, description = desc(e.value.description.value), start = date(e.value.start), tags = e.value.tags.map(_.value))

  private def proposal(p: Linked[domain.Proposal]): Proposal = Proposal(link = p.link, title = p.value.title.value, description = desc(p.value.description.value), slides = p.value.slides.map(_.value), video = p.value.video.map(_.value), tags = p.value.tags.map(_.value))

  private def eventVenue(v: domain.Venue.Full): EventVenue = EventVenue(v.partner.name.value, v.address.value, v.partner.logo.value, v.address.url)

  private def talkSpeaker(v: Linked[domain.User]): TalkSpeaker = TalkSpeaker(link = v.link, publicLink = v.publicLink, name = v.value.name.value, avatar = v.value.avatar.url.value)

  private def eventTalk(p: Linked[domain.Proposal], s: Seq[Linked[domain.User]]): EventTalk =
    EventTalk(link = p.link, publicLink = p.publicLink, title = p.value.title.value, description = desc(p.value.description.value), s.map(talkSpeaker), tags = p.value.tags.map(_.value))

  def eventCreated(msg: GsMessage.EventCreated): EventCreated = EventCreated(group(msg.group), event(msg.event), user(msg.user))

  def talkAdded(msg: GsMessage.TalkAdded): TalkAdded = TalkAdded(group(msg.group), event(msg.event), cfp(msg.cfp), proposal(msg.proposal), user(msg.user))

  def talkRemoved(msg: GsMessage.TalkRemoved): TalkRemoved = TalkRemoved(group(msg.group), event(msg.event), cfp(msg.cfp), proposal(msg.proposal), user(msg.user))

  def eventPublished(msg: GsMessage.EventPublished): EventPublished = EventPublished(group(msg.group), event(msg.event), user(msg.user))

  def proposalCreated(msg: GsMessage.ProposalCreated): ProposalCreated = ProposalCreated(group(msg.group), cfp(msg.cfp), proposal(msg.proposal), user(msg.user))

  def eventInfo(g: Linked[domain.Group], e: Linked[domain.Event], v: Option[domain.Venue.Full], c: Option[Linked[domain.Cfp]], ts: Seq[Linked[domain.Proposal]], ss: Seq[Linked[domain.User]]): EventInfo =
    EventInfo(group(g), event(e), v.map(eventVenue), c.map(cfp), ts.map(t => eventTalk(t, t.value.speakers.toList.flatMap(s => ss.find(_.value.id == s)))))

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
