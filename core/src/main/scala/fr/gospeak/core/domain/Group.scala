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
                       description: Markdown,
                       owners: NonEmptyList[User.Id],
                       tags: Seq[Tag],
                       published: Option[Instant],
                       info: Info) {
  def data: Group.Data = Group.Data(this)

  def isPublic: Boolean = published.isDefined
}

object Group {
  def apply(data: Data, owners: NonEmptyList[User.Id], info: Info): Group =
    new Group(Id.generate(), data.slug, data.name, data.description, owners, data.tags, None, info) // FIXME add public in form

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Group.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Group.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Group.Slug,
                        name: Group.Name,
                        description: Markdown,
                        tags: Seq[Tag])

  object Data {
    def apply(group: Group): Data = new Data(group.slug, group.name, group.description, group.tags)
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
    val default = Settings(
      accounts = Accounts(
        meetup = None,
        slack = None),
        // twitter = None,
        // youtube = None),
      event = Event(
        defaultDescription = TemplateData.Static.defaultEventDescription,
        templates = Map()),
      actions = Map())

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

    final case class Event(defaultDescription: MustacheMarkdownTmpl[TemplateData.EventInfo],
                           templates: Map[String, MustacheTextTmpl[TemplateData.EventInfo]]) {
      def defaultTemplates: Seq[(String, MustacheMarkdownTmpl[TemplateData.EventInfo])] =
        Seq((Event.defaultDescriptionId, defaultDescription))

      def allTemplates: Seq[(String, Boolean, MustacheTmpl[TemplateData.EventInfo])] =
        defaultTemplates.map { case (id, t) => (id, true, t) } ++
          templates.toSeq.map { case (id, t) => (id, false, t) }.sortBy(_._1)

      def getTemplate(id: String): Option[MustacheTmpl[TemplateData.EventInfo]] =
        if (id == Event.defaultDescriptionId) Some(defaultDescription)
        else templates.get(id)

      def removeTemplate(id: String): Try[Event] =
        if (templates.contains(id)) Success(copy(templates = templates - id))
        else if (id == Event.defaultDescriptionId) Failure(new IllegalArgumentException(s"Template '$id' is a default one, unable to remove it"))
        else Failure(new IllegalArgumentException(s"Template '$id' does not exists, unable to remove it"))

      def addTemplate(id: String, tmpl: MustacheTextTmpl[TemplateData.EventInfo]): Try[Event] =
        if (templates.contains(id) || id == Event.defaultDescriptionId) Failure(new IllegalArgumentException(s"Template '$id' already exists, unable to add it"))
        else Success(copy(templates = templates ++ Map(id -> tmpl)))

      def updateTemplate(oldId: String, newId: String, tmpl: MustacheMarkdownTmpl[TemplateData.EventInfo]): Try[Event] =
        if (newId == Event.defaultDescriptionId) Success(copy(defaultDescription = tmpl))
        else removeTemplate(oldId).mapFailure(e => new IllegalArgumentException(s"Template '$oldId' does not exists, unable to update it", e))
          .flatMap(_.addTemplate(newId, tmpl.asText).mapFailure(e => new IllegalArgumentException(s"Template '$newId' already exists, unable to rename to it", e)))
    }

    object Event {
      val defaultDescriptionId = "Default description"
    }

  }

}
