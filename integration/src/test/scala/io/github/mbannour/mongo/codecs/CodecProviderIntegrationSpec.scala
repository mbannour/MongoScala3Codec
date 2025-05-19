package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import io.github.mbannour.mongo.codecs.models.*
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.bson.codecs.configuration.CodecRegistry
import DefaultCodecRegistries.*
import org.bson.types.ObjectId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.ZonedDateTime
import scala.concurrent.Future

class CodecProviderIntegrationSpec extends AnyFlatSpec with ForAllTestContainer with Matchers with ScalaFutures with BeforeAndAfterAll:

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  override val container: MongoDBContainer = MongoDBContainer("mongo:6.0.19")

  private lazy val mongoUri: String =
    s"mongodb://${container.containerIpAddress}:${container.mappedPort(27017)}"

  private def createDatabaseWithRegistry(registry: CodecRegistry): MongoDatabase =
    MongoClient(mongoUri)
      .getDatabase("test_db")
      .withCodecRegistry(registry)

  "CaseClassCodecGenerator" should "handle nested case classes and optional fields with custom codecs" in {
    assert(container.container.isRunning, "The MongoDB container is not running!")

    val database: MongoDatabase = MongoClient(mongoUri)
      .getDatabase("test_db")
      .withCodecRegistry(
        DefaultCodecRegistries.defaultRegistry
      )

    val collection: MongoCollection[Person] = database.getCollection("people")

    val person = Person(
      _id = new ObjectId(),
      name = "Alice",
      employeeId = Map("E1" -> EmployeeId()),
      middleName = Some("Marie"),
      age = 30,
      height = 5.6,
      married = true,
      address = Some(Address("123 Main St", "Wonderland", 12345, EmployeeId())),
      nicknames = Seq("Ally", "Lissie")
    )

    collection.insertOne(person).toFuture().futureValue

    val retrievedPerson =
      collection
        .find(Filters.and(Filters.equal(
          PersonFields.id, person._id), Filters.equal(
          PersonFields.address.zipCode, 12345))
        )
        .first()
        .toFuture()
        .futureValue
    retrievedPerson shouldBe person

    val personWithoutMiddleName = person.copy(middleName = None, _id = new ObjectId())
    collection.insertOne(personWithoutMiddleName).toFuture().futureValue

    val retrievedPersonWithoutMiddleName =
      collection.find(Filters.equal("_id", personWithoutMiddleName._id)).first().toFuture().futureValue

    retrievedPersonWithoutMiddleName shouldBe personWithoutMiddleName

    database.drop().toFuture().futureValue
  }

  it should "handle empty collections and missing nested case class fields" in {

    val database: MongoDatabase = createDatabaseWithRegistry(DefaultCodecRegistries.defaultRegistry)
    val collection: MongoCollection[Person] = database.getCollection("people")

    val person = Person(
      _id = new ObjectId(),
      name = "Bob",
      employeeId = Map("E1" -> EmployeeId()),
      middleName = None,
      age = 25,
      height = 5.9,
      married = false,
      address = None,
      nicknames = Seq.empty
    )

    collection.insertOne(person).toFuture().futureValue
    val retrieved =
      collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  it should "handle custom codecs such as ZonedDateTime" in {

    val database: MongoDatabase = createDatabaseWithRegistry(Event.eventRegistry)
    val collection: MongoCollection[Event] = database.getCollection("events")

    val event = Event(
      _id = new ObjectId(),
      title = "Conference",
      time = ZonedDateTime.now()
    )

    collection.insertOne(event).toFuture().futureValue
    val retrievedEvent =
      collection.find(Filters.equal("_id", event._id)).first().toFuture().futureValue
    retrievedEvent._id shouldBe event._id

    database.drop().toFuture().futureValue
  }

  it should "handle nested collections (e.g., a company with employees)" in {

    val database: MongoDatabase = createDatabaseWithRegistry(Company.defaultRegistry)

    val collection: MongoCollection[Company] = database.getCollection("companies")

    val employee1 = Person(
      _id = new ObjectId(),
      name = "Alice",
      employeeId = Map("E1" -> EmployeeId()),
      middleName = Some("Marie"),
      age = 30,
      height = 5.6,
      married = true,
      address = Some(Address("123 Main St", "Wonderland", 12345, EmployeeId())),
      nicknames = Seq("Ally")
    )
    val employee2 = Person(
      _id = new ObjectId(),
      name = "Bob",
      employeeId = Map("E1" -> EmployeeId()),
      middleName = None,
      age = 28,
      height = 5.8,
      married = false,
      address = None,
      nicknames = Seq("Bobby", "Rob")
    )
    val company = Company("TechCorp", Some(Seq(employee1, employee2)))

    collection.insertOne(company).toFuture().futureValue
    val retrievedCompany =
      collection.find(Filters.equal("name", "TechCorp")).first().toFuture().futureValue
    retrievedCompany shouldBe company

    database.drop().toFuture().futureValue
  }

  it should "handle Scala Enumeration fields in case classes" in {

    val database: MongoDatabase = createDatabaseWithRegistry(Task.defaultRegistry)
    val collection: MongoCollection[Task] = database.getCollection("tasks")

    val task = Task(
      _id = new ObjectId(),
      title = "Complete report",
      priority = Priority.High
    )

    collection.insertOne(task).toFuture().futureValue
    val retrievedTask =
      collection.find(Filters.and(Filters.equal("_id", task._id), Filters.equal("priority", Priority.High))).first().toFuture().futureValue
    retrievedTask shouldBe task

    database.drop().toFuture().futureValue
  }

  it should "handle high concurrency loads" in {

    val database: MongoDatabase = createDatabaseWithRegistry(DefaultCodecRegistries.defaultRegistry)
    val collection: MongoCollection[Person] = database.getCollection("concurrent_people")

    val numDocs = 1000
    val persons: Seq[Person] = (1 to numDocs).map { i =>
      Person(
        _id = new ObjectId(),
        name = s"Person $i",
        employeeId = Map("E1" -> EmployeeId()),
        middleName = if i % 2 == 0 then Some(s"Middle $i") else None,
        age = 20 + (i % 30),
        height = 5.0 + (i % 10) * 0.1,
        married = i % 2 == 0,
        address = if i % 3 == 0 then Some(Address(s"$i Main St", s"City $i", 10000 + i, EmployeeId())) else None,
        nicknames = if i % 5 == 0 then Seq(s"nick$i", s"alias$i") else Seq.empty
      )
    }

    import scala.concurrent.ExecutionContext.Implicits.global

    val insertStartTime = System.nanoTime()
    val insertFutures = persons.map { person =>
      collection.insertOne(person).toFuture()
    }
    Future.sequence(insertFutures).futureValue
    val insertDurationMs = (System.nanoTime() - insertStartTime) / 1e6
    info(s"Inserted $numDocs documents concurrently in $insertDurationMs ms")

    val retrievalStartTime = System.nanoTime()
    val retrievalFutures = persons.map { person =>
      collection.find(Filters.equal("_id", person._id)).first().toFuture()
    }
    val retrievedPersons = Future.sequence(retrievalFutures).futureValue
    val retrievalDurationMs = (System.nanoTime() - retrievalStartTime) / 1e6
    info(s"Retrieved $numDocs documents concurrently in $retrievalDurationMs ms")

    retrievedPersons should contain theSameElementsAs persons
    database.drop().toFuture().futureValue
  }

  override def afterAll(): Unit =
    container.stop()
end CodecProviderIntegrationSpec
