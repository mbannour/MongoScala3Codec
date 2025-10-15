package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.BeforeAndAfterAll
import RegistryBuilder.{*, given}

/** Integration tests that write/read a nested case class and an ADT (modeled via concrete case classes)
  * using the MongoDB Scala driver and Testcontainers.
  */
class AdtIntegrationSpec
    extends AnyFlatSpec
    with ForAllTestContainer
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll:

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  override val container: MongoDBContainer = MongoDBContainer("mongo:6.0.19")

  private lazy val mongoUri: String =
    s"mongodb://${container.containerIpAddress}:${container.mappedPort(27017)}"

  private def dbWith(registry: CodecRegistry): MongoDatabase =
    MongoClient(mongoUri).getDatabase("m1_test").withCodecRegistry(registry)

  // Nested case class model
  case class Address(street: String, city: String, zipCode: Int)
  case class User(_id: ObjectId, name: String, address: Option[Address])

  // Simple ADT via concrete classes with discriminator field
  sealed trait Shape
  case class Circle(_id: ObjectId, radius: Double, shapeType: String = "Circle") extends Shape
  case class Rectangle(_id: ObjectId, width: Double, height: Double, shapeType: String = "Rectangle") extends Shape

  "Mongo driver" should "write/read a nested case class" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[Address]
      .register[User]
      .build

    val db = dbWith(registry)
    val coll: MongoCollection[User] = db.getCollection("users_nested")

    val u = User(new ObjectId(), "Alice", Some(Address("123 Main St", "Springfield", 12345)))

    coll.insertOne(u).toFuture().futureValue
    val fetched = coll.find(Filters.equal("_id", u._id)).first().toFuture().futureValue

    fetched shouldBe u
  }

  it should "write/read two concrete ADT variants" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[Circle]
      .register[Rectangle]
      .build

    val db = dbWith(registry)

    val circles: MongoCollection[Circle] = db.getCollection("circles")
    val rectangles: MongoCollection[Rectangle] = db.getCollection("rectangles")

    val c = Circle(new ObjectId(), 5.0)
    val r = Rectangle(new ObjectId(), 2.0, 3.0)

    circles.insertOne(c).toFuture().futureValue
    rectangles.insertOne(r).toFuture().futureValue

    val fetchedC = circles.find(Filters.equal("_id", c._id)).first().toFuture().futureValue
    val fetchedR = rectangles.find(Filters.equal("_id", r._id)).first().toFuture().futureValue

    fetchedC shouldBe c
    fetchedR shouldBe r
    fetchedC.shapeType shouldBe "Circle"
    fetchedR.shapeType shouldBe "Rectangle"
  }

