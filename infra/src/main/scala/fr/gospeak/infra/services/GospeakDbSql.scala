package fr.gospeak.infra.services

import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Meta
import fr.gospeak.core.services.GospeakDb

import scala.collection.mutable
import scala.concurrent.Future

class GospeakDbSql extends GospeakDb {
  private val userId = User.Id.generate()
  private val group1 = Group.Id.generate()
  private val group2 = Group.Id.generate()
  private val group3 = Group.Id.generate()
  private val event1 = Event.Id.generate()
  private val event2 = Event.Id.generate()
  private val event3 = Event.Id.generate()
  private val event4 = Event.Id.generate()
  private val talk1 = Talk.Id.generate()
  private val talk2 = Talk.Id.generate()
  private val talk3 = Talk.Id.generate()
  private val proposal1 = Proposal.Id.generate()

  private val users = mutable.ArrayBuffer(
    User(userId, "Loïc", "Knuchel"))

  private val groups = mutable.ArrayBuffer(
    Group(group1, Group.Slug("ht-paris"), Group.Name("HumanTalks Paris"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(userId)),
    Group(group2, Group.Slug("paris-js"), Group.Name("Paris.Js"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq()),
    Group(group3, Group.Slug("data-gov"), Group.Name("Data governance"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(userId)))

  private val events = mutable.ArrayBuffer(
    Event(event1, Event.Slug("2019-03"), group1, Event.Name("HumanTalks Paris Mars 2019"), Some("desc"), Some("Zeenea"), Seq(proposal1), Meta(userId)),
    Event(event2, Event.Slug("2019-04"), group1, Event.Name("HumanTalks Paris Avril 2019"), None, None, Seq(), Meta(userId)),
    Event(event3, Event.Slug("2019-03"), group2, Event.Name("Paris.Js Avril"), None, None, Seq(), Meta(userId)),
    Event(event4, Event.Slug("2019-03"), group3, Event.Name("Nouveaux modèles de gouvenance"), None, None, Seq(), Meta(userId)))

  private val talks = mutable.ArrayBuffer(
    Talk(talk1, Talk.Slug("why-fp"), Talk.Title("Why FP"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(userId), Meta(userId)),
    Talk(talk2, Talk.Slug("scala-best-practices"), Talk.Title("Scala Best Practices"), "Cras sit amet nibh libero, in gravida nulla..", Seq(userId), Meta(userId)),
    Talk(talk3, Talk.Slug("nodejs-news"), Talk.Title("NodeJs news"), "Cras sit amet nibh libero, in gravida nulla..", Seq(), Meta(userId)))

  private val proposals = mutable.ArrayBuffer(
    Proposal(proposal1, talk1, group1, Proposal.Title("Why FP"), "temporary description"))

  override def getUser(): User = users.find(_.id == userId).head // TODO mock auth, to remove

  override def getGroupId(group: Group.Slug): Future[Option[Group.Id]] = Future.successful(groups.find(_.slug == group).map(_.id))

  override def getEventId(group: Group.Id, event: Event.Slug): Future[Option[Event.Id]] = Future.successful(events.find(_.slug == event).map(_.id))

  override def getTalkId(talk: Talk.Slug): Future[Option[Talk.Id]] = Future.successful(talks.find(_.slug == talk).map(_.id))

  override def getGroups(user: User.Id): Future[Seq[Group]] = Future.successful(groups.filter(_.owners.contains(user)))

  override def getGroup(id: Group.Id, user: User.Id): Future[Option[Group]] = Future.successful(groups.find(_.id == id).filter(_.owners.contains(user)))

  override def getEvents(group: Group.Id): Future[Seq[Event]] = Future.successful(events.filter(_.group == group))

  override def getEvent(id: Event.Id): Future[Option[Event]] = Future.successful(events.find(_.id == id))

  override def createEvent(group: Group.Id, slug: Event.Slug, name: Event.Name, by: User.Id): Future[Event] = {
    val event = Event(Event.Id.generate(), slug, group, name, None, None, Seq(), Meta(by))
    events += event
    Future.successful(event)
  }

  override def getTalks(user: User.Id): Future[Seq[Talk]] = Future.successful(talks.filter(_.speakers.contains(user)))

  override def getTalk(id: Talk.Id, user: User.Id): Future[Option[Talk]] = Future.successful(talks.find(_.id == id).filter(_.speakers.contains(user)))

  override def createTalk(slug: Talk.Slug, title: Talk.Title, description: String, by: User.Id): Future[Talk] = {
    val talk = Talk(Talk.Id.generate(), slug, title, description, Seq(by), Meta(by))
    talks += talk
    Future.successful(talk)
  }

  override def getProposals(talk: Talk.Id): Future[Seq[(Group, Proposal)]] = Future.successful(proposals.filter(_.talk == talk).flatMap(p => groups.find(_.id == p.group).map(g => (g, p))))

  override def getProposals(group: Group.Id): Future[Seq[Proposal]] = Future.successful(proposals.filter(_.group == group))

  override def getProposal(id: Proposal.Id): Future[Option[Proposal]] = Future.successful(proposals.find(_.id == id))
}
