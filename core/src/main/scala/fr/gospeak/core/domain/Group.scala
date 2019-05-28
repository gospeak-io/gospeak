package fr.gospeak.core.domain

import java.time.Instant

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.{Info, TemplateData}
import fr.gospeak.core.services.slack.domain.{SlackAction, SlackCredentials}
import fr.gospeak.libs.scalautils.domain._

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
                            actions: Map[Settings.Action.Trigger, Seq[Settings.Action]],
                            event: Settings.Event) {
    def set(slack: SlackCredentials): Settings = copy(accounts = accounts.copy(slack = Some(slack)))
  }

  object Settings {
    val default = Settings(
      accounts = Accounts(
        slack = None,
        meetup = None,
        twitter = None,
        youtube = None),
      actions = Map(),
      event = Event(TemplateData.Static.defaultEventDescription))

    final case class Accounts(slack: Option[SlackCredentials],
                              meetup: Option[String],
                              twitter: Option[String],
                              youtube: Option[String])

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

    final case class Event(defaultDescription: MarkdownTemplate[TemplateData.EventInfo])

  }

}
