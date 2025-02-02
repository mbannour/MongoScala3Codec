package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
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
import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.temporal.ChronoUnit
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

// --- Domain Case Classes ---

case class Address(street: String, city: String, zipCode: Int)

case class Person(
    _id: ObjectId,
    @BsonProperty("n") name: String,
    middleName: Option[String],
    age: Int,
    height: Double,
    married: Boolean,
    address: Option[Address],
    nicknames: Seq[String]
)

case class Event(_id: ObjectId, title: String, time: ZonedDateTime)

case class Company(name: String, employees: Option[Seq[Person]])

// Scala Enumeration for Priority
object Priority extends Enumeration:
  type Priority = Value
  val Low, Medium, High = Value

// A case class that uses the Scala Enumeration
case class Task(_id: ObjectId, title: String, priority: Priority.Value)

// --- Custom Codec for ZonedDateTime ---
class ZonedDateTimeCodec extends Codec[ZonedDateTime]:
  override def encode(writer: BsonWriter, value: ZonedDateTime, encoderContext: EncoderContext): Unit =
    // Truncate the value to milliseconds before encoding.
    val truncatedValue = value.truncatedTo(ChronoUnit.MILLIS)
    writer.writeDateTime(truncatedValue.toInstant.toEpochMilli)

    val codecWithNone = CaseClassCodec.generateCodecEncodeNone[Person]

  override def decode(reader: BsonReader, decoderContext: DecoderContext): ZonedDateTime =
    ZonedDateTime.ofInstant(
      java.time.Instant.ofEpochMilli(reader.readDateTime()),
      ZoneId.systemDefault()
    )
  override def getEncoderClass: Class[ZonedDateTime] = classOf[ZonedDateTime]
end ZonedDateTimeCodec

object PriorityCodec extends Codec[Priority.Value] {
  override def encode(writer: BsonWriter, value: Priority.Value, encoderContext: EncoderContext): Unit =
    writer.writeString(value.toString)

  override def decode(reader: BsonReader, decoderContext: DecoderContext): Priority.Value =
    val str = reader.readString()
    try Priority.withName(str)
    catch {
      case ex: NoSuchElementException =>
        throw new IllegalArgumentException(s"Unknown Priority value: '$str'", ex)
    }

  override def getEncoderClass: Class[Priority.Value] = classOf[Priority.Value]
}

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

    val baseRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY

    val addressProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Address](encodeNone = true, baseRegistry)
    val registryWithAddress: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(addressProvider),
        baseRegistry
      )
    val personProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Person](encodeNone = true, registryWithAddress)
    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(personProvider),
        registryWithAddress
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
      address = Some(Address("123 Main St", "Wonderland", 12345)),
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
    val baseRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY

    val addressProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Address](encodeNone = true, baseRegistry)

    val addressRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(addressProvider),
        baseRegistry
      )

    val personProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Person](encodeNone = true, addressRegistry)

    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(personProvider),
        baseRegistry
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
    val baseRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY
    // Create a provider for Event using our macro.
    val eventRegistry: CodecRegistry = CodecRegistries.fromRegistries(
      CodecRegistries.fromCodecs(new ZonedDateTimeCodec),
      baseRegistry
    )
    val eventProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Event](encodeNone = true, eventRegistry)
    // Register the ZonedDateTimeCodec first so it is found.
    val customRegistry: CodecRegistry = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(eventProvider),
      baseRegistry
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
    val baseRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY
    val addressProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Address](encodeNone = true, baseRegistry)

    val personRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(addressProvider),
        baseRegistry
      )
    end personRegistry

    val personProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Person](encodeNone = true, personRegistry)
    end personProvider

    val companyRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(addressProvider, personProvider),
        baseRegistry
      )

    val companyProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Company](encodeNone = true, companyRegistry)
    end companyProvider

    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(companyProvider),
        baseRegistry
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
      address = Some(Address("123 Main St", "Wonderland", 12345)),
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
    val baseRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY
    val taskRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromCodecs(PriorityCodec),
        baseRegistry
      )
    val taskProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Task](encodeNone = true, taskRegistry)

    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(taskProvider),
        baseRegistry
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
    // Set up the base registry and create CodecProviders for Address and Person.
    val baseRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY

    val addressProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Address](encodeNone = true, baseRegistry)
    val addressRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(addressProvider),
        baseRegistry
      )

    val personProvider: CodecProvider =
      CodecProviderMacro.createCodecProvider[Person](encodeNone = true, addressRegistry)
    val combinedRegistry: CodecRegistry =
      CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(personProvider),
        addressRegistry
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
        middleName = if (i % 2 == 0) Some(s"Middle $i") else None,
        age = 20 + (i % 30),
        height = 5.0 + (i % 10) * 0.1,
        married = i % 2 == 0,
        address = if (i % 3 == 0) Some(Address(s"$i Main St", s"City $i", 10000 + i)) else None,
        nicknames = if (i % 5 == 0) Seq(s"nick$i", s"alias$i") else Seq.empty
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
