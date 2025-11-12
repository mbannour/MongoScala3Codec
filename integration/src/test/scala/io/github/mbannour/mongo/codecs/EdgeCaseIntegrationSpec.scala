package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import io.github.mbannour.fields.MongoPath
import io.github.mbannour.mongo.codecs.RegistryBuilder.*
import org.bson.types.ObjectId
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

/** Edge case integration tests with real MongoDB.
  *
  * Tests extreme scenarios:
  *   - Large documents
  *   - Deep nesting
  *   - Unicode and special characters
  *   - Concurrent operations
  *   - Boundary values
  */
class EdgeCaseIntegrationSpec
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

  private def createDatabase: MongoDatabase =
    MongoClient(mongoUri).getDatabase("edge_case_test_db")

  // ========== Test Models ==========

  case class SimpleDoc(_id: ObjectId, value: String)

  case class Level5(_id: ObjectId, value: String)
  case class Level4(_id: ObjectId, nested: Level5)
  case class Level3(_id: ObjectId, nested: Level4)
  case class Level2(_id: ObjectId, nested: Level3)
  case class Level1(_id: ObjectId, nested: Level2)
  case class DeepNesting(_id: ObjectId, nested: Level1)

  case class LargeDoc(
      _id: ObjectId,
      field01: String,
      field02: String,
      field03: String,
      field04: String,
      field05: String,
      field06: String,
      field07: String,
      field08: String,
      field09: String,
      field10: String,
      field11: Int,
      field12: Int,
      field13: Int,
      field14: Int,
      field15: Int,
      field16: Double,
      field17: Double,
      field18: Double,
      field19: Boolean,
      field20: Boolean,
      tags: List[String],
      metadata: Map[String, String]
  )

  case class CollectionDoc(
      _id: ObjectId,
      largeList: List[String],
      largeVector: Vector[Int]
  )

  // ========== Unicode and Special Characters ==========

  "Codec" should "handle Unicode characters correctly" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[SimpleDoc]
      .build

    val database = createDatabase.withCodecRegistry(registry)
    val collection = database.getCollection[SimpleDoc]("unicode_test")

    // Test various Unicode ranges
    val testCases = List(
      SimpleDoc(new ObjectId(), "Hello 世界"), // Chinese
      SimpleDoc(new ObjectId(), "Привет мир"), // Cyrillic
      SimpleDoc(new ObjectId(), "مرحبا بالعالم"), // Arabic
      SimpleDoc(new ObjectId(), "🎉🎊🎈"), // Emoji
      SimpleDoc(new ObjectId(), "日本語テスト"), // Japanese
      SimpleDoc(new ObjectId(), "한국어 테스트"), // Korean
      SimpleDoc(new ObjectId(), "Ελληνικά"), // Greek
      SimpleDoc(new ObjectId(), "Ñoño"), // Spanish special chars
      SimpleDoc(new ObjectId(), "Zürich"), // German umlauts
      SimpleDoc(new ObjectId(), "Café") // French accents
    )

    collection.insertMany(testCases).toFuture().futureValue

    val retrieved = collection.find().toFuture().futureValue

    retrieved should have size 10
    retrieved.map(_.value).toSet shouldBe testCases.map(_.value).toSet

    database.drop().toFuture().futureValue
  }

  it should "handle special characters in strings" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[SimpleDoc]
      .build

    val database = createDatabase.withCodecRegistry(registry)
    val collection = database.getCollection[SimpleDoc]("special_chars")

    val specialChars = List(
      SimpleDoc(new ObjectId(), "Line\nBreak"),
      SimpleDoc(new ObjectId(), "Tab\tCharacter"),
      SimpleDoc(new ObjectId(), "Quote\"Test"),
      SimpleDoc(new ObjectId(), "Backslash\\Test"),
      SimpleDoc(new ObjectId(), "Null\u0000Character"),
      SimpleDoc(new ObjectId(), """Multi"Line"String"""),
      SimpleDoc(new ObjectId(), "Path/With/Slashes"),
      SimpleDoc(new ObjectId(), "Email@Test.com"),
      SimpleDoc(new ObjectId(), "Dollar$Sign"),
      SimpleDoc(new ObjectId(), "Percent%Sign")
    )

    collection.insertMany(specialChars).toFuture().futureValue

    val retrieved = collection.find().toFuture().futureValue

    retrieved should have size 10
    retrieved.map(_.value) should contain theSameElementsAs specialChars.map(_.value)

    database.drop().toFuture().futureValue
  }

  // ========== Deep Nesting Tests ==========

  it should "handle deep nesting (6 levels)" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerAll[(Level5, Level4, Level3, Level2, Level1, DeepNesting)]
      .build

    val database = createDatabase.withCodecRegistry(registry)
    val collection = database.getCollection[DeepNesting]("deep_nesting")

    val deepDoc = DeepNesting(
      _id = new ObjectId(),
      nested = Level1(
        _id = new ObjectId(),
        nested = Level2(
          _id = new ObjectId(),
          nested = Level3(
            _id = new ObjectId(),
            nested = Level4(
              _id = new ObjectId(),
              nested = Level5(
                _id = new ObjectId(),
                value = "Deeply nested value"
              )
            )
          )
        )
      )
    )

    collection.insertOne(deepDoc).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[DeepNesting](_._id), deepDoc._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe deepDoc
    retrieved.nested.nested.nested.nested.nested.value shouldBe "Deeply nested value"

    database.drop().toFuture().futureValue
  }

  // ========== Large Document Tests ==========

  it should "handle documents with many fields" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[LargeDoc]
      .build

    val database = createDatabase.withCodecRegistry(registry)
    val collection = database.getCollection[LargeDoc]("large_docs")

    val largeDoc = LargeDoc(
      _id = new ObjectId(),
      field01 = "Value 01",
      field02 = "Value 02",
      field03 = "Value 03",
      field04 = "Value 04",
      field05 = "Value 05",
      field06 = "Value 06",
      field07 = "Value 07",
      field08 = "Value 08",
      field09 = "Value 09",
      field10 = "Value 10",
      field11 = 11,
      field12 = 12,
      field13 = 13,
      field14 = 14,
      field15 = 15,
      field16 = 16.16,
      field17 = 17.17,
      field18 = 18.18,
      field19 = true,
      field20 = false,
      tags = (1 to 100).map(i => s"tag-$i").toList,
      metadata = (1 to 50).map(i => s"key$i" -> s"value$i").toMap
    )

    collection.insertOne(largeDoc).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[LargeDoc](_._id), largeDoc._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe largeDoc
    retrieved.tags should have size 100
    retrieved.metadata should have size 50

    database.drop().toFuture().futureValue
  }

  // ========== Large Collection Tests ==========

  it should "handle collections with thousands of items" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[CollectionDoc]
      .build

    val database = createDatabase.withCodecRegistry(registry)
    val collection = database.getCollection[CollectionDoc]("large_collections")

    val largeList = (1 to 5000).map(i => s"item-$i").toList
    val largeVector = (1 to 5000).toVector

    val doc = CollectionDoc(
      _id = new ObjectId(),
      largeList = largeList,
      largeVector = largeVector
    )

    collection.insertOne(doc).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[CollectionDoc](_._id), doc._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe doc
    retrieved.largeList should have size 5000
    retrieved.largeVector should have size 5000
    retrieved.largeList.head shouldBe "item-1"
    retrieved.largeVector.head shouldBe 1

    database.drop().toFuture().futureValue
  }

  // ========== Boundary Value Tests ==========

  it should "handle extreme numeric values" in {
    case class NumericBoundaries(
        _id: ObjectId,
        maxInt: Int,
        minInt: Int,
        maxLong: Long,
        minLong: Long,
        maxDouble: Double,
        minDouble: Double,
        zero: Int,
        negativeZero: Double
    )

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[NumericBoundaries]
      .build

    val database = createDatabase.withCodecRegistry(registry)
    val collection = database.getCollection[NumericBoundaries]("numeric_boundaries")

    val doc = NumericBoundaries(
      _id = new ObjectId(),
      maxInt = Int.MaxValue,
      minInt = Int.MinValue,
      maxLong = Long.MaxValue,
      minLong = Long.MinValue,
      maxDouble = Double.MaxValue,
      minDouble = Double.MinValue,
      zero = 0,
      negativeZero = -0.0
    )

    collection.insertOne(doc).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[NumericBoundaries](_._id), doc._id))
      .first()
      .toFuture()
      .futureValue

    retrieved.maxInt shouldBe Int.MaxValue
    retrieved.minInt shouldBe Int.MinValue
    retrieved.maxLong shouldBe Long.MaxValue
    retrieved.minLong shouldBe Long.MinValue
    retrieved.maxDouble shouldBe Double.MaxValue
    retrieved.minDouble shouldBe Double.MinValue
    retrieved.zero shouldBe 0

    database.drop().toFuture().futureValue
  }

  // ========== Empty String Tests ==========

  it should "handle empty strings and collections" in {
    case class EmptyValues(
        _id: ObjectId,
        emptyString: String,
        emptyList: List[String],
        emptyMap: Map[String, String]
    )

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[EmptyValues]
      .build

    val database = createDatabase.withCodecRegistry(registry)
    val collection = database.getCollection[EmptyValues]("empty_values")

    val doc = EmptyValues(
      _id = new ObjectId(),
      emptyString = "",
      emptyList = List.empty,
      emptyMap = Map.empty
    )

    collection.insertOne(doc).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[EmptyValues](_._id), doc._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe doc
    retrieved.emptyString shouldBe ""
    retrieved.emptyList shouldBe empty
    retrieved.emptyMap shouldBe empty

    database.drop().toFuture().futureValue
  }

  // ========== Concurrent Access Tests ==========

  it should "handle concurrent inserts correctly" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[SimpleDoc]
      .build

    val database = createDatabase.withCodecRegistry(registry)
    val collection = database.getCollection[SimpleDoc]("concurrent_test")

    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Future

    // Insert 100 documents concurrently
    val futures = (1 to 100).map { i =>
      Future {
        val doc = SimpleDoc(new ObjectId(), s"concurrent-$i")
        collection.insertOne(doc).toFuture().futureValue
      }
    }

    Future.sequence(futures).futureValue

    val count = collection.countDocuments().toFuture().futureValue
    count shouldBe 100

    database.drop().toFuture().futureValue
  }

  override def afterAll(): Unit =
    container.stop()

end EdgeCaseIntegrationSpec
