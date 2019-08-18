package fr.gospeak.migration

import fr.gospeak.migration.domain._
import fr.gospeak.migration.domain.utils.{Coords, GMapPlace, MeetupRef, Meta}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{Cursor, DefaultDB, MongoConnection, MongoDriver}
import reactivemongo.bson.{BSONDocumentReader, Macros, document}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

class MongoRepo(connection: MongoConnection, dbName: String) extends AutoCloseable {
  private def db: Future[DefaultDB] = connection.database(dbName)

  private def personColl: Future[BSONCollection] = db.map(_.collection[BSONCollection]("Person"))

  private def eventColl: Future[BSONCollection] = db.map(_.collection[BSONCollection]("Event"))

  private def talkColl: Future[BSONCollection] = db.map(_.collection[BSONCollection]("Talk"))

  private def partnerColl: Future[BSONCollection] = db.map(_.collection[BSONCollection]("Partner"))

  /*
    TODO "Config" collection: templates for
      - proposal.submitted.email.subject
      - proposal.submitted.email.content
      - proposal.submitted.slack.title
      - proposal.submitted.slack.message
      - proposal.submitted.slack.text
      - meetup.event.description
      - meetup.created.slack.message
      - talk.added.to.meetup.slack.message
   */

  private implicit val metaReader: BSONDocumentReader[Meta] = Macros.reader[Meta]
  private implicit val meetupRefReader: BSONDocumentReader[MeetupRef] = BSONDocumentReader[MeetupRef](bson => (for {
    group <- bson.getAsTry[String]("group")
    id <- bson.getAsTry[Long]("id")
      .orElse(bson.getAsTry[Int]("id").map(_.toLong))
      .orElse(bson.getAsTry[Double]("id").map(_.toLong))
  } yield MeetupRef(group, id)).get)
  private implicit val coordsReader: BSONDocumentReader[Coords] = Macros.reader[Coords]
  private implicit val gMapPlaceReader: BSONDocumentReader[GMapPlace] = Macros.reader[GMapPlace]

  private implicit val loginInfoReader: BSONDocumentReader[LoginInfo] = Macros.reader[LoginInfo]
  private implicit val personAuthReader: BSONDocumentReader[PersonAuth] = Macros.reader[PersonAuth]
  private implicit val personDataReader: BSONDocumentReader[PersonData] = Macros.reader[PersonData]
  private implicit val personReader: BSONDocumentReader[Person] = Macros.reader[Person]

  private implicit val eventDataReader: BSONDocumentReader[EventData] = Macros.reader[EventData]
  private implicit val eventReader: BSONDocumentReader[Event] = Macros.reader[Event]

  private implicit val proposalReader: BSONDocumentReader[Proposal] = Macros.reader[Proposal]
  private implicit val talkDataReader: BSONDocumentReader[TalkData] = Macros.reader[TalkData]
  private implicit val talkReader: BSONDocumentReader[Talk] = Macros.reader[Talk]

  private implicit val sponsorReader: BSONDocumentReader[Sponsor] = Macros.reader[Sponsor]
  private implicit val venueReader: BSONDocumentReader[Venue] = BSONDocumentReader[Venue](bson => (for {
    location <- bson.getAsTry[GMapPlace]("location")
    capacity = bson.getAsTry[Int]("capacity")
      .orElse(bson.getAsTry[Long]("capacity").map(_.toInt))
      .orElse(bson.getAsTry[Double]("capacity").map(_.toInt))
      .toOption
    closeTime = bson.getAs[String]("closeTime")
    attendeeList = bson.getAs[Boolean]("attendeeList")
    entranceCheck = bson.getAs[Boolean]("entranceCheck")
    offeredAperitif = bson.getAs[Boolean]("offeredAperitif")
    contact = bson.getAs[String]("contact")
    comment = bson.getAs[String]("comment")
  } yield Venue(location, capacity, closeTime, attendeeList, entranceCheck, offeredAperitif, contact, comment)).get)
  private implicit val partnerDataReader: BSONDocumentReader[PartnerData] = Macros.reader[PartnerData]
  private implicit val partnerReader: BSONDocumentReader[Partner] = Macros.reader[Partner]

  def loadPersons(): Future[List[Person]] =
    personColl.flatMap(_.find(document(), None).cursor[Person]().collect[List](-1, Cursor.FailOnError[List[Person]]()))

  def loadEvents(): Future[List[Event]] =
    eventColl.flatMap(_.find(document(), None).cursor[Event]().collect[List](-1, Cursor.FailOnError[List[Event]]()))

  def loadTalks(): Future[List[Talk]] =
    talkColl.flatMap(_.find(document(), None).cursor[Talk]().collect[List](-1, Cursor.FailOnError[List[Talk]]()))

  def loadPartners(): Future[List[Partner]] =
    partnerColl.flatMap(_.find(document(), None).cursor[Partner]().collect[List](-1, Cursor.FailOnError[List[Partner]]()))

  override def close(): Unit = {
    Await.result(connection.actorSystem.terminate(), Duration.Inf)
  }
}

object MongoRepo {

  def create(mongoUri: String, dbName: String): Try[MongoRepo] = {
    val driver = MongoDriver()
    for {
      parsedUri <- MongoConnection.parseURI(mongoUri)
      connection <- Try(driver.connection(parsedUri))
    } yield new MongoRepo(connection, dbName)
  }

}
