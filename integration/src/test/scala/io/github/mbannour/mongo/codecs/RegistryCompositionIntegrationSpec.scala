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

import org.bson.{Document => BsonDoc}

import RegistryBuilder.{*, given}

/** Integration tests for RegistryBuilder composition via the ++ operator.
  *
  * Key behaviours documented here:
  *
  *   1. `A ++ B` makes codecs from both A and B available in the resulting registry.
  *   2. The `config` of the **left** builder is inherited by the merged builder. The right builder's config is ignored for the merged
  *      state. However, providers on each side already have their original config baked in from the time they were registered — so a
  *      `User` registered under `ignoreNone` keeps ignoring None even after being merged with a builder that uses `encodeNone`.
  *   3. Chains of `++` operators work correctly.
  *   4. State inspection methods (`codecCount`, `providerCount`, `isEmpty`, `currentConfig`) reflect the merged state.
  *   5. The merged registry works end-to-end with real MongoDB.
  */
class RegistryCompositionIntegrationSpec
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
    MongoClient(mongoUri).getDatabase("registry_composition_test")

  // ── Models ──────────────────────────────────────────────────────────────────

  case class Author(_id: ObjectId, name: String, bio: Option[String])
  case class Book(_id: ObjectId, title: String, pages: Int)
  case class Publisher(_id: ObjectId, company: String, country: String)
  case class Review(_id: ObjectId, score: Int, comment: Option[String])

  // ── Tests: codecs from both sides are available ──────────────────────────────

  "Registry ++: merging two builders" should
    "make all types from both builders available in the resulting registry" in {
      assert(container.container.isRunning)

      val leftBuilder  = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Author].register[Book]
      val rightBuilder = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Publisher]

      val merged   = leftBuilder ++ rightBuilder
      val registry = merged.build

      // All three types must be resolvable
      registry.get(classOf[Author])    should not be null
      registry.get(classOf[Book])      should not be null
      registry.get(classOf[Publisher]) should not be null
    }

  "Registry ++: providerCount" should
    "equal the sum of both builders' provider counts" in {
      assert(container.container.isRunning)

      val left  = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Author].register[Book]
      val right = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Publisher].register[Review]

      left.providerCount shouldBe 2
      right.providerCount shouldBe 2

      val merged = left ++ right
      merged.providerCount shouldBe 4
    }

  // ── Tests: config inheritance ────────────────────────────────────────────────

  "Registry ++: config" should
    "inherit the LEFT builder's config for the merged builder state" in {
      assert(container.container.isRunning)

      val ignoreLeft = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .ignoreNone
        .register[Author]

      val encodeRight = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .encodeNone
        .register[Review]

      val merged = ignoreLeft ++ encodeRight

      // The merged builder carries the LEFT config
      merged.currentConfig.noneHandling shouldBe NoneHandling.Ignore
    }

  // ── Tests: each side's providers keep their original config ─────────────────

  "Registry ++: NoneHandling per provider" should
    "keep each provider's original config even after merging with a differently-configured builder" in {
      assert(container.container.isRunning)

      // Author registered with ignoreNone
      val leftBuilder = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .ignoreNone
        .register[Author]

      // Review registered with encodeNone
      val rightBuilder = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .encodeNone
        .register[Review]

      val registry = (leftBuilder ++ rightBuilder).build
      val database = db.withCodecRegistry(registry)

      // -- Author: bio=None should be OMITTED (ignoreNone) ---
      val authorColl = database.getCollection[Author]("authors_config_check")
      val author     = Author(new ObjectId(), "Alice", None)
      authorColl.insertOne(author).toFuture().futureValue

      // Read back as a raw BsonDoc to inspect the actual stored BSON field presence
      val rawAuthorColl = db.getCollection[BsonDoc]("authors_config_check")
      val rawAuthor = rawAuthorColl
        .find(Filters.equal("_id", author._id))
        .first()
        .toFuture()
        .futureValue

      rawAuthor.containsKey("bio") shouldBe false // field was omitted (ignoreNone)

      // -- Review: comment=None should be ENCODED as null (encodeNone) ---
      val reviewColl = database.getCollection[Review]("reviews_config_check")
      val review     = Review(new ObjectId(), 5, None)
      reviewColl.insertOne(review).toFuture().futureValue

      val rawReviewColl = db.getCollection[BsonDoc]("reviews_config_check")
      val rawReview = rawReviewColl
        .find(Filters.equal("_id", review._id))
        .first()
        .toFuture()
        .futureValue

      rawReview.containsKey("comment") shouldBe true // field present (encodeNone)
      rawReview.get("comment") shouldBe null         // stored as BSON null

      database.drop().toFuture().futureValue
    }

  // ── Tests: chained composition ───────────────────────────────────────────────

  "Registry ++: chaining three builders" should
    "make all types from all three builders available" in {
      assert(container.container.isRunning)

      val a = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Author]
      val b = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Book]
      val c = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Publisher]

      val merged   = a ++ b ++ c
      val registry = merged.build

      registry.get(classOf[Author])    should not be null
      registry.get(classOf[Book])      should not be null
      registry.get(classOf[Publisher]) should not be null

      merged.providerCount shouldBe 3
    }

  // ── Tests: reusable base builder ─────────────────────────────────────────────

  "Registry ++: composing from a shared base builder" should
    "create independent registries without cross-contamination" in {
      assert(container.container.isRunning)

      // A reusable base that both teams share
      val shared = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Author]

      val withBooks     = (shared ++ MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Book]).build
      val withPublisher = (shared ++ MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Publisher]).build

      // withBooks: Author + Book, no Publisher
      withBooks.get(classOf[Author]) should not be null
      withBooks.get(classOf[Book])   should not be null
      an[Exception] should be thrownBy withBooks.get(classOf[Publisher])

      // withPublisher: Author + Publisher, no Book
      withPublisher.get(classOf[Author])    should not be null
      withPublisher.get(classOf[Publisher]) should not be null
      an[Exception] should be thrownBy withPublisher.get(classOf[Book])
    }

  // ── Tests: isEmpty reflects merged state ─────────────────────────────────────

  "Registry ++: isEmpty" should
    "be false when either side has registrations" in {
      assert(container.container.isRunning)

      val empty    = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      val nonEmpty = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Author]

      empty.isEmpty shouldBe true
      (empty ++ nonEmpty).isEmpty shouldBe false
      (nonEmpty ++ empty).isEmpty shouldBe false
    }

  // ── Tests: MongoDB round-trip with merged registry ───────────────────────────

  "Registry ++: MongoDB round-trip" should
    "correctly insert and retrieve all types registered across both builders" in {
      assert(container.container.isRunning)

      val left  = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Author].register[Book]
      val right = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Publisher]

      val database = db.withCodecRegistry((left ++ right).build)

      // Insert and retrieve Author
      val authorColl = database.getCollection[Author]("authors_roundtrip")
      val author     = Author(new ObjectId(), "Isaac Asimov", Some("Science fiction author"))
      authorColl.insertOne(author).toFuture().futureValue
      val retrievedAuthor = authorColl.find(Filters.equal("_id", author._id)).first().toFuture().futureValue
      retrievedAuthor shouldBe author

      // Insert and retrieve Book
      val bookColl = database.getCollection[Book]("books_roundtrip")
      val book     = Book(new ObjectId(), "Foundation", 244)
      bookColl.insertOne(book).toFuture().futureValue
      val retrievedBook = bookColl.find(Filters.equal("_id", book._id)).first().toFuture().futureValue
      retrievedBook shouldBe book

      // Insert and retrieve Publisher
      val pubColl   = database.getCollection[Publisher]("publishers_roundtrip")
      val publisher = Publisher(new ObjectId(), "Gnome Press", "USA")
      pubColl.insertOne(publisher).toFuture().futureValue
      val retrievedPublisher = pubColl.find(Filters.equal("_id", publisher._id)).first().toFuture().futureValue
      retrievedPublisher shouldBe publisher

      database.drop().toFuture().futureValue
    }

  "Registry ++: MongoDB round-trip with optional fields" should
    "preserve Some and None values from both merged builders" in {
      assert(container.container.isRunning)

      val registry = (
        MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Author] ++
          MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.register[Review]
      ).build

      val database = db.withCodecRegistry(registry)

      val authorColl = database.getCollection[Author]("authors_optional")
      val withBio    = Author(new ObjectId(), "Alice", Some("Author bio"))
      val withoutBio = Author(new ObjectId(), "Bob", None)

      authorColl.insertMany(Seq(withBio, withoutBio)).toFuture().futureValue

      val all = authorColl.find().toFuture().futureValue
      val byName = all.map(a => a.name -> a).toMap

      byName("Alice").bio shouldBe Some("Author bio")
      byName("Bob").bio shouldBe None

      database.drop().toFuture().futureValue
    }

  override def afterAll(): Unit =
    container.stop()

end RegistryCompositionIntegrationSpec
