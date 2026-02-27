package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import org.bson.types.ObjectId
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.mongodb.scala.bson.annotations.BsonProperty

import RegistryBuilder.{*, given}

/** Integration tests for schema evolution scenarios.
  *
  * These tests document exactly what happens when a case class schema changes but old documents already exist in MongoDB.
  * Each test simulates a real-world migration scenario:
  *   - Adding a required field (no default)
  *   - Adding a field with a default value
  *   - Removing a field
  *   - Renaming a field without @BsonProperty (breaking change)
  *   - Renaming a field with @BsonProperty (safe migration)
  *   - Changing a field type (Int -> Long)
  */
class SchemaEvolutionIntegrationSpec
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

  private def rawDatabase: MongoDatabase =
    MongoClient(mongoUri).getDatabase("schema_evolution_test")

  // ── Models ──────────────────────────────────────────────────────────────────

  // V2 adds a required field "age: Int" that old documents don't have
  case class UserV2(_id: ObjectId, name: String, age: Int)

  // V2 with a default value on the new field
  case class UserV2Default(_id: ObjectId, name: String, age: Int = 0)

  // Old schema had "category", V2 removes it
  case class ProductV1(_id: ObjectId, name: String, price: Double, category: String)
  case class ProductV2(_id: ObjectId, name: String, price: Double)

  // Field renamed from "firstName" to "name" WITHOUT @BsonProperty
  case class PersonV2(_id: ObjectId, name: String)

  // Field renamed from "firstName" to "name" WITH @BsonProperty — safe migration
  case class PersonV3(_id: ObjectId, @BsonProperty("firstName") name: String)

  // Field type changed from Int to Long
  case class ScoreV2(_id: ObjectId, value: Long)

  // ── Helpers ─────────────────────────────────────────────────────────────────

  /** Insert a raw Scala document (simulates an old document written by a previous schema version). */
  private def insertRaw(collName: String, doc: Document): Unit =
    rawDatabase.getCollection(collName).insertOne(doc).toFuture().futureValue

  // ── Tests ───────────────────────────────────────────────────────────────────

  "Schema evolution: adding a required field" should
    "fail to decode an old document that is missing the new field" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // Old document: only has "name", no "age"
      insertRaw("users_add_required", Document("_id" -> oid, "name" -> "Alice"))

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[UserV2]
        .build

      val coll = rawDatabase.withCodecRegistry(registry).getCollection[UserV2]("users_add_required")

      // Decoding an old document with a required field missing must fail
      val ex = coll.find(Filters.equal("_id", oid)).first().toFuture().failed.futureValue
      ex shouldBe a[Exception]

      rawDatabase.drop().toFuture().futureValue
    }

  "Schema evolution: adding a field with a default value" should
    "decode an old document using the default when the field is absent" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // Old document: only has "name", no "age"
      insertRaw("users_add_default", Document("_id" -> oid, "name" -> "Bob"))

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[UserV2Default]
        .build

      val coll =
        rawDatabase.withCodecRegistry(registry).getCollection[UserV2Default]("users_add_default")

      val result = coll.find(Filters.equal("_id", oid)).first().toFuture().futureValue

      result.name shouldBe "Bob"
      result.age shouldBe 0 // the declared default value

      rawDatabase.drop().toFuture().futureValue
    }

  "Schema evolution: removing a field" should
    "decode an old document that has extra fields, silently ignoring them" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // Old document: has "category" which was removed in V2
      insertRaw(
        "products_remove_field",
        Document("_id" -> oid, "name" -> "Keyboard", "price" -> 49.99, "category" -> "Electronics")
      )

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[ProductV2]
        .build

      val coll =
        rawDatabase.withCodecRegistry(registry).getCollection[ProductV2]("products_remove_field")

      val result = coll.find(Filters.equal("_id", oid)).first().toFuture().futureValue

      result.name shouldBe "Keyboard"
      result.price shouldBe 49.99
      // "category" is silently ignored — no error

      rawDatabase.drop().toFuture().futureValue
    }

  "Schema evolution: renaming a field WITHOUT @BsonProperty" should
    "fail to decode an old document because the old field name is no longer recognised" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // Old document uses "firstName"; new schema uses "name"
      insertRaw("persons_rename_breaking", Document("_id" -> oid, "firstName" -> "Charlie"))

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[PersonV2]
        .build

      val coll =
        rawDatabase.withCodecRegistry(registry).getCollection[PersonV2]("persons_rename_breaking")

      // The codec looks for "name" but only "firstName" exists in the document — must fail
      val ex = coll.find(Filters.equal("_id", oid)).first().toFuture().failed.futureValue
      ex shouldBe a[Exception]

      rawDatabase.drop().toFuture().futureValue
    }

  "Schema evolution: renaming a field WITH @BsonProperty" should
    "decode old documents correctly using the annotated BSON field name" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // Old document uses "firstName" — the @BsonProperty("firstName") annotation maps to it
      insertRaw("persons_rename_safe", Document("_id" -> oid, "firstName" -> "Diana"))

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[PersonV3]
        .build

      val coll =
        rawDatabase.withCodecRegistry(registry).getCollection[PersonV3]("persons_rename_safe")

      val result = coll.find(Filters.equal("_id", oid)).first().toFuture().futureValue

      // @BsonProperty("firstName") on "name" means the codec reads from "firstName" in MongoDB
      result.name shouldBe "Diana"

      rawDatabase.drop().toFuture().futureValue
    }

  "Schema evolution: changing a field type from Int to Long" should
    "decode an old document that stored the value as INT32 into a Long field" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // Old document: score stored as Int (BSON INT32)
      insertRaw("scores_type_change", Document("_id" -> oid, "value" -> 42))

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[ScoreV2]
        .build

      val coll =
        rawDatabase.withCodecRegistry(registry).getCollection[ScoreV2]("scores_type_change")

      // INT32 in MongoDB must be readable as Long by the codec
      val result = coll.find(Filters.equal("_id", oid)).first().toFuture().futureValue
      result.value shouldBe 42L

      rawDatabase.drop().toFuture().futureValue
    }

  override def afterAll(): Unit =
    container.stop()

end SchemaEvolutionIntegrationSpec
