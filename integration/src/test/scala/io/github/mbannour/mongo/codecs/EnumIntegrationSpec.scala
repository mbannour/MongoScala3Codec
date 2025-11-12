package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import io.github.mbannour.mongo.codecs.models.EnumModels.*
import io.github.mbannour.mongo.codecs.models.*
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import io.github.mbannour.fields.MongoPath

/** Comprehensive integration tests for Scala 3 enum support.
  *
  * Tests cover:
  *   - String-based enum serialization
  *   - Ordinal-based enum serialization
  *   - Enums with custom fields (automatic code detection)
  *   - Enums with @BsonEnum annotation
  *   - Optional enum fields
  *   - Collections of enums
  *   - Nested case classes with enums
  *   - Maps with enum values
  */
class EnumIntegrationSpec
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

  private def createDatabaseWithRegistry(registry: CodecRegistry): MongoDatabase =
    MongoClient(mongoUri)
      .getDatabase("enum_test_db")
      .withCodecRegistry(registry)

  // ========== Test 1: Simple String Enum ==========
  "Enum codec" should "handle simple string-based enum serialization" in {
    val database: MongoDatabase = createDatabaseWithRegistry(colorRegistry)
    val collection: MongoCollection[ColoredItem] = database.getCollection("colored_items")

    val item = ColoredItem(
      _id = new ObjectId(),
      name = "Ruby Gem",
      color = Color.Red
    )

    collection.insertOne(item).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[ColoredItem](_._id), item._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe item
    retrieved.color shouldBe Color.Red

    // Verify BSON stores string representation
    import org.bson.Document
    val docCollection: MongoCollection[Document] = database.getCollection("colored_items")
    val doc = docCollection
      .find(Filters.equal(MongoPath.of[ColoredItem](_._id), item._id))
      .first()
      .toFuture()
      .futureValue

    doc.getString("color") shouldBe "Red"

    database.drop().toFuture().futureValue
  }

  it should "handle all enum values in string-based enum" in {
    val database: MongoDatabase = createDatabaseWithRegistry(colorRegistry)
    val collection: MongoCollection[ColoredItem] = database.getCollection("all_colors")

    val items = List(
      ColoredItem(new ObjectId(), "Red Item", Color.Red),
      ColoredItem(new ObjectId(), "Green Item", Color.Green),
      ColoredItem(new ObjectId(), "Blue Item", Color.Blue),
      ColoredItem(new ObjectId(), "Yellow Item", Color.Yellow)
    )

    collection.insertMany(items).toFuture().futureValue

    val retrieved = collection.find().toFuture().futureValue

    retrieved should have size 4
    retrieved.map(_.color) should contain allOf (Color.Red, Color.Green, Color.Blue, Color.Yellow)

    database.drop().toFuture().futureValue
  }

  // ========== Test 2: Ordinal Enum ==========
  it should "handle ordinal-based enum serialization" in {
    val database: MongoDatabase = createDatabaseWithRegistry(levelRegistry)
    val collection: MongoCollection[UserProfile] = database.getCollection("profiles")

    val profile = UserProfile(
      _id = new ObjectId(),
      username = "player123",
      level = Level.Advanced
    )

    collection.insertOne(profile).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[UserProfile](_._id), profile._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe profile
    retrieved.level shouldBe Level.Advanced

    database.drop().toFuture().futureValue
  }

  // ========== Test 3: Enum with Custom Field (Automatic Code Detection) ==========
  it should "handle enum with custom code field (automatic detection)" in {
    val database: MongoDatabase = createDatabaseWithRegistry(statusCodeRegistry)
    val collection: MongoCollection[ApiResponse] = database.getCollection("api_responses")

    val response = ApiResponse(
      _id = new ObjectId(),
      message = "Resource not found",
      status = StatusCode.NotFound
    )

    collection.insertOne(response).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[ApiResponse](_._id), response._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe response
    retrieved.status shouldBe StatusCode.NotFound
    retrieved.status.code shouldBe 404

    database.drop().toFuture().futureValue
  }

  it should "serialize enum with code field correctly to BSON" in {
    val database: MongoDatabase = createDatabaseWithRegistry(statusCodeRegistry)
    val collection: MongoCollection[ApiResponse] = database.getCollection("status_codes")

    val responses = List(
      ApiResponse(new ObjectId(), "OK", StatusCode.Success),
      ApiResponse(new ObjectId(), "Created", StatusCode.Created),
      ApiResponse(new ObjectId(), "Bad Request", StatusCode.BadRequest),
      ApiResponse(new ObjectId(), "Not Found", StatusCode.NotFound),
      ApiResponse(new ObjectId(), "Server Error", StatusCode.ServerError)
    )

    collection.insertMany(responses).toFuture().futureValue

    // Verify we can query by status name
    val notFoundResponses = collection
      .find(Filters.equal(MongoPath.of[ApiResponse](_.status), StatusCode.NotFound))
      .toFuture()
      .futureValue

    notFoundResponses should have size 1
    notFoundResponses.head.status.code shouldBe 404

    database.drop().toFuture().futureValue
  }

  // ========== Test 4: Enum with @BsonEnum Annotation ==========
  it should "handle enum with @BsonEnum annotation specifying custom field" in {
    val database: MongoDatabase = createDatabaseWithRegistry(taskRegistry)
    val collection: MongoCollection[TaskItem] = database.getCollection("tasks")

    val task = TaskItem(
      _id = new ObjectId(),
      title = "Fix critical bug",
      priority = Priority.Critical
    )

    collection.insertOne(task).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[TaskItem](_._id), task._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe task
    retrieved.priority shouldBe Priority.Critical
    retrieved.priority.value shouldBe "critical"
    retrieved.priority.weight shouldBe 20

    // Verify BSON stores the custom field value
    import org.bson.Document
    val docCollection: MongoCollection[Document] = database.getCollection("tasks")
    val doc = docCollection
      .find(Filters.equal(MongoPath.of[TaskItem](_._id), task._id))
      .first()
      .toFuture()
      .futureValue

    doc.getString("priority") shouldBe "critical"

    database.drop().toFuture().futureValue
  }

  it should "handle all priority levels with @BsonEnum annotation" in {
    val database: MongoDatabase = createDatabaseWithRegistry(taskRegistry)
    val collection: MongoCollection[TaskItem] = database.getCollection("priority_tasks")

    val tasks = List(
      TaskItem(new ObjectId(), "Low priority task", Priority.Low),
      TaskItem(new ObjectId(), "Medium priority task", Priority.Medium),
      TaskItem(new ObjectId(), "High priority task", Priority.High),
      TaskItem(new ObjectId(), "Critical priority task", Priority.Critical)
    )

    collection.insertMany(tasks).toFuture().futureValue

    val highPriorityTasks = collection
      .find(Filters.equal(MongoPath.of[TaskItem](_.priority), Priority.High))
      .toFuture()
      .futureValue

    highPriorityTasks should have size 1
    highPriorityTasks.head.title shouldBe "High priority task"

    database.drop().toFuture().futureValue
  }

  it should "handle enum with @BsonEnum using id field" in {
    val database: MongoDatabase = createDatabaseWithRegistry(productRegistry)
    val collection: MongoCollection[Product] = database.getCollection("products")

    val product = Product(
      _id = new ObjectId(),
      name = "Laptop",
      category = Category.Electronics,
      price = 999.99
    )

    collection.insertOne(product).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Product](_._id), product._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe product
    retrieved.category shouldBe Category.Electronics

    // Verify BSON stores the id value
    import org.bson.Document
    val docCollection: MongoCollection[Document] = database.getCollection("products")
    val doc = docCollection
      .find(Filters.equal(MongoPath.of[Product](_._id), product._id))
      .first()
      .toFuture()
      .futureValue

    doc.getString("category") shouldBe "ELEC"

    database.drop().toFuture().futureValue
  }

  // ========== Test 5: Optional Enum Fields ==========
  it should "handle optional enum fields (Some case)" in {
    val database: MongoDatabase = createDatabaseWithRegistry(configurationRegistry)
    val collection: MongoCollection[Configuration] = database.getCollection("configs")

    val config = Configuration(
      _id = new ObjectId(),
      name = "Theme Settings",
      color = Some(Color.Blue),
      level = Some(Level.Intermediate)
    )

    collection.insertOne(config).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Configuration](_._id), config._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe config
    retrieved.color shouldBe Some(Color.Blue)
    retrieved.level shouldBe Some(Level.Intermediate)

    database.drop().toFuture().futureValue
  }

  it should "handle optional enum fields (None case)" in {
    val database: MongoDatabase = createDatabaseWithRegistry(configurationRegistry)
    val collection: MongoCollection[Configuration] = database.getCollection("configs_none")

    val config = Configuration(
      _id = new ObjectId(),
      name = "Default Settings",
      color = None,
      level = None
    )

    collection.insertOne(config).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Configuration](_._id), config._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe config
    retrieved.color shouldBe None
    retrieved.level shouldBe None

    database.drop().toFuture().futureValue
  }

  // ========== Test 6: Collections of Enums ==========
  it should "handle List of enums" in {
    val database: MongoDatabase = createDatabaseWithRegistry(paletteRegistry)
    val collection: MongoCollection[Palette] = database.getCollection("palettes")

    val palette = Palette(
      _id = new ObjectId(),
      name = "Primary Colors",
      colors = List(Color.Red, Color.Blue, Color.Yellow)
    )

    collection.insertOne(palette).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Palette](_._id), palette._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe palette
    retrieved.colors should have size 3
    retrieved.colors should contain allOf (Color.Red, Color.Blue, Color.Yellow)

    database.drop().toFuture().futureValue
  }

  it should "handle empty List of enums" in {
    val database: MongoDatabase = createDatabaseWithRegistry(paletteRegistry)
    val collection: MongoCollection[Palette] = database.getCollection("empty_palettes")

    val palette = Palette(
      _id = new ObjectId(),
      name = "Empty Palette",
      colors = List.empty
    )

    collection.insertOne(palette).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Palette](_._id), palette._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe palette
    retrieved.colors shouldBe empty

    database.drop().toFuture().futureValue
  }

  // ========== Test 7: Multiple Enums in One Case Class ==========
  it should "handle multiple enums in a single case class" in {
    val database: MongoDatabase = createDatabaseWithRegistry(gameCharacterRegistry)
    val collection: MongoCollection[GameCharacter] = database.getCollection("characters")

    val character = GameCharacter(
      _id = new ObjectId(),
      name = "Warrior",
      level = Level.Expert,
      favoriteColor = Color.Red,
      priority = Priority.High
    )

    collection.insertOne(character).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[GameCharacter](_._id), character._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe character
    retrieved.level shouldBe Level.Expert
    retrieved.favoriteColor shouldBe Color.Red
    retrieved.priority shouldBe Priority.High
    retrieved.priority.weight shouldBe 10

    database.drop().toFuture().futureValue
  }

  // ========== Test 8: Nested Case Class with Enum ==========
  it should "handle nested case classes containing enums" in {
    val database: MongoDatabase = createDatabaseWithRegistry(orderRegistry)
    val collection: MongoCollection[Order] = database.getCollection("orders")

    val order = Order(
      _id = new ObjectId(),
      orderId = "ORD-12345",
      items = List(
        ItemWithStatus("Item 1", StatusCode.Success),
        ItemWithStatus("Item 2", StatusCode.Created),
        ItemWithStatus("Item 3", StatusCode.BadRequest)
      ),
      priority = Priority.High
    )

    collection.insertOne(order).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Order](_._id), order._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe order
    retrieved.items should have size 3
    retrieved.items(0).status shouldBe StatusCode.Success
    retrieved.items(1).status shouldBe StatusCode.Created
    retrieved.items(2).status shouldBe StatusCode.BadRequest
    retrieved.priority shouldBe Priority.High

    database.drop().toFuture().futureValue
  }

  // ========== Test 9: Map with Enum Values ==========
  it should "handle Map with enum values" in {
    val database: MongoDatabase = createDatabaseWithRegistry(colorMappingRegistry)
    val collection: MongoCollection[ColorMapping] = database.getCollection("color_mappings")

    val mapping = ColorMapping(
      _id = new ObjectId(),
      name = "Room Colors",
      colorAssignments = Map(
        "bedroom" -> Color.Blue,
        "kitchen" -> Color.Yellow,
        "living_room" -> Color.Green
      )
    )

    collection.insertOne(mapping).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[ColorMapping](_._id), mapping._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe mapping
    retrieved.colorAssignments should have size 3
    retrieved.colorAssignments("bedroom") shouldBe Color.Blue
    retrieved.colorAssignments("kitchen") shouldBe Color.Yellow
    retrieved.colorAssignments("living_room") shouldBe Color.Green

    database.drop().toFuture().futureValue
  }

  // ========== Test 10: Query by Enum Value ==========
  it should "support querying by enum values" in {
    val database: MongoDatabase = createDatabaseWithRegistry(colorRegistry)
    val collection: MongoCollection[ColoredItem] = database.getCollection("queryable_items")

    val items = List(
      ColoredItem(new ObjectId(), "Ruby", Color.Red),
      ColoredItem(new ObjectId(), "Sapphire", Color.Blue),
      ColoredItem(new ObjectId(), "Emerald", Color.Green),
      ColoredItem(new ObjectId(), "Rose", Color.Red)
    )

    collection.insertMany(items).toFuture().futureValue

    // Query for all red items
    val redItems = collection
      .find(Filters.equal(MongoPath.of[ColoredItem](_.color), Color.Red))
      .toFuture()
      .futureValue

    redItems should have size 2
    redItems.map(_.name) should contain allOf ("Ruby", "Rose")

    // Query for blue items
    val blueItems = collection
      .find(Filters.equal(MongoPath.of[ColoredItem](_.color), Color.Blue))
      .toFuture()
      .futureValue

    blueItems should have size 1
    blueItems.head.name shouldBe "Sapphire"

    database.drop().toFuture().futureValue
  }

  // ========== Test 11: Bulk Operations with Enums ==========
  it should "handle bulk operations with enums efficiently" in {
    val database: MongoDatabase = createDatabaseWithRegistry(levelRegistry)
    val collection: MongoCollection[UserProfile] = database.getCollection("bulk_profiles")

    val profiles = (1 to 100).map { i =>
      val level = i % 4 match
        case 0 => Level.Beginner
        case 1 => Level.Intermediate
        case 2 => Level.Advanced
        case 3 => Level.Expert

      UserProfile(new ObjectId(), s"user$i", level)
    }

    collection.insertMany(profiles).toFuture().futureValue

    val beginnerCount = collection
      .countDocuments(Filters.equal(MongoPath.of[UserProfile](_.level), Level.Beginner))
      .toFuture()
      .futureValue

    val expertCount = collection
      .countDocuments(Filters.equal(MongoPath.of[UserProfile](_.level), Level.Expert))
      .toFuture()
      .futureValue

    beginnerCount shouldBe 25
    expertCount shouldBe 25

    database.drop().toFuture().futureValue
  }

  // ========== Test 12: Update Operations with Enums ==========
  it should "handle update operations with enum fields" in {
    val database: MongoDatabase = createDatabaseWithRegistry(levelRegistry)
    val collection: MongoCollection[UserProfile] = database.getCollection("updatable_profiles")

    val profile = UserProfile(
      _id = new ObjectId(),
      username = "evolving_player",
      level = Level.Beginner
    )

    collection.insertOne(profile).toFuture().futureValue

    // Update the level
    val updatedProfile = profile.copy(level = Level.Expert)
    collection
      .replaceOne(
        Filters.equal(MongoPath.of[UserProfile](_._id), profile._id),
        updatedProfile
      )
      .toFuture()
      .futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[UserProfile](_._id), profile._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe updatedProfile
    retrieved.level shouldBe Level.Expert

    database.drop().toFuture().futureValue
  }

  override def afterAll(): Unit =
    container.stop()

end EnumIntegrationSpec
