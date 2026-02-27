package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import org.bson.types.ObjectId
import org.mongodb.scala.*
import org.mongodb.scala.model.{Filters, Projections}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import RegistryBuilder.{*, given}

/** Integration tests for MongoDB projection with typed codecs.
  *
  * Projections are a very common MongoDB pattern: instead of fetching the whole document, only a subset of fields is
  * returned. These tests document what happens when the projected result is decoded into a case class:
  *
  *   - Projecting out a required field causes a decode failure.
  *   - Projecting into a separate, smaller case class succeeds cleanly.
  *   - Projecting out an optional field decodes as None.
  *   - Projecting in a nested field works correctly.
  */
class ProjectionIntegrationSpec
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

  private def db: MongoDatabase =
    MongoClient(mongoUri).getDatabase("projection_test")

  // ── Models ───────────────────────────────────────────────────────────────────

  // Full document shape stored in MongoDB
  case class FullUser(_id: ObjectId, name: String, age: Int, email: String)

  // Smaller projection target — only the fields we care about
  case class UserSummary(_id: ObjectId, name: String)

  // Document with an optional field
  case class UserWithOptEmail(_id: ObjectId, name: String, email: Option[String])

  // Nested document
  case class Address(city: String, country: String)
  case class PersonWithAddress(_id: ObjectId, name: String, address: Address)

  // Projection target for nested: only top-level + one nested field
  case class PersonCityView(_id: ObjectId, name: String, address: Address)

  // ── Tests ────────────────────────────────────────────────────────────────────

  "Projection: excluding a required field" should
    "fail to decode because the required field is absent from the returned document" in {
      assert(container.container.isRunning)

      val fullRegistry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[FullUser]
        .build

      val database = db.withCodecRegistry(fullRegistry)
      val coll     = database.getCollection[FullUser]("users_required_projection")

      val user = FullUser(new ObjectId(), "Alice", 30, "alice@example.com")
      coll.insertOne(user).toFuture().futureValue

      // Exclude "age" — but FullUser.age is a required Int field
      val ex = coll
        .find(Filters.equal("_id", user._id))
        .projection(Projections.exclude("age"))
        .first()
        .toFuture()
        .failed
        .futureValue

      ex shouldBe a[Exception]

      database.drop().toFuture().futureValue
    }

  "Projection: decoding into a smaller case class" should
    "succeed when the projection exactly covers the target case class fields" in {
      assert(container.container.isRunning)

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[FullUser]
        .register[UserSummary]
        .build

      val database  = db.withCodecRegistry(registry)
      val insertCol = database.getCollection[FullUser]("users_summary_projection")
      val readCol   = database.getCollection[UserSummary]("users_summary_projection")

      val user = FullUser(new ObjectId(), "Bob", 25, "bob@example.com")
      insertCol.insertOne(user).toFuture().futureValue

      // Project only _id and name — matches UserSummary exactly
      val result = readCol
        .find(Filters.equal("_id", user._id))
        .projection(Projections.include("_id", "name"))
        .first()
        .toFuture()
        .futureValue

      result._id shouldBe user._id
      result.name shouldBe "Bob"

      database.drop().toFuture().futureValue
    }

  "Projection: excluding an optional field" should
    "decode successfully with the optional field set to None" in {
      assert(container.container.isRunning)

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .ignoreNone
        .register[UserWithOptEmail]
        .build

      val database = db.withCodecRegistry(registry)
      val coll     = database.getCollection[UserWithOptEmail]("users_opt_projection")

      val user = UserWithOptEmail(new ObjectId(), "Carol", Some("carol@example.com"))
      coll.insertOne(user).toFuture().futureValue

      // Exclude "email" — it is Option[String], so absence should decode as None
      val result = coll
        .find(Filters.equal("_id", user._id))
        .projection(Projections.exclude("email"))
        .first()
        .toFuture()
        .futureValue

      result.name shouldBe "Carol"
      result.email shouldBe None

      database.drop().toFuture().futureValue
    }

  "Projection: fetching only nested sub-document fields" should
    "decode correctly when nested fields are included in the projection" in {
      assert(container.container.isRunning)

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[Address]
        .register[PersonWithAddress]
        .register[PersonCityView]
        .build

      val database  = db.withCodecRegistry(registry)
      val insertCol = database.getCollection[PersonWithAddress]("persons_nested_projection")
      val readCol   = database.getCollection[PersonCityView]("persons_nested_projection")

      val person = PersonWithAddress(new ObjectId(), "Dave", Address("Paris", "France"))
      insertCol.insertOne(person).toFuture().futureValue

      // Include _id, name, and the full address sub-document
      val result = readCol
        .find(Filters.equal("_id", person._id))
        .projection(Projections.include("_id", "name", "address"))
        .first()
        .toFuture()
        .futureValue

      result.name shouldBe "Dave"
      result.address.city shouldBe "Paris"
      result.address.country shouldBe "France"

      database.drop().toFuture().futureValue
    }

  "Projection: fetching multiple documents with partial fields" should
    "decode all documents correctly using a summary view" in {
      assert(container.container.isRunning)

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[FullUser]
        .register[UserSummary]
        .build

      val database  = db.withCodecRegistry(registry)
      val insertCol = database.getCollection[FullUser]("users_multi_projection")
      val readCol   = database.getCollection[UserSummary]("users_multi_projection")

      val users = Seq(
        FullUser(new ObjectId(), "Eve", 28, "eve@example.com"),
        FullUser(new ObjectId(), "Frank", 35, "frank@example.com"),
        FullUser(new ObjectId(), "Grace", 22, "grace@example.com")
      )
      insertCol.insertMany(users).toFuture().futureValue

      val results = readCol
        .find()
        .projection(Projections.include("_id", "name"))
        .toFuture()
        .futureValue

      results should have size 3
      results.map(_.name) should contain allOf ("Eve", "Frank", "Grace")

      database.drop().toFuture().futureValue
    }

  override def afterAll(): Unit =
    container.stop()

end ProjectionIntegrationSpec
