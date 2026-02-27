package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import org.bson.types.ObjectId
import org.mongodb.scala.*
import org.mongodb.scala.model.{Filters, Sorts}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.mongodb.scala.bson.annotations.BsonProperty

import RegistryBuilder.{*, given}

/** Integration tests verifying that MongoDB queries on domain fields work correctly.
  *
  * Every integration test in the codebase queries by `_id`. These tests prove that:
  *   - Queries on regular domain fields (name, age, price) return the correct documents.
  *   - Queries on @BsonProperty-renamed fields must use the BSON name, not the Scala name.
  *   - Querying with the Scala field name when @BsonProperty is present returns nothing.
  *   - Queries on nested fields using dot-notation work correctly.
  *   - Range and compound queries work correctly.
  */
class FieldQueryIntegrationSpec
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
    MongoClient(mongoUri).getDatabase("field_query_test")

  // ── Models ───────────────────────────────────────────────────────────────────

  case class Product(_id: ObjectId, name: String, price: Double, inStock: Boolean)

  // @BsonProperty renames the Scala field "firstName" to "first_name" in MongoDB
  case class Member(
      _id: ObjectId,
      @BsonProperty("first_name") firstName: String,
      @BsonProperty("last_name") lastName: String,
      age: Int
  )

  case class City(name: String, country: String)
  case class Traveller(_id: ObjectId, username: String, homeCity: City)

  case class Tag(_id: ObjectId, label: String, priority: Int)

  // ── Tests: regular field queries ─────────────────────────────────────────────

  "Query by string field" should "return only the matching document" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .register[Product]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[Product]("products_str_query")

    val products = Seq(
      Product(new ObjectId(), "Laptop", 999.99, true),
      Product(new ObjectId(), "Mouse", 29.99, true),
      Product(new ObjectId(), "Monitor", 349.99, false)
    )
    coll.insertMany(products).toFuture().futureValue

    val result = coll.find(Filters.equal("name", "Mouse")).first().toFuture().futureValue

    result.name shouldBe "Mouse"
    result.price shouldBe 29.99

    database.drop().toFuture().futureValue
  }

  "Query by boolean field" should "return all documents matching the flag" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .register[Product]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[Product]("products_bool_query")

    val products = Seq(
      Product(new ObjectId(), "Laptop", 999.99, inStock = true),
      Product(new ObjectId(), "Mouse", 29.99, inStock = true),
      Product(new ObjectId(), "Monitor", 349.99, inStock = false)
    )
    coll.insertMany(products).toFuture().futureValue

    val inStock = coll.find(Filters.equal("inStock", true)).toFuture().futureValue
    inStock should have size 2
    inStock.map(_.name) should contain allOf ("Laptop", "Mouse")

    val outOfStock = coll.find(Filters.equal("inStock", false)).toFuture().futureValue
    outOfStock should have size 1
    outOfStock.head.name shouldBe "Monitor"

    database.drop().toFuture().futureValue
  }

  "Query by numeric range" should "return documents within the range" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .register[Tag]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[Tag]("tags_range_query")

    val tags = Seq(
      Tag(new ObjectId(), "urgent", 10),
      Tag(new ObjectId(), "normal", 5),
      Tag(new ObjectId(), "low", 1),
      Tag(new ObjectId(), "critical", 20)
    )
    coll.insertMany(tags).toFuture().futureValue

    val highPriority = coll.find(Filters.gte("priority", 8)).toFuture().futureValue
    highPriority should have size 2
    highPriority.map(_.label) should contain allOf ("urgent", "critical")

    database.drop().toFuture().futureValue
  }

  // ── Tests: @BsonProperty renamed fields ──────────────────────────────────────

  "Query using the BSON name of a @BsonProperty-annotated field" should
    "return the matching document" in {
      assert(container.container.isRunning)

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[Member]
        .build

      val database = db.withCodecRegistry(registry)
      val coll     = database.getCollection[Member]("members_bson_name_query")

      val member = Member(new ObjectId(), "Alice", "Smith", 30)
      coll.insertOne(member).toFuture().futureValue

      // The BSON document stores "first_name" (due to @BsonProperty), NOT "firstName"
      val result = coll.find(Filters.equal("first_name", "Alice")).first().toFuture().futureValue

      result.firstName shouldBe "Alice"
      result.lastName shouldBe "Smith"

      database.drop().toFuture().futureValue
    }

  "Query using the Scala field name of a @BsonProperty-annotated field" should
    "return no results because MongoDB stores the BSON name, not the Scala name" in {
      assert(container.container.isRunning)

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[Member]
        .build

      val database = db.withCodecRegistry(registry)
      val coll     = database.getCollection[Member]("members_scala_name_query")

      val member = Member(new ObjectId(), "Bob", "Jones", 25)
      coll.insertOne(member).toFuture().futureValue

      // Using "firstName" (the Scala name) finds nothing — MongoDB stores "first_name"
      val result = coll.find(Filters.equal("firstName", "Bob")).toFuture().futureValue

      result shouldBe empty

      database.drop().toFuture().futureValue
    }

  "Compound query on multiple @BsonProperty fields" should "return the correct document" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .register[Member]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[Member]("members_compound_query")

    val members = Seq(
      Member(new ObjectId(), "Alice", "Smith", 30),
      Member(new ObjectId(), "Alice", "Brown", 25),
      Member(new ObjectId(), "Bob", "Smith", 40)
    )
    coll.insertMany(members).toFuture().futureValue

    // Both fields use their BSON names
    val result = coll
      .find(Filters.and(Filters.equal("first_name", "Alice"), Filters.equal("last_name", "Smith")))
      .first()
      .toFuture()
      .futureValue

    result.firstName shouldBe "Alice"
    result.lastName shouldBe "Smith"
    result.age shouldBe 30

    database.drop().toFuture().futureValue
  }

  // ── Tests: nested field queries ───────────────────────────────────────────────

  "Query by nested field using dot-notation" should "return the matching document" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .register[City]
      .register[Traveller]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[Traveller]("travellers_nested_query")

    val travellers = Seq(
      Traveller(new ObjectId(), "carol", City("Paris", "France")),
      Traveller(new ObjectId(), "dave", City("Berlin", "Germany")),
      Traveller(new ObjectId(), "eve", City("Paris", "France"))
    )
    coll.insertMany(travellers).toFuture().futureValue

    // Dot-notation query on the nested city name
    val parisians = coll.find(Filters.equal("homeCity.name", "Paris")).toFuture().futureValue

    parisians should have size 2
    parisians.map(_.username) should contain allOf ("carol", "eve")

    database.drop().toFuture().futureValue
  }

  "Query by nested field and a top-level field combined" should "narrow results correctly" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .register[City]
      .register[Traveller]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[Traveller]("travellers_combined_query")

    val travellers = Seq(
      Traveller(new ObjectId(), "carol", City("Paris", "France")),
      Traveller(new ObjectId(), "dave", City("Paris", "France")),
      Traveller(new ObjectId(), "eve", City("London", "UK"))
    )
    coll.insertMany(travellers).toFuture().futureValue

    val result = coll
      .find(
        Filters.and(
          Filters.equal("homeCity.name", "Paris"),
          Filters.equal("username", "carol")
        )
      )
      .first()
      .toFuture()
      .futureValue

    result.username shouldBe "carol"
    result.homeCity.country shouldBe "France"

    database.drop().toFuture().futureValue
  }

  // ── Tests: sort ───────────────────────────────────────────────────────────────

  "Sort by a domain field" should "return documents in the correct order" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .register[Tag]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[Tag]("tags_sort")

    val tags = Seq(
      Tag(new ObjectId(), "low", 1),
      Tag(new ObjectId(), "critical", 20),
      Tag(new ObjectId(), "normal", 5)
    )
    coll.insertMany(tags).toFuture().futureValue

    val sorted = coll.find().sort(Sorts.descending("priority")).toFuture().futureValue

    sorted.map(_.priority) shouldBe Seq(20, 5, 1)
    sorted.head.label shouldBe "critical"
    sorted.last.label shouldBe "low"

    database.drop().toFuture().futureValue
  }

  override def afterAll(): Unit =
    container.stop()

end FieldQueryIntegrationSpec
