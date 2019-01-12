package fr.gospeak.web

import fr.gospeak.core.domain._

import scala.concurrent.Future

object Values {
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

  private val users: Seq[User] = Seq(
    User(userId, "Loïc", "Knuchel"))

  private val groups: Seq[Group] = Seq(
    Group(group1, Group.Slug("ht-paris"), Group.Name("HumanTalks Paris"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(userId)),
    Group(group2, Group.Slug("paris-js"), Group.Name("Paris.Js"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq()),
    Group(group3, Group.Slug("data-gov"), Group.Name("Data governance"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(userId)))

  private val events: Seq[Event] = Seq(
    Event(event1, Event.Slug("2019-03"), group1, Event.Name("HumanTalks Paris Mars 2019"), "desc", Some("Zeenea"), Seq(proposal1)),
    Event(event2, Event.Slug("2019-04"), group1, Event.Name("HumanTalks Paris Avril 2019"), "desc", None, Seq()),
    Event(event3, Event.Slug("2019-03"), group2, Event.Name("Paris.Js Avril"), "desc", None, Seq()),
    Event(event4, Event.Slug("2019-03"), group3, Event.Name("Nouveaux modèles de gouvenance"), "desc", None, Seq()))

  private val talks: Seq[Talk] = Seq(
    Talk(talk1, Talk.Slug("why-fp"), Talk.Title("Why FP"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(userId)),
    Talk(talk2, Talk.Slug("scala-best-practices"), Talk.Title("Scala Best Practices"), "Cras sit amet nibh libero, in gravida nulla..", Seq(userId)),
    Talk(talk3, Talk.Slug("nodejs-news"), Talk.Title("NodeJs news"), "Cras sit amet nibh libero, in gravida nulla..", Seq()))

  private val proposals: Seq[Proposal] = Seq(
    Proposal(proposal1, talk1, group1, Talk.Title("Why FP"), "temporary description"))

  val user: User = users.find(_.id == userId).head // logged user

  def getGroupId(group: String): Future[Option[Group.Id]] = Future.successful(groups.find(_.slug.value == group).map(_.id))

  def getEventId(group: Group.Id, event: String): Future[Option[Event.Id]] = Future.successful(events.find(_.slug.value == event).map(_.id))

  def getTalkId(talk: String): Future[Option[Talk.Id]] = Future.successful(talks.find(_.slug.value == talk).map(_.id))

  def getGroups(user: User.Id): Future[Seq[Group]] = Future.successful(groups.filter(_.owners.contains(user)))

  def getGroup(id: Group.Id, user: User.Id): Future[Option[Group]] = Future.successful(groups.find(_.id == id).filter(_.owners.contains(user)))

  def getEvents(group: Group.Id): Future[Seq[Event]] = Future.successful(events.filter(_.group == group))

  def getEvent(id: Event.Id): Future[Option[Event]] = Future.successful(events.find(_.id == id))

  def getTalks(user: User.Id): Future[Seq[Talk]] = Future.successful(talks.filter(_.speakers.contains(user)))

  def getTalk(id: Talk.Id, user: User.Id): Future[Option[Talk]] = Future.successful(talks.find(_.id == id).filter(_.speakers.contains(user)))

  def getProposals(talk: Talk.Id): Future[Seq[(Group, Proposal)]] = Future.successful(proposals.filter(_.talk == talk).flatMap(p => groups.find(_.id == p.group).map(g => (g, p))))

  def getProposals(group: Group.Id): Future[Seq[Proposal]] = Future.successful(proposals.filter(_.group == group))

  def getProposal(id: Proposal.Id): Future[Option[Proposal]] = Future.successful(proposals.find(_.id == id))
}
