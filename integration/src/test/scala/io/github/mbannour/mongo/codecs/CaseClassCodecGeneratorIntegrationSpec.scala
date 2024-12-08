package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.github.mbannour.bson.macros.CodecRegistryManager
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.mongodb.scala.bson.annotations.BsonProperty

import java.time.{ZoneId, ZonedDateTime}
import scala.jdk.CollectionConverters.*

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

class ZonedDateTimeCodec extends Codec[ZonedDateTime]:
  override def encode(writer: BsonWriter, value: ZonedDateTime, encoderContext: EncoderContext): Unit =
    writer.writeDateTime(value.toInstant.toEpochMilli)

  override def decode(reader: BsonReader, decoderContext: DecoderContext): ZonedDateTime =
    ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(reader.readDateTime()), ZoneId.systemDefault())

  override def getEncoderClass: Class[ZonedDateTime] = classOf[ZonedDateTime]

class CaseClassCodecGeneratorIntegrationSpec
  extends AnyFlatSpec
    with ForAllTestContainer
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll:

  override val container: MongoDBContainer = MongoDBContainer("mongo:6.0.19")

  "CaseClassCodecGenerator" should "handle nested case classes and optional fields with custom codecs" in {
    assert(container.container.isRunning, "The MongoDB container is not running!")

    val mongoUri = s"mongodb://${container.containerIpAddress}:${container.mappedPort(27017)}"
    val mongoClient: MongoClient = MongoClient(mongoUri)
    val database: MongoDatabase = mongoClient.getDatabase("test_db")

    // Generate and register codecs
    val addressCodec = CaseClassCodecGenerator.generateCodec[Address](encodeNone = true)
    val personCodec = CaseClassCodecGenerator.generateCodec[Person](encodeNone = true)
    CodecRegistryManager.addCodecRegistries(
      List(
        CodecRegistries.fromCodecs(addressCodec, personCodec, new ZonedDateTimeCodec),
        MongoClient.DEFAULT_CODEC_REGISTRY
      )
    )

    // Attach codecs to the database
    val consolidatedRegistry = CodecRegistryManager.getCombinedCodecRegistry
    val customDatabase: MongoDatabase = database.withCodecRegistry(consolidatedRegistry)
    val collection: MongoCollection[Person] = customDatabase.getCollection("people")

    // Insert and retrieve a document
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
    val retrievedPerson = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrievedPerson shouldBe person

    // Insert and retrieve a document without optional fields
    val personWithoutMiddleName = person.copy(middleName = None, _id = new ObjectId())
    collection.insertOne(personWithoutMiddleName).toFuture().futureValue
    val retrievedPersonWithoutMiddleName =
      collection.find(Filters.eq("_id", personWithoutMiddleName._id)).first().toFuture().futureValue
    retrievedPersonWithoutMiddleName shouldBe personWithoutMiddleName

    // Clean up
    customDatabase.drop().toFuture().futureValue
    mongoClient.close()
  }

  override def afterAll(): Unit = container.stop()
