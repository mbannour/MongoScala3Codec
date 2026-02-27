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

import RegistryBuilder.{*, given}

/** Integration tests for case class default field values during decode.
  *
  * The codec factory (CaseClassFactory) resolves missing fields in two ways:
  *   - `Option[T]` fields absent from the document → decoded as `None`
  *   - Fields with a Scala default value absent from the document → decoded using the default
  *   - Required fields (no default, non-Option) absent from the document → `RuntimeException("Missing field: …")`
  *
  * These tests insert raw documents (simulating old/partial data in MongoDB) and decode them with a codec that may have fields not present in
  * the stored document.
  */
class DefaultValuesIntegrationSpec
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
    MongoClient(mongoUri).getDatabase("default_values_test")

  // ── Models ─────────────────────────────────────────────────────────────────

  // All non-_id fields have defaults
  case class Settings(
      _id: ObjectId,
      theme: String = "dark",
      language: String = "en",
      notifications: Boolean = true,
      pageSize: Int = 20
  )

  // Mix of required and defaulted fields
  case class Article(
      _id: ObjectId,
      title: String,                      // required — no default
      body: String,                       // required — no default
      status: String = "draft",           // optional with default
      viewCount: Int = 0,                 // optional with default
      tags: List[String] = List.empty     // optional with default
  )

  // Nested case class — inner class also has defaults
  case class ContactInfo(phone: String = "unknown", email: String = "unknown")
  case class Customer(_id: ObjectId, name: String, contact: ContactInfo = ContactInfo())

  // Required field — no default — confirms failure message
  case class StrictDoc(_id: ObjectId, requiredField: String)

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private def insertRaw(collName: String, doc: Document): Unit =
    db.getCollection(collName).insertOne(doc).toFuture().futureValue

  // ── Tests: defaults are used when field is absent ───────────────────────────

  "Default values: all fields absent except _id" should
    "decode using every declared default" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // Only _id present — every other field must fall back to its default
      insertRaw("settings_all_absent", Document("_id" -> oid))

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[Settings]
        .build

      val coll = db.withCodecRegistry(registry).getCollection[Settings]("settings_all_absent")
      val result = coll.find(Filters.equal("_id", oid)).first().toFuture().futureValue

      result.theme shouldBe "dark"
      result.language shouldBe "en"
      result.notifications shouldBe true
      result.pageSize shouldBe 20

      db.drop().toFuture().futureValue
    }

  "Default values: stored value overrides the default" should
    "use the value from MongoDB, not the default" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // Some fields present with non-default values
      insertRaw(
        "settings_partial",
        Document("_id" -> oid, "theme" -> "light", "pageSize" -> 50)
      )

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[Settings]
        .build

      val coll = db.withCodecRegistry(registry).getCollection[Settings]("settings_partial")
      val result = coll.find(Filters.equal("_id", oid)).first().toFuture().futureValue

      result.theme shouldBe "light"    // from MongoDB
      result.pageSize shouldBe 50      // from MongoDB
      result.language shouldBe "en"    // from default (absent in doc)
      result.notifications shouldBe true // from default (absent in doc)

      db.drop().toFuture().futureValue
    }

  "Default values: mix of required and defaulted fields" should
    "use defaults for absent optional fields, read required fields from document" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // Only required fields present; defaulted fields absent
      insertRaw(
        "articles_required_only",
        Document("_id" -> oid, "title" -> "My Post", "body" -> "Content here")
      )

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[Article]
        .build

      val coll = db.withCodecRegistry(registry).getCollection[Article]("articles_required_only")
      val result = coll.find(Filters.equal("_id", oid)).first().toFuture().futureValue

      result.title shouldBe "My Post"           // from MongoDB
      result.body shouldBe "Content here"       // from MongoDB
      result.status shouldBe "draft"            // default
      result.viewCount shouldBe 0               // default
      result.tags shouldBe List.empty           // default

      db.drop().toFuture().futureValue
    }

  "Default values: full round-trip preserves stored values" should
    "not lose any field when all fields are present in MongoDB" in {
      assert(container.container.isRunning)

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[Article]
        .build

      val database = db.withCodecRegistry(registry)
      val coll     = database.getCollection[Article]("articles_full")

      val article = Article(
        _id = new ObjectId(),
        title = "Scala 3",
        body = "Great language",
        status = "published",
        viewCount = 1024,
        tags = List("scala", "fp", "types")
      )
      coll.insertOne(article).toFuture().futureValue

      val result = coll.find(Filters.equal("_id", article._id)).first().toFuture().futureValue

      result shouldBe article

      database.drop().toFuture().futureValue
    }

  // ── Tests: nested case class defaults ───────────────────────────────────────

  "Default values: nested case class field absent from document" should
    "decode the parent using its default instance for the nested field" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // "contact" sub-document completely absent — should fall back to ContactInfo()
      insertRaw("customers_no_contact", Document("_id" -> oid, "name" -> "Alice"))

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[ContactInfo]
        .register[Customer]
        .build

      val coll = db.withCodecRegistry(registry).getCollection[Customer]("customers_no_contact")
      val result = coll.find(Filters.equal("_id", oid)).first().toFuture().futureValue

      result.name shouldBe "Alice"
      result.contact.phone shouldBe "unknown"  // ContactInfo() default
      result.contact.email shouldBe "unknown"  // ContactInfo() default

      db.drop().toFuture().futureValue
    }

  "Default values: nested case class partially present in document" should
    "decode the nested object normally when it is stored in MongoDB" in {
      assert(container.container.isRunning)

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[ContactInfo]
        .register[Customer]
        .build

      val database = db.withCodecRegistry(registry)
      val coll     = database.getCollection[Customer]("customers_with_contact")

      val customer = Customer(new ObjectId(), "Bob", ContactInfo("+1234567890", "bob@example.com"))
      coll.insertOne(customer).toFuture().futureValue

      val result = coll.find(Filters.equal("_id", customer._id)).first().toFuture().futureValue

      result.name shouldBe "Bob"
      result.contact.phone shouldBe "+1234567890"
      result.contact.email shouldBe "bob@example.com"

      database.drop().toFuture().futureValue
    }

  // ── Tests: missing required field gives clear error ──────────────────────────

  "Default values: required field missing from document" should
    "fail with a clear error message naming the missing field" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()
      // "requiredField" is absent — no default, not Option
      insertRaw("strict_docs_missing", Document("_id" -> oid))

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[StrictDoc]
        .build

      val coll = db.withCodecRegistry(registry).getCollection[StrictDoc]("strict_docs_missing")

      val ex = coll.find(Filters.equal("_id", oid)).first().toFuture().failed.futureValue

      ex shouldBe a[Exception]
      ex.getMessage should include("requiredField")

      db.drop().toFuture().futureValue
    }

  // ── Tests: multiple documents — defaults applied per document ────────────────

  "Default values: batch of documents with varying field presence" should
    "apply defaults independently per document" in {
      assert(container.container.isRunning)

      val oid1 = new ObjectId()
      val oid2 = new ObjectId()
      val oid3 = new ObjectId()

      // doc1: only _id
      // doc2: theme only
      // doc3: all fields
      insertRaw("settings_batch", Document("_id" -> oid1))
      insertRaw("settings_batch", Document("_id" -> oid2, "theme" -> "solarized"))
      insertRaw(
        "settings_batch",
        Document("_id" -> oid3, "theme" -> "light", "language" -> "fr", "notifications" -> false, "pageSize" -> 10)
      )

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .register[Settings]
        .build

      val coll = db.withCodecRegistry(registry).getCollection[Settings]("settings_batch")

      val all = coll.find().toFuture().futureValue
      val byId = all.map(s => s._id -> s).toMap

      // doc1: all defaults
      byId(oid1).theme shouldBe "dark"
      byId(oid1).language shouldBe "en"
      byId(oid1).notifications shouldBe true
      byId(oid1).pageSize shouldBe 20

      // doc2: theme from DB, rest defaults
      byId(oid2).theme shouldBe "solarized"
      byId(oid2).language shouldBe "en"

      // doc3: all from DB
      byId(oid3).theme shouldBe "light"
      byId(oid3).language shouldBe "fr"
      byId(oid3).notifications shouldBe false
      byId(oid3).pageSize shouldBe 10

      db.drop().toFuture().futureValue
    }

  override def afterAll(): Unit =
    container.stop()

end DefaultValuesIntegrationSpec
