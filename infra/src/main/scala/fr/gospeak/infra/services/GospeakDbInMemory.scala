package fr.gospeak.infra.services

import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{Email, Meta, Page}
import fr.gospeak.core.services.GospeakDb

import scala.collection.mutable
import scala.concurrent.Future

class GospeakDbInMemory extends GospeakDb {
  private val user1 = User.Id.generate()
  private val user2 = User.Id.generate()
  private val user3 = User.Id.generate()
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
  private val talk4 = Talk.Id.generate()
  private val talk5 = Talk.Id.generate()
  private val talk6 = Talk.Id.generate()
  private val proposal1 = Proposal.Id.generate()
  private val proposal2 = Proposal.Id.generate()
  private val proposal3 = Proposal.Id.generate()
  private val proposal4 = Proposal.Id.generate()
  private val proposal5 = Proposal.Id.generate()

  private val users = mutable.ArrayBuffer(
    User(user1, "Demo", "User", Email("demo@mail.com")),
    User(user2, "Speaker", "User", Email("speaker@mail.com")),
    User(user3, "Orga", "User", Email("orga@mail.com")),
    User(User.Id.generate(), "Empty", "User", Email("empty@mail.com")))

  private val groups = mutable.ArrayBuffer(
    Group(group1, Group.Slug("ht-paris"), Group.Name("HumanTalks Paris"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(user1, user3)),
    Group(group2, Group.Slug("paris-js"), Group.Name("Paris.Js"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(user3)),
    Group(group3, Group.Slug("data-gov"), Group.Name("Data governance"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(user1)))

  private val events = mutable.ArrayBuffer(
    Event(event1, Event.Slug("2019-03"), group1, Event.Name("HumanTalks Paris Mars 2019"), Some("desc"), Some("Zeenea"), Seq(), Meta(user1)),
    Event(event2, Event.Slug("2019-04"), group1, Event.Name("HumanTalks Paris Avril 2019"), None, None, Seq(), Meta(user3)),
    Event(event3, Event.Slug("2019-03"), group2, Event.Name("Paris.Js Avril"), None, None, Seq(), Meta(user3)),
    Event(event4, Event.Slug("2019-03"), group3, Event.Name("Nouveaux modèles de gouvenance"), None, None, Seq(), Meta(user1)))

  private val talks = mutable.ArrayBuffer(
    Talk(talk1, Talk.Slug("why-fp"), Talk.Title("Why FP"), "Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.", Seq(user1), Meta(user1)),
    Talk(talk2, Talk.Slug("scala-best-practices"), Talk.Title("Scala Best Practices"), "Cras sit amet nibh libero, in gravida nulla..", Seq(user1, user2), Meta(user1)),
    Talk(talk3, Talk.Slug("nodejs-news"), Talk.Title("NodeJs news"), "Cras sit amet nibh libero, in gravida nulla..", Seq(user1), Meta(user1)),
    Talk(talk4, Talk.Slug("scalajs-react"), Talk.Title("ScalaJS + React = ♥"), "Cras sit amet nibh libero, in gravida nulla..", Seq(user2, user1), Meta(user2)),
    Talk(talk5, Talk.Slug("gagner-1-million"), Talk.Title("Gagner 1 Million au BlackJack avec Akka"), "Cras sit amet nibh libero, in gravida nulla..", Seq(user2), Meta(user2)),
    Talk(talk6, Talk.Slug("demarrer-avec-spark"), Talk.Title("7 conseils pour démarrer avec Spark"), "Cras sit amet nibh libero, in gravida nulla..", Seq(user2), Meta(user2))
  )

  private val proposals = mutable.ArrayBuffer(
    Proposal(proposal1, talk1, group1, Proposal.Title("Why FP"), "temporary description"),
    Proposal(proposal2, talk2, group1, Proposal.Title("Scala Best Practices"), "temporary description"),
    Proposal(proposal3, talk2, group2, Proposal.Title("Scala Best Practices"), "temporary description"),
    Proposal(proposal4, talk3, group1, Proposal.Title("NodeJs news"), "temporary description"),
    Proposal(proposal5, talk4, group2, Proposal.Title("ScalaJS + React = ♥"), "temporary description"))

  private var logged: Option[User] = users.headOption

  override def setLogged(user: User): Future[Unit] = {
    logged = Some(user)
    Future.successful(())
  }

  override def logout(): Future[Unit] = {
    logged = None
    Future.successful(())
  }

  override def userAware(): Option[User] = logged

  override def authed(): User = logged.get

  override def createUser(firstName: String, lastName: String, email: Email): Future[User] = {
    val user = User(User.Id.generate(), firstName, lastName, email)
    users += user
    Future.successful(user)
  }

  override def getUser(email: Email): Future[Option[User]] = Future.successful(users.find(_.email == email))

  override def getGroupId(group: Group.Slug): Future[Option[Group.Id]] = Future.successful(groups.find(_.slug == group).map(_.id))

  override def getEventId(group: Group.Id, event: Event.Slug): Future[Option[Event.Id]] = Future.successful(events.find(_.slug == event).map(_.id))

  override def getTalkId(talk: Talk.Slug): Future[Option[Talk.Id]] = Future.successful(talks.find(_.slug == talk).map(_.id))

  override def getGroups(user: User.Id, params: Page.Params): Future[Page[Group]] = {
    val res = groups.filter(_.owners.contains(user))
      .filter(t => params.search.forall(s => contains(t.name.value, s) || contains(t.description, s)))
      .sortBy(t => params.sortBy.map(_.value) match {
        case Some("description") => t.description
        case _ => t.name.value
      })
    val page = Page(res.slice(params.offsetStart, params.offsetEnd), params, Page.Total(res.length))
    Future.successful(page)
  }

  override def getGroup(id: Group.Id, user: User.Id): Future[Option[Group]] = Future.successful(groups.find(_.id == id).filter(_.owners.contains(user)))

  override def getEvents(group: Group.Id, params: Page.Params): Future[Page[Event]] = {
    val res = events.filter(_.group == group)
      .filter(t => params.search.forall(s => contains(t.name.value, s) || t.description.forall(contains(_, s))))
      .sortBy(t => params.sortBy.map(_.value) match {
        case Some("description") => t.description.getOrElse("")
        case _ => t.name.value
      })
    val page = Page(res.slice(params.offsetStart, params.offsetEnd), params, Page.Total(res.length))
    Future.successful(page)
  }

  override def getEvent(id: Event.Id): Future[Option[Event]] = Future.successful(events.find(_.id == id))

  override def createEvent(group: Group.Id, slug: Event.Slug, name: Event.Name, by: User.Id): Future[Event] = {
    val event = Event(Event.Id.generate(), slug, group, name, None, None, Seq(), Meta(by))
    events += event
    Future.successful(event)
  }

  override def getTalks(user: User.Id, params: Page.Params): Future[Page[Talk]] = {
    val res = talks.filter(_.speakers.contains(user))
      .filter(t => params.search.forall(s => contains(t.title.value, s) || contains(t.description, s)))
      .sortBy(t => params.sortBy.map(_.value) match {
        case Some("description") => t.description
        case _ => t.title.value
      })
    val page = Page(res.slice(params.offsetStart, params.offsetEnd), params, Page.Total(res.length))
    Future.successful(page)
  }

  override def getTalk(id: Talk.Id, user: User.Id): Future[Option[Talk]] = Future.successful(talks.find(_.id == id).filter(_.speakers.contains(user)))

  override def createTalk(slug: Talk.Slug, title: Talk.Title, description: String, by: User.Id): Future[Talk] = {
    val talk = Talk(Talk.Id.generate(), slug, title, description, Seq(by), Meta(by))
    talks += talk
    Future.successful(talk)
  }

  override def getProposals(talk: Talk.Id, params: Page.Params): Future[Page[(Group, Proposal)]] = {
    val res = proposals.filter(_.talk == talk).flatMap(p => groups.find(_.id == p.group).map(g => (g, p)))
      .filter(t => params.search.forall(s => contains(t._2.title.value, s) || contains(t._2.description, s)))
      .sortBy(t => params.sortBy.map(_.value) match {
        case Some("description") => t._2.description
        case _ => t._2.title.value
      })
    val page = Page(res.slice(params.offsetStart, params.offsetEnd), params, Page.Total(res.length))
    Future.successful(page)
  }

  override def getProposals(group: Group.Id, params: Page.Params): Future[Page[Proposal]] = {
    val res = proposals.filter(_.group == group)
      .filter(t => params.search.forall(s => contains(t.title.value, s) || contains(t.description, s)))
      .sortBy(t => params.sortBy.map(_.value) match {
        case Some("description") => t.description
        case _ => t.title.value
      })
    val page = Page(res.slice(params.offsetStart, params.offsetEnd), params, Page.Total(res.length))
    Future.successful(page)
  }

  override def getProposal(id: Proposal.Id): Future[Option[Proposal]] = Future.successful(proposals.find(_.id == id))

  private def contains(str: String, q: Page.Search): Boolean = str.toLowerCase.contains(q.value.toLowerCase)
}
