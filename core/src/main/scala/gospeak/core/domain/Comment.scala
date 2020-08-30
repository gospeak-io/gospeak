package gospeak.core.domain

import java.time.Instant

import gospeak.libs.scala.domain._

case class Comment(id: Comment.Id,
                   kind: Comment.Kind,
                   answers: Option[Comment.Id],
                   text: String,
                   createdAt: Instant,
                   createdBy: User.Id) {
  def data: Comment.Data = Comment.Data(this)
}

object Comment {
  def create(d: Data, kind: Kind, by: User.Id, now: Instant): Comment =
    Comment(Id.generate(), kind, d.answers, d.text, now, by)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Comment.Id", new Id(_))

  sealed trait Kind extends StringEnum {
    override def value: String = toString
  }

  object Kind extends EnumBuilder[Kind]("Comment.Kind") {

    case object Event extends Kind

    case object Proposal extends Kind

    case object ProposalOrga extends Kind

    override val all: List[Kind] = List(Event, Proposal, ProposalOrga)
  }

  final case class Full(comment: Comment, createdBy: User) {
    def id: Id = comment.id

    def answers: Option[Id] = comment.answers

    def text: String = comment.text

    def createdAt: Instant = comment.createdAt
  }

  final case class Data(answers: Option[Comment.Id],
                        text: String)

  object Data {
    def apply(c: Comment): Data = new Data(c.answers, c.text)
  }

}
