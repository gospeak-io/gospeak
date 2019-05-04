package fr.gospeak.infra.services.slack.api

import java.time.Instant

// cf https://api.slack.com/types/channel
final case class SlackChannel(id: SlackChannel.Id,
                              name: SlackChannel.Name,
                              is_channel: Boolean,
                              created: Instant,
                              creator: SlackUser.Id,
                              is_archived: Boolean,
                              is_general: Boolean,
                              name_normalized: String,
                              is_shared: Boolean,
                              is_org_shared: Boolean,
                              is_member: Boolean,
                              is_private: Boolean,
                              is_mpim: Boolean,
                              members: Seq[SlackUser.Id],
                              topic: SlackChannel.Topic,
                              purpose: SlackChannel.Purpose,
                              num_members: Option[Int],
                              previous_names: Seq[String])

object SlackChannel {

  sealed trait Ref {
    val value: String
  }

  final case class Id(value: String) extends Ref

  final case class Name(value: String) extends Ref

  final case class Topic(value: String,
                         creator: SlackUser.Id,
                         last_set: Instant)

  final case class Purpose(value: String,
                           creator: SlackUser.Id,
                           last_set: Instant)

  final case class List(channels: Seq[SlackChannel],
                        ok: Boolean)

  final case class Single(channel: SlackChannel,
                          ok: Boolean)

}
