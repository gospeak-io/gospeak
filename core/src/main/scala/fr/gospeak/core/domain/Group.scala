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
        twitter = None),
      events = Map())

    final case class Accounts(slack: Option[SlackCredentials],
                              meetup: Option[String],
                              twitter: Option[String])

    object Events {

      sealed trait Event

      object Event {

        case object OnEventCreated extends Event

        case object OnEventAddTalk extends Event

        case object OnEventRemoveTalk extends Event

        case object OnEventVenueChange extends Event

        case object OnEventPublish extends Event

        case object OnProposalCreated extends Event

        val all: Seq[Event] = Seq(OnEventCreated, OnEventAddTalk, OnEventRemoveTalk, OnEventVenueChange, OnEventPublish, OnProposalCreated)
      }

      sealed trait Action

      object Action {

        final case class Slack(value: SlackAction) extends Action

      }

    }

  }

}
