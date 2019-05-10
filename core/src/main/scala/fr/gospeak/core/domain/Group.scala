package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.slack.domain.{SlackAction, SlackCredentials}
import fr.gospeak.libs.scalautils.domain._

final case class Group(id: Group.Id,
                       slug: Group.Slug,
                       name: Group.Name,
                       description: Markdown,
                       owners: NonEmptyList[User.Id],
                       public: Boolean,
                       tags: Seq[Tag],
                       info: Info) {
  def data: Group.Data = Group.Data(this)
}

object Group {
  def apply(data: Data, owners: NonEmptyList[User.Id], info: Info): Group =
    new Group(Id.generate(), data.slug, data.name, data.description, owners, true, data.tags, info) // FIXME add public in form

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
                            events: Map[Settings.Events.Event, Seq[Settings.Events.Action]]) {
    def set(slack: SlackCredentials): Settings = copy(accounts = accounts.copy(slack = Some(slack)))

    def verify: Either[Seq[String], Settings] = {
      val actions = this.events.values.flatten
      if (accounts.slack.isEmpty && actions.exists { case _: Settings.Events.Action.Slack => true }) {
        Left(Seq("Some Slack actions are defined but no Slack account"))
      } else {
        Right(this)
      }
    }
  }

  object Settings {
    val default = Settings(
      accounts = Accounts(
        slack = None,
        meetup = None,
        twitter = None,
        youtube = None),
      events = Map())

    final case class Accounts(slack: Option[SlackCredentials],
                              meetup: Option[String],
                              twitter: Option[String],
                              youtube: Option[String])

    object Events {

      sealed abstract class Event(val name: String)

      object Event {

        case object OnEventCreated extends Event("When an Event is created")

        case object OnEventAddTalk extends Event("When a Talk is added to an Event")

        case object OnEventRemoveTalk extends Event("When a Talk is removed from an Event")

        case object OnEventPublish extends Event("When an Event is published")

        case object OnProposalCreated extends Event("When a Proposal is submitted to a CFP")

        val all: Seq[Event] = Seq(OnEventCreated, OnEventAddTalk, OnEventRemoveTalk, OnEventPublish, OnProposalCreated)

        def from(str: String): Option[Event] = all.find(_.toString == str)
      }

      sealed trait Action

      object Action {

        final case class Slack(value: SlackAction) extends Action

      }

    }

  }

}
