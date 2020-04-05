package gospeak.core.domain

import java.time.Instant

import cats.data.NonEmptyList
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.{Constants, Info, SocialAccounts}
import gospeak.core.services.meetup.domain.MeetupCredentials
import gospeak.core.services.slack.domain.{SlackAction, SlackCredentials}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

import scala.util.{Failure, Success, Try}

final case class Group(id: Group.Id,
                       slug: Group.Slug,
                       name: Group.Name,
                       logo: Option[Logo],
                       banner: Option[Banner],
                       contact: Option[EmailAddress],
                       website: Option[Url],
                       description: Markdown,
                       location: Option[GMapPlace],
                       owners: NonEmptyList[User.Id],
                       social: SocialAccounts,
                       tags: Seq[Tag],
                       status: Group.Status,
                       info: Info) {
  def data: Group.Data = Group.Data(this)

  def hasOrga(user: User.Id): Boolean = owners.toList.contains(user)

  def senders(user: User): Seq[EmailAddress.Contact] = Seq(
    contact.map(email => EmailAddress.Contact(email, Some(name.value))),
    Some(EmailAddress.Contact(user.email, Some(user.name.value))),
    Some(Constants.Gospeak.noreplyEmail)).flatten

  def users: List[User.Id] = (owners.toList ++ info.users).distinct
}

object Group {
  def apply(d: Data, owners: NonEmptyList[User.Id], info: Info): Group =
    new Group(Id.generate(), d.slug, d.name, d.logo, d.banner, d.contact, d.website, d.description, d.location, owners, d.social, d.tags, Status.Active, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Group.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Group.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Member(group: Group.Id,
                          role: Member.Role,
                          presentation: Option[String],
                          joinedAt: Instant,
                          leavedAt: Option[Instant],
                          user: User) {
    def name: User.Name = user.name

    def avatar: Avatar = user.avatar

    def website: Option[Url] = user.website

    def social: SocialAccounts = user.social

    def isPublic: Boolean = user.isPublic

    def isActive: Boolean = leavedAt.isEmpty
  }

  object Member {

    sealed trait Role extends StringEnum {
      override def value: String = toString
    }

    object Role extends EnumBuilder[Role]("Group.Member.Role") {

      case object Owner extends Role

      case object Member extends Role

      val all: Seq[Role] = Seq(Owner, Member)
    }

  }

  sealed trait Status extends StringEnum {
    def value: String = toString
  }

  object Status extends EnumBuilder[Status]("Group.Status") {

    case object Active extends Status

    case object Disabled extends Status

    val all: Seq[Status] = Seq(Active, Disabled)
  }

  final case class Full(group: Group, memberCount: Long, eventCount: Long, talkCount: Long) {
    def id: Id = group.id

    def slug: Slug = group.slug

    def name: Name = group.name

    def logo: Option[Logo] = group.logo

    def contact: Option[EmailAddress] = group.contact

    def website: Option[Url] = group.website

    def description: Markdown = group.description

    def location: Option[GMapPlace] = group.location

    def owners: NonEmptyList[User.Id] = group.owners

    def social: SocialAccounts = group.social

    def tags: Seq[Tag] = group.tags

    def info: Info = group.info
  }

  final case class Stats(id: Id,
                         slug: Slug,
                         name: Name,
                         memberCount: Long,
                         proposalCount: Long,
                         eventCount: Long)

  final case class Data(slug: Group.Slug,
                        name: Group.Name,
                        logo: Option[Logo],
                        banner: Option[Banner],
                        contact: Option[EmailAddress],
                        website: Option[Url],
                        description: Markdown,
                        location: Option[GMapPlace],
                        social: SocialAccounts,
                        tags: Seq[Tag])

  object Data {
    def apply(g: Group): Data = new Data(g.slug, g.name, g.logo, g.banner, g.contact, g.website, g.description, g.location, g.social, g.tags)
  }


  final case class Settings(accounts: Settings.Accounts,
                            event: Settings.Event,
                            proposal: Settings.Proposal,
                            actions: Map[Settings.Action.Trigger, Seq[Settings.Action]]) {
    def set(meetup: MeetupCredentials): Settings = copy(accounts = accounts.copy(meetup = Some(meetup)))

    def set(slack: SlackCredentials): Settings = copy(accounts = accounts.copy(slack = Some(slack)))

    def removeAccount(kind: String): Try[Settings] = kind match {
      case "meetup" => Success(copy(accounts = accounts.copy(meetup = None)))
      case "slack" => Success(copy(accounts = accounts.copy(slack = None)))
      // case "twitter" => Success(copy(accounts = accounts.copy(twitter = None)))
      // case "youtube" => Success(copy(accounts = accounts.copy(youtube = None)))
      case _ => Failure(new IllegalArgumentException(s"Account '$kind' does not exists"))
    }

    def addEventTemplate(id: String, tmpl: Mustache.Text[Message.EventInfo]): Try[Settings] =
      event.addTemplate(id, tmpl).map(e => copy(event = e))

    def removeEventTemplate(id: String): Try[Settings] =
      event.removeTemplate(id).map(e => copy(event = e))

    def updateEventTemplate(oldId: String, newId: String, tmpl: Mustache.Markdown[Message.EventInfo]): Try[Settings] =
      event.updateTemplate(oldId, newId, tmpl).map(e => copy(event = e))
  }

  object Settings {

    final case class Accounts(meetup: Option[MeetupCredentials],
                              slack: Option[SlackCredentials])

    sealed trait Action

    object Action {

      sealed abstract class Trigger(val message: Message.Ref, val label: String) extends StringEnum {
        def value: String = getClass.getName.split("[.$]").toList.last // like getSimpleName but avoid "Malformed class name"
      }

      object Trigger extends EnumBuilder[Trigger]("Group.Settings.Action.Trigger") {

        case object OnEventCreated extends Trigger(Message.Ref.eventCreated, "When an Event is created")

        case object OnEventPublish extends Trigger(Message.Ref.eventPublished, "When an Event is published")

        case object OnProposalCreated extends Trigger(Message.Ref.proposalCreated, "When a Proposal is submitted to a CFP")

        case object OnEventAddTalk extends Trigger(Message.Ref.proposalAddedToEvent, "When a Talk is added to an Event")

        case object OnEventRemoveTalk extends Trigger(Message.Ref.proposalRemovedFromEvent, "When a Talk is removed from an Event")

        val all: Seq[Trigger] = Seq(OnEventCreated, OnEventPublish, OnProposalCreated, OnEventAddTalk, OnEventRemoveTalk)
      }

      final case class Email(to: Mustache.Text[Any],
                             subject: Mustache.Text[Any],
                             content: Mustache.Markdown[Any]) extends Action

      final case class Slack(value: SlackAction) extends Action

    }

    final case class Event(description: Mustache.Markdown[Message.EventInfo],
                           templates: Map[String, Mustache.Text[Message.EventInfo]]) {
      private def defaultTemplates: Map[String, Mustache.Markdown[Message.EventInfo]] = Map(
        Event.descriptionTmplId -> Some(description),
      ).collect { case (id, Some(tmpl)) => (id, tmpl) }

      def allTemplates: Seq[(String, Boolean, Mustache[Message.EventInfo])] =
        defaultTemplates.toSeq.map { case (id, t) => (id, true, t) } ++
          templates.toSeq.map { case (id, t) => (id, false, t) }.sortBy(_._1)

      def getTemplate(id: String): Option[Mustache[Message.EventInfo]] =
        defaultTemplates.get(id).orElse(templates.get(id))

      def removeTemplate(id: String): Try[Event] =
        if (templates.contains(id)) Success(copy(templates = templates - id))
        else if (Event.defaultTmplIds.contains(id)) Failure(new IllegalArgumentException(s"Template '$id' is a default one, unable to remove it"))
        else Failure(new IllegalArgumentException(s"Template '$id' does not exists, unable to remove it"))

      def addTemplate(id: String, tmpl: Mustache.Text[Message.EventInfo]): Try[Event] =
        if (templates.contains(id) || Event.defaultTmplIds.contains(id)) Failure(new IllegalArgumentException(s"Template '$id' already exists, unable to add it"))
        else Success(copy(templates = templates ++ Map(id -> tmpl)))

      def updateTemplate(oldId: String, newId: String, tmpl: Mustache[Message.EventInfo]): Try[Event] =
        if (newId == Event.descriptionTmplId) Success(copy(description = tmpl.asMarkdown))
        else removeTemplate(oldId).mapFailure(e => new IllegalArgumentException(s"Template '$oldId' does not exists, unable to update it", e))
          .flatMap(_.addTemplate(newId, tmpl.asText).mapFailure(e => new IllegalArgumentException(s"Template '$newId' already exists, unable to rename to it", e)))
    }

    object Event {
      val descriptionTmplId = "Event description"

      val defaultTmplIds: Seq[String] = Seq(descriptionTmplId)
    }

    final case class Proposal(tweet: Mustache.Text[Message.ProposalInfo])

  }

}
