package fr.gospeak.core.domain

import java.time.Instant

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.{Info, TemplateData}
import fr.gospeak.core.services.meetup.domain.MeetupCredentials
import fr.gospeak.core.services.slack.domain.{SlackAction, SlackCredentials}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}
import fr.gospeak.libs.scalautils.domain._

import scala.util.{Failure, Success, Try}

final case class Group(id: Group.Id,
                       slug: Group.Slug,
                       name: Group.Name,
                       contact: Option[EmailAddress],
                       description: Markdown,
                       owners: NonEmptyList[User.Id],
                       tags: Seq[Tag],
                       info: Info) {
  def data: Group.Data = Group.Data(this)
}

object Group {
  def apply(data: Data, owners: NonEmptyList[User.Id], info: Info): Group =
    new Group(Id.generate(), data.slug, data.name, data.contact, data.description, owners, data.tags, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Group.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Group.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Member(user: User,
                          role: Member.Role,
                          presentation: Option[String],
                          joinedAt: Instant)

  object Member {

    sealed trait Role

    object Role {

      case object Owner extends Role

      case object Member extends Role

      val all: Seq[Role] = Seq(Owner, Member)

      def from(str: String): Either[CustomException, Role] =
        all.find(_.toString == str).map(Right(_)).getOrElse(Left(CustomException(s"'$str' is not a valid Group.Member.Role")))

    }

  }

  final case class Data(slug: Group.Slug,
                        name: Group.Name,
                        contact: Option[EmailAddress],
                        description: Markdown,
                        tags: Seq[Tag])

  object Data {
    def apply(group: Group): Data = new Data(group.slug, group.name, group.contact, group.description, group.tags)
  }


  final case class Settings(accounts: Settings.Accounts,
                            event: Settings.Event,
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

    def addEventTemplate(id: String, tmpl: MustacheTextTmpl[TemplateData.EventInfo]): Try[Settings] =
      event.addTemplate(id, tmpl).map(e => copy(event = e))

    def removeEventTemplate(id: String): Try[Settings] =
      event.removeTemplate(id).map(e => copy(event = e))

    def updateEventTemplate(oldId: String, newId: String, tmpl: MustacheMarkdownTmpl[TemplateData.EventInfo]): Try[Settings] =
      event.updateTemplate(oldId, newId, tmpl).map(e => copy(event = e))
  }

  object Settings {

    final case class Accounts(meetup: Option[MeetupCredentials],
                              slack: Option[SlackCredentials])

    // twitter: Option[String],
    // youtube: Option[String])

    sealed trait Action

    object Action {

      sealed abstract class Trigger(val name: String) {
        def getClassName: String = getClass.getName.split("[.$]").toList.last
      }

      object Trigger {

        case object OnEventCreated extends Trigger("When an Event is created")

        case object OnEventAddTalk extends Trigger("When a Talk is added to an Event")

        case object OnEventRemoveTalk extends Trigger("When a Talk is removed from an Event")

        case object OnEventPublish extends Trigger("When an Event is published")

        case object OnProposalCreated extends Trigger("When a Proposal is submitted to a CFP")

        val all: Seq[Trigger] = Seq(OnEventCreated, OnEventAddTalk, OnEventRemoveTalk, OnEventPublish, OnProposalCreated)

        def from(str: String): Option[Trigger] = all.find(_.toString == str)
      }

      final case class Slack(value: SlackAction) extends Action

    }

    final case class Event(description: MustacheMarkdownTmpl[TemplateData.EventInfo],
                           templates: Map[String, MustacheTextTmpl[TemplateData.EventInfo]]) {
      private def defaultTemplates: Map[String, MustacheTmpl[TemplateData.EventInfo]] = Map(
        Event.descriptionTmplId -> Some(description),
      ).collect { case (id, Some(tmpl)) => (id, tmpl) }

      def allTemplates: Seq[(String, Boolean, MustacheTmpl[TemplateData.EventInfo])] =
        defaultTemplates.toSeq.map { case (id, t) => (id, true, t) } ++
          templates.toSeq.map { case (id, t) => (id, false, t) }.sortBy(_._1)

      def getTemplate(id: String): Option[MustacheTmpl[TemplateData.EventInfo]] =
        defaultTemplates.get(id).orElse(templates.get(id))

      def removeTemplate(id: String): Try[Event] =
        if (templates.contains(id)) Success(copy(templates = templates - id))
        else if (Event.defaultTmplIds.contains(id)) Failure(new IllegalArgumentException(s"Template '$id' is a default one, unable to remove it"))
        else Failure(new IllegalArgumentException(s"Template '$id' does not exists, unable to remove it"))

      def addTemplate(id: String, tmpl: MustacheTextTmpl[TemplateData.EventInfo]): Try[Event] =
        if (templates.contains(id) || Event.defaultTmplIds.contains(id)) Failure(new IllegalArgumentException(s"Template '$id' already exists, unable to add it"))
        else Success(copy(templates = templates ++ Map(id -> tmpl)))

      def updateTemplate(oldId: String, newId: String, tmpl: MustacheTmpl[TemplateData.EventInfo]): Try[Event] =
        if (newId == Event.descriptionTmplId) Success(copy(description = tmpl.asMarkdown))
        else removeTemplate(oldId).mapFailure(e => new IllegalArgumentException(s"Template '$oldId' does not exists, unable to update it", e))
          .flatMap(_.addTemplate(newId, tmpl.asText).mapFailure(e => new IllegalArgumentException(s"Template '$newId' already exists, unable to rename to it", e)))
    }

    object Event {
      val descriptionTmplId = "Event description"

      val defaultTmplIds: Seq[String] = Seq(descriptionTmplId)
    }

  }

}
