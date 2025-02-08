package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import io.github.mbannour.mongo.codecs.models.Company.companyRegistry
import io.github.mbannour.mongo.codecs.models.Event.eventRegistry
import io.github.mbannour.mongo.codecs.models.Person.personRegistry
import io.github.mbannour.mongo.codecs.models.Task.taskRegistry
import io.github.mbannour.mongo.codecs.models.{Address, Company, EmployeeId, Event, Person, Priority, Task}
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.types.ObjectId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.{ZonedDateTime}
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

class CaseClassCodecGeneratorIntegrationSpec
    extends AnyFlatSpec
    with ForAllTestContainer
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll:

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(500, Millis))

  override val container: MongoDBContainer = MongoDBContainer("mongo:6.0.19")

  private lazy val mongoUri: String =
    s"mongodb://${container.containerIpAddress}:${container.mappedPort(27017)}"

  private def createDatabaseWithRegistry(registry: CodecRegistry): MongoDatabase =
    MongoClient(mongoUri)
      .getDatabase("test_db")
      .withCodecRegistry(registry)

  "CaseClassCodecGenerator" should "handle nested case classes and optional fields with custom codecs" in {
    assert(container.container.isRunning, "The MongoDB container is not running!")

    val personProvider: CodecProvider =
      CodecProviderMacro.createCodecProviderEncodeNone[Person](personRegistry)

    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(personProvider),
        MongoClient.DEFAULT_CODEC_REGISTRY
      )

    val database: MongoDatabase = createDatabaseWithRegistry(combinedRegistry)
    val collection: MongoCollection[Person] = database.getCollection("people")

    val person = Person(
      _id = new ObjectId(),
      name = "Alice",
      middleName = Some("Marie"),
      age = 30,
      height = 5.6,
      married = true,
      address = Some(Address("123 Main St", "Wonderland", 12345, EmployeeId())),
      nicknames = Seq("Ally", "Lissie")
    )

    collection.insertOne(person).toFuture().futureValue
    val retrievedPerson =
      collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrievedPerson shouldBe person

    val personWithoutMiddleName = person.copy(middleName = None, _id = new ObjectId())
    collection.insertOne(personWithoutMiddleName).toFuture().futureValue
    val retrievedPersonWithoutMiddleName =
      collection.find(Filters.equal("_id", personWithoutMiddleName._id)).first().toFuture().futureValue

    retrievedPersonWithoutMiddleName shouldBe personWithoutMiddleName

    database.drop().toFuture().futureValue
  }

  it should "handle empty collections and missing nested case class fields" in {
    val personProvider: CodecProvider =
      CodecProviderMacro.createCodecProviderEncodeNone[Person](personRegistry)

    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(personProvider),
        MongoClient.DEFAULT_CODEC_REGISTRY
      )

    val database: MongoDatabase = createDatabaseWithRegistry(combinedRegistry)
    val collection: MongoCollection[Person] = database.getCollection("people")

    // Person with empty nicknames and no address.
    val person = Person(
      _id = new ObjectId(),
      name = "Bob",
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

    val eventProvider: CodecProvider =
      CodecProviderMacro.createCodecProviderEncodeNone[Event](eventRegistry)
    // Register the ZonedDateTimeCodec first so it is found.
    val customRegistry: CodecRegistry = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(eventProvider),
      MongoClient.DEFAULT_CODEC_REGISTRY
    )
    val database: MongoDatabase = createDatabaseWithRegistry(customRegistry)
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

    val companyProvider: CodecProvider =
      CodecProviderMacro.createCodecProviderEncodeNone[Company](companyRegistry)
    end companyProvider

    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(companyProvider),
        MongoClient.DEFAULT_CODEC_REGISTRY
      )

    val database: MongoDatabase = createDatabaseWithRegistry(combinedRegistry)

    val collection: MongoCollection[Company] = database.getCollection("companies")

    val employee1 = Person(
      _id = new ObjectId(),
      name = "Alice",
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
    // Define a provider for Task (which has a Scala Enumeration field).

    val taskProvider: CodecProvider =
      CodecProviderMacro.createCodecProviderEncodeNone[Task](taskRegistry)

    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(taskProvider),
        MongoClient.DEFAULT_CODEC_REGISTRY
      )
    val database: MongoDatabase = createDatabaseWithRegistry(combinedRegistry)
    val collection: MongoCollection[Task] = database.getCollection("tasks")

    val task = Task(
      _id = new ObjectId(),
      title = "Complete report",
      priority = Priority.High
    )

    collection.insertOne(task).toFuture().futureValue
    val retrievedTask =
      collection.find(Filters.equal("_id", task._id)).first().toFuture().futureValue
    retrievedTask shouldBe task

    database.drop().toFuture().futureValue
  }

  it should "handle high concurrency loads" in {

    val personProvider: CodecProvider =
      CodecProviderMacro.createCodecProviderEncodeNone[Person](personRegistry)

    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(personProvider),
        MongoClient.DEFAULT_CODEC_REGISTRY
      )

    // Create a database instance using the combined registry.
    val database: MongoDatabase = createDatabaseWithRegistry(combinedRegistry)
    val collection: MongoCollection[Person] = database.getCollection("concurrent_people")

    // Create a large number of Person documents.
    val numDocs = 1000
    val persons: Seq[Person] = (1 to numDocs).map { i =>
      Person(
        _id = new ObjectId(),
        name = s"Person $i",
        middleName = if i % 2 == 0 then Some(s"Middle $i") else None,
        age = 20 + (i % 30),
        height = 5.0 + (i % 10) * 0.1,
        married = i % 2 == 0,
        address = if i % 3 == 0 then Some(Address(s"$i Main St", s"City $i", 10000 + i, EmployeeId())) else None,
        nicknames = if i % 5 == 0 then Seq(s"nick$i", s"alias$i") else Seq.empty
      )
    }

    import scala.concurrent.ExecutionContext.Implicits.global

    // Concurrently insert all documents.
    val insertStartTime = System.nanoTime()
    val insertFutures = persons.map { person =>
      collection.insertOne(person).toFuture()
    }
    Future.sequence(insertFutures).futureValue
    val insertDurationMs = (System.nanoTime() - insertStartTime) / 1e6
    info(s"Inserted $numDocs documents concurrently in $insertDurationMs ms")

    // Concurrently retrieve all documents by _id.
    val retrievalStartTime = System.nanoTime()
    val retrievalFutures = persons.map { person =>
      collection.find(Filters.equal("_id", person._id)).first().toFuture()
    }
    val retrievedPersons = Future.sequence(retrievalFutures).futureValue
    val retrievalDurationMs = (System.nanoTime() - retrievalStartTime) / 1e6
    info(s"Retrieved $numDocs documents concurrently in $retrievalDurationMs ms")

    // Verify that all inserted documents were retrieved correctly.
    retrievedPersons should contain theSameElementsAs persons

    // Clean up the database.
    database.drop().toFuture().futureValue
  }

  override def afterAll(): Unit =
    container.stop()
end CaseClassCodecGeneratorIntegrationSpec
