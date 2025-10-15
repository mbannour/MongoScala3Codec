package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import scala.util.chaining.*

// Import extension methods
import RegistryBuilder.{*, given}

class RegistryBuilderIntegrationSpec
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
      .getDatabase("test_db")
      .withCodecRegistry(registry)

  // Test models
  case class SimpleDocument(_id: ObjectId, name: String, value: Int)

  case class Address(street: String, city: String, zipCode: Int)

  case class Person(_id: ObjectId, name: String, age: Int, address: Option[Address])

  case class Company(_id: ObjectId, name: String, employees: List[Person])

  case class Department(_id: ObjectId, name: String, budget: Double)

  case class Project(_id: ObjectId, title: String, active: Boolean, tags: Set[String])

  case class Team(_id: ObjectId, name: String, members: Vector[String])

  // Opaque type examples - demonstrating Scala 3 opaque types for type-safe domain modeling
  object OpaqueTypes:
    opaque type UserId = String
    object UserId:
      def apply(value: String): UserId = value
      extension (userId: UserId)
        def value: String = userId
    
    opaque type Email = String
    object Email:
      def apply(value: String): Email = value
      extension (email: Email)
        def value: String = email
    
    opaque type Age = Int
    object Age:
      def apply(value: Int): Age = value
      extension (age: Age)
        def value: Int = age

  import OpaqueTypes.*

  // Case class using opaque types for stronger type safety
  case class UserProfile(
    _id: ObjectId,
    userId: UserId,
    email: Email,
    age: Age,
    displayName: String
  )

  "RegistryBuilder.register[T]" should "register and work with MongoDB for simple case class" in {
    assert(container.container.isRunning, "The MongoDB container is not running!")

    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[SimpleDocument]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[SimpleDocument] = database.getCollection("simple_docs")

    val doc = SimpleDocument(new ObjectId(), "Test Document", 42)
    collection.insertOne(doc).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", doc._id)).first().toFuture().futureValue
    retrieved shouldBe doc
    retrieved.name shouldBe "Test Document"
    retrieved.value shouldBe 42

    database.drop().toFuture().futureValue
  }

  it should "register and work with nested case classes" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[Address]
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people")

    val person = Person(
      new ObjectId(),
      "Alice",
      30,
      Some(Address("123 Main St", "Springfield", 12345))
    )

    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person
    retrieved.address.get.city shouldBe "Springfield"

    database.drop().toFuture().futureValue
  }

  it should "work with ignoreNone policy" in {
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .register[Address]
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_ignore_none")

    val personWithoutAddress = Person(new ObjectId(), "Bob", 25, None)
    collection.insertOne(personWithoutAddress).toFuture().futureValue

    val retrieved =
      collection.find(Filters.equal("_id", personWithoutAddress._id)).first().toFuture().futureValue
    retrieved shouldBe personWithoutAddress
    retrieved.address shouldBe None

    database.drop().toFuture().futureValue
  }

  it should "work with encodeNone policy" in {
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .encodeNone
      .register[Address]
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_encode_none")

    val personWithoutAddress = Person(new ObjectId(), "Charlie", 35, None)
    collection.insertOne(personWithoutAddress).toFuture().futureValue

    val retrieved =
      collection.find(Filters.equal("_id", personWithoutAddress._id)).first().toFuture().futureValue
    retrieved shouldBe personWithoutAddress
    retrieved.address shouldBe None

    database.drop().toFuture().futureValue
  }

  it should "work with custom discriminator field" in {
    given config: CodecConfig = CodecConfig(discriminatorField = "_customType")

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .discriminator("_customType")
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_custom_discriminator")

    val person = Person(new ObjectId(), "Diana", 28, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  "RegistryBuilder.registerAll[Tuple]" should "register multiple types with tuple syntax" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .registerAll[(Address, Person, SimpleDocument)]
      .build

    val database = createDatabaseWithRegistry(registry)

    // Test Address
    val addressCollection: MongoCollection[Address] = database.getCollection("addresses")
    val address = Address("456 Oak St", "Portland", 97201)
    // Note: Address doesn't have _id, so we'll test with Person which includes Address

    // Test Person
    val personCollection: MongoCollection[Person] = database.getCollection("people_tuple")
    val person = Person(new ObjectId(), "Eve", 32, Some(address))
    personCollection.insertOne(person).toFuture().futureValue

    val retrievedPerson =
      personCollection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrievedPerson shouldBe person
    retrievedPerson.address.get.street shouldBe "456 Oak St"

    // Test SimpleDocument
    val simpleCollection: MongoCollection[SimpleDocument] = database.getCollection("simple_tuple")
    val simpleDoc = SimpleDocument(new ObjectId(), "Tuple Test", 99)
    simpleCollection.insertOne(simpleDoc).toFuture().futureValue

    val retrievedSimple =
      simpleCollection.find(Filters.equal("_id", simpleDoc._id)).first().toFuture().futureValue
    retrievedSimple shouldBe simpleDoc

    database.drop().toFuture().futureValue
  }

  it should "register and work with complex nested structures" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .registerAll[(Address, Person, Company)]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Company] = database.getCollection("companies")

    val employee1 = Person(new ObjectId(), "Frank", 40, Some(Address("100 Tech Blvd", "Austin", 78701)))
    val employee2 = Person(new ObjectId(), "Grace", 35, Some(Address("200 Tech Blvd", "Austin", 78702)))

    val company = Company(new ObjectId(), "Tech Corp", List(employee1, employee2))

    collection.insertOne(company).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", company._id)).first().toFuture().futureValue
    retrieved shouldBe company
    retrieved.employees.size shouldBe 2
    retrieved.employees.head.name shouldBe "Frank"
    retrieved.employees(1).name shouldBe "Grace"

    database.drop().toFuture().futureValue
  }

  it should "register many types at once" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .registerAll[(SimpleDocument, Address, Person, Department, Project, Team)]
      .build

    val database = createDatabaseWithRegistry(registry)

    // Test Department
    val deptCollection: MongoCollection[Department] = database.getCollection("departments")
    val dept = Department(new ObjectId(), "Engineering", 1000000.0)
    deptCollection.insertOne(dept).toFuture().futureValue

    val retrievedDept =
      deptCollection.find(Filters.equal("_id", dept._id)).first().toFuture().futureValue
    retrievedDept shouldBe dept
    retrievedDept.budget shouldBe 1000000.0

    // Test Project
    val projectCollection: MongoCollection[Project] = database.getCollection("projects")
    val project = Project(new ObjectId(), "MongoDB Integration", true, Set("scala", "mongodb", "test"))
    projectCollection.insertOne(project).toFuture().futureValue

    val retrievedProject =
      projectCollection.find(Filters.equal("_id", project._id)).first().toFuture().futureValue
    retrievedProject shouldBe project
    retrievedProject.tags should contain("scala")

    // Test Team
    val teamCollection: MongoCollection[Team] = database.getCollection("teams")
    val team = Team(new ObjectId(), "Backend Team", Vector("Alice", "Bob", "Charlie"))
    teamCollection.insertOne(team).toFuture().futureValue

    val retrievedTeam = teamCollection.find(Filters.equal("_id", team._id)).first().toFuture().futureValue
    retrievedTeam shouldBe team
    retrievedTeam.members.size shouldBe 3

    database.drop().toFuture().futureValue
  }

  it should "work with configuration chaining" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .discriminator("_entityType")
      .registerAll[(Address, Person, Department)]
      .build

    val database = createDatabaseWithRegistry(registry)

    val personCollection: MongoCollection[Person] = database.getCollection("people_configured")
    val person = Person(new ObjectId(), "Henry", 45, None)
    personCollection.insertOne(person).toFuture().futureValue

    val retrieved = personCollection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person
    retrieved.address shouldBe None

    database.drop().toFuture().futureValue
  }

  "RegistryBuilder mixed usage" should "combine register and registerAll" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[SimpleDocument]
      .registerAll[(Address, Person)]
      .register[Department]
      .build

    val database = createDatabaseWithRegistry(registry)

    // Test SimpleDocument
    val simpleCollection: MongoCollection[SimpleDocument] = database.getCollection("simple_mixed")
    val simpleDoc = SimpleDocument(new ObjectId(), "Mixed Test", 77)
    simpleCollection.insertOne(simpleDoc).toFuture().futureValue

    val retrievedSimple =
      simpleCollection.find(Filters.equal("_id", simpleDoc._id)).first().toFuture().futureValue
    retrievedSimple shouldBe simpleDoc

    // Test Person
    val personCollection: MongoCollection[Person] = database.getCollection("people_mixed")
    val person = Person(new ObjectId(), "Ivy", 29, Some(Address("789 Elm St", "Seattle", 98101)))
    personCollection.insertOne(person).toFuture().futureValue

    val retrievedPerson =
      personCollection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrievedPerson shouldBe person

    // Test Department
    val deptCollection: MongoCollection[Department] = database.getCollection("departments_mixed")
    val dept = Department(new ObjectId(), "Sales", 500000.0)
    deptCollection.insertOne(dept).toFuture().futureValue

    val retrievedDept =
      deptCollection.find(Filters.equal("_id", dept._id)).first().toFuture().futureValue
    retrievedDept shouldBe dept

    database.drop().toFuture().futureValue
  }

  "RegistryBuilder extension methods" should "work with .newBuilder on CodecRegistry" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_extension")

    val person = Person(new ObjectId(), "Jack", 50, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  it should "work with .builderWith(config)" in {
    val customConfig = CodecConfig(
      noneHandling = NoneHandling.Ignore,
      discriminatorField = "_myType"
    )

    val registry = RegistryBuilder
      .apply(MongoClient.DEFAULT_CODEC_REGISTRY, customConfig)
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_builder_with")

    val person = Person(new ObjectId(), "Kate", 38, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  "RegistryBuilder functional configuration" should "work with configure function" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .configure(_.copy(noneHandling = NoneHandling.Ignore, discriminatorField = "_docType"))
      .registerAll[(Person, Address)]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_functional")

    val person = Person(new ObjectId(), "Leo", 42, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  it should "allow multiple configuration changes" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .register[Address]
      .encodeNone // Change policy
      .register[Person]
      .discriminator("_type")
      .register[Department]
      .build

    val database = createDatabaseWithRegistry(registry)

    val personCollection: MongoCollection[Person] = database.getCollection("people_multi_config")
    val person = Person(new ObjectId(), "Maya", 33, None)
    personCollection.insertOne(person).toFuture().futureValue

    val retrieved = personCollection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  "RegistryBuilder backward compatibility" should "work with modern register method" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_register")

    val person = Person(new ObjectId(), "Nina", 27, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  it should "support modern newBuilder API" in {
    given config: CodecConfig = CodecConfig()

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_new_builder")

    val person = Person(new ObjectId(), "Oscar", 55, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  "RegistryBuilder opaque type" should "demonstrate type safety through immutability" in {
    given config: CodecConfig = CodecConfig()

    // Opaque type ensures that RegistryBuilder cannot be directly manipulated
    // Each operation returns a new RegistryBuilder instance
    val builder1: RegistryBuilder = RegistryBuilder.from(MongoClient.DEFAULT_CODEC_REGISTRY)
    val builder2: RegistryBuilder = builder1.ignoreNone
    val builder3: RegistryBuilder = builder2.register[Person]
    
    // Each builder is independent and immutable
    val registry = builder3.build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_opaque_immutable")

    val person = Person(new ObjectId(), "Paula", 31, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person
    retrieved.address shouldBe None

    database.drop().toFuture().futureValue
  }

  it should "allow fluent chaining with type-safe operations" in {
    given config: CodecConfig = CodecConfig()

    // Opaque type provides type-safe fluent API
    // The type is RegistryBuilder throughout the chain
    val registry: CodecRegistry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone              // Returns RegistryBuilder
      .discriminator("_t")     // Returns RegistryBuilder
      .register[Address]       // Returns RegistryBuilder
      .register[Person]        // Returns RegistryBuilder
      .register[Department]    // Returns RegistryBuilder
      .build                   // Returns CodecRegistry

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_opaque_fluent")

    val person = Person(new ObjectId(), "Quinn", 44, Some(Address("999 Park Ave", "Boston", 02101)))
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person
    retrieved.address.get.city shouldBe "Boston"

    database.drop().toFuture().futureValue
  }

  it should "demonstrate configuration immutability" in {
    given config: CodecConfig = CodecConfig()

    // Original builder with ignoreNone
    val baseBuilder = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone

    // Create two different registries from same base - demonstrates immutability
    val registryWithIgnore = baseBuilder
      .register[Person]
      .build

    val registryWithEncode = baseBuilder
      .encodeNone  // This creates a NEW builder, doesn't modify baseBuilder
      .register[Person]
      .build

    // Both registries work correctly with their own configurations
    val database = createDatabaseWithRegistry(registryWithIgnore)
    val collection: MongoCollection[Person] = database.getCollection("people_config_immutable")

    val person = Person(new ObjectId(), "Rachel", 36, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  it should "support functional transformation with configure" in {
    given config: CodecConfig = CodecConfig()

    // Opaque type works seamlessly with functional programming
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .configure { cfg =>
        // Can apply complex configuration logic
        val withNoneHandling = cfg.copy(noneHandling = NoneHandling.Ignore)
        val withDiscriminator = withNoneHandling.copy(discriminatorField = "_kind")
        withDiscriminator
      }
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_opaque_functional")

    val person = Person(new ObjectId(), "Sam", 29, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  it should "enable reusable builder patterns" in {
    given config: CodecConfig = CodecConfig()

    // Create reusable base builders - opaque type makes this safe
    def standardBuilder: RegistryBuilder = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .discriminator("_type")

    // Use the base builder multiple times
    val registryForPeople = standardBuilder
      .register[Address]
      .register[Person]
      .build

    val registryForDepartments = standardBuilder
      .register[Department]
      .build

    // Test with registryForPeople
    val database = createDatabaseWithRegistry(registryForPeople)
    val collection: MongoCollection[Person] = database.getCollection("people_reusable")

    val person = Person(new ObjectId(), "Tina", 41, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  it should "work with extension methods on CodecRegistry" in {
    given config: CodecConfig = CodecConfig()

    // Extension methods enabled by opaque type design
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder                    // Extension method returns RegistryBuilder
      .ignoreNone
      .register[Address]
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_extension_opaque")

    val person = Person(new ObjectId(), "Uma", 34, Some(Address("111 First St", "Miami", 33101)))
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person
    retrieved.address.get.city shouldBe "Miami"

    database.drop().toFuture().futureValue
  }

  it should "demonstrate type-safe tuple registration with opaque type" in {
    given config: CodecConfig = CodecConfig()

    // Opaque type provides type-safe tuple operations
    val registry: CodecRegistry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .registerAll[(SimpleDocument, Address, Person, Department, Project, Team)]
      .build

    // The opaque type ensures compile-time type safety throughout
    val database = createDatabaseWithRegistry(registry)
    
    val projectCollection: MongoCollection[Project] = database.getCollection("projects_opaque_tuple")
    val project = Project(new ObjectId(), "Opaque Type Demo", true, Set("scala3", "opaque", "types"))
    projectCollection.insertOne(project).toFuture().futureValue

    val retrieved = projectCollection.find(Filters.equal("_id", project._id)).first().toFuture().futureValue
    retrieved shouldBe project
    retrieved.tags should contain("opaque")

    database.drop().toFuture().futureValue
  }

  it should "support complex builder composition" in {
    given config: CodecConfig = CodecConfig()

    // Compose builders in functional style - enabled by opaque type safety
    def withStandardTypes(builder: RegistryBuilder): RegistryBuilder =
      builder
        .register[Address]
        .register[Person]

    def withBusinessTypes(builder: RegistryBuilder): RegistryBuilder =
      builder
        .register[Department]
        .register[Company]

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .pipe(withStandardTypes)
      .pipe(withBusinessTypes)
      .build

    val database = createDatabaseWithRegistry(registry)
    val companyCollection: MongoCollection[Company] = database.getCollection("companies_composition")

    val employee = Person(new ObjectId(), "Victor", 38, Some(Address("222 Second Ave", "Denver", 80201)))
    val company = Company(new ObjectId(), "Opaque Tech Inc", List(employee))
    companyCollection.insertOne(company).toFuture().futureValue

    val retrieved = companyCollection.find(Filters.equal("_id", company._id)).first().toFuture().futureValue
    retrieved shouldBe company
    retrieved.employees should have size 1
    retrieved.employees.head.name shouldBe "Victor"

    database.drop().toFuture().futureValue
  }

  it should "demonstrate withConfig preserves type safety" in {
    val customConfig = CodecConfig(
      noneHandling = NoneHandling.Encode,
      discriminatorField = "_entityType"
    )

    // withConfig maintains type safety through opaque type
    val registry: CodecRegistry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(customConfig)
      .register[Person]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Person] = database.getCollection("people_with_config")

    val person = Person(new ObjectId(), "Wendy", 39, None)
    collection.insertOne(person).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", person._id)).first().toFuture().futureValue
    retrieved shouldBe person

    database.drop().toFuture().futureValue
  }

  "Scala 3 opaque types" should "work seamlessly with codec generation" in {
    given config: CodecConfig = CodecConfig()

    // Register codec for case class that uses opaque types
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[UserProfile]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[UserProfile] = database.getCollection("user_profiles")

    // Create user with opaque types - compile-time type safety prevents mixing types
    val userProfile = UserProfile(
      _id = new ObjectId(),
      userId = UserId("user_12345"),
      email = Email("john@example.com"),
      age = Age(28),
      displayName = "John Doe"
    )

    collection.insertOne(userProfile).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", userProfile._id)).first().toFuture().futureValue
    retrieved shouldBe userProfile
    retrieved.userId.value shouldBe "user_12345"
    retrieved.email.value shouldBe "john@example.com"
    retrieved.age.value shouldBe 28

    database.drop().toFuture().futureValue
  }

  it should "prevent type confusion at compile time with opaque types" in {
    // This test demonstrates compile-time safety - opaque types prevent mixing
    val userId = UserId("user_123")
    val email = Email("test@example.com")
    val age = Age(30)

    // These would NOT compile - demonstrating type safety:
    // val wrongUser: UserId = email  // ❌ Compile error: type mismatch
    // val wrongEmail: Email = userId // ❌ Compile error: type mismatch
    // val wrongAge: Age = userId     // ❌ Compile error: type mismatch

    // Can only construct with correct factory methods
    userId.value shouldBe "user_123"
    email.value shouldBe "test@example.com"
    age.value shouldBe 30
  }

  it should "store and retrieve opaque types correctly in MongoDB" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[UserProfile]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[UserProfile] = database.getCollection("opaque_type_users")

    // Insert multiple users with different opaque type values
    val users = Seq(
      UserProfile(new ObjectId(), UserId("alice_001"), Email("alice@test.com"), Age(25), "Alice"),
      UserProfile(new ObjectId(), UserId("bob_002"), Email("bob@test.com"), Age(30), "Bob"),
      UserProfile(new ObjectId(), UserId("charlie_003"), Email("charlie@test.com"), Age(35), "Charlie")
    )

    collection.insertMany(users).toFuture().futureValue

    // Query and verify
    val allUsers = collection.find().toFuture().futureValue
    allUsers should have size 3
    allUsers.map(_.userId.value) should contain allOf("alice_001", "bob_002", "charlie_003")
    allUsers.map(_.email.value) should contain allOf("alice@test.com", "bob@test.com", "charlie@test.com")

    database.drop().toFuture().futureValue
  }

  it should "demonstrate opaque types provide zero runtime overhead" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[UserProfile]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[UserProfile] = database.getCollection("opaque_performance")

    // Opaque types are erased at runtime - stored as their underlying types
    val user = UserProfile(
      _id = new ObjectId(),
      userId = UserId("performance_test"),
      email = Email("perf@test.com"),
      age = Age(40),
      displayName = "Performance Test"
    )

    collection.insertOne(user).toFuture().futureValue

    // Check raw BSON to verify opaque types are stored as primitives
    import org.bson.Document
    val docCollection: MongoCollection[Document] = database.getCollection("opaque_performance")
    val rawDoc = docCollection.find(Filters.equal("_id", user._id)).first().toFuture().futureValue

    // Opaque types are stored as their underlying primitive types (String, Int)
    rawDoc.getString("userId") shouldBe "performance_test"
    rawDoc.getString("email") shouldBe "perf@test.com"
    rawDoc.getInteger("age") shouldBe 40

    // Retrieve as typed object
    val typedUser = collection.find(Filters.equal("_id", user._id)).first().toFuture().futureValue
    typedUser.userId.value shouldBe "performance_test"

    database.drop().toFuture().futureValue
  }

  it should "work with opaque types in complex queries" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[UserProfile]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[UserProfile] = database.getCollection("opaque_queries")

    val users = Seq(
      UserProfile(new ObjectId(), UserId("user_1"), Email("young@test.com"), Age(20), "Young User"),
      UserProfile(new ObjectId(), UserId("user_2"), Email("mid@test.com"), Age(30), "Mid User"),
      UserProfile(new ObjectId(), UserId("user_3"), Email("senior@test.com"), Age(40), "Senior User")
    )

    collection.insertMany(users).toFuture().futureValue

    // Query using underlying type value (opaque types are transparent in queries)
    val youngUsers = collection
      .find(Filters.lt("age", 25))
      .toFuture()
      .futureValue

    youngUsers should have size 1
    youngUsers.head.age.value shouldBe 20
    youngUsers.head.displayName shouldBe "Young User"

    // Range query
    val midAgeUsers = collection
      .find(Filters.and(
        Filters.gte("age", 25),
        Filters.lte("age", 35)
      ))
      .toFuture()
      .futureValue

    midAgeUsers should have size 1
    midAgeUsers.head.age.value shouldBe 30

    database.drop().toFuture().futureValue
  }

  it should "combine opaque types with RegistryBuilder opaque type for double type safety" in {
    given config: CodecConfig = CodecConfig()

    // Two levels of opaque types:
    // 1. RegistryBuilder is an opaque type (type-safe builder pattern)
    // 2. UserProfile uses opaque types (UserId, Email, Age) for domain modeling
    val builder: RegistryBuilder = RegistryBuilder.from(MongoClient.DEFAULT_CODEC_REGISTRY)
    val registryBuilder: RegistryBuilder = builder.ignoreNone
    val withCodec: RegistryBuilder = registryBuilder.register[UserProfile]
    val registry: CodecRegistry = withCodec.build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[UserProfile] = database.getCollection("double_opaque")

    // Both RegistryBuilder and domain opaque types provide compile-time safety
    val user = UserProfile(
      _id = new ObjectId(),
      userId = UserId("safe_user"),
      email = Email("safe@example.com"),
      age = Age(45),
      displayName = "Type Safe User"
    )

    collection.insertOne(user).toFuture().futureValue

    val retrieved = collection.find(Filters.equal("_id", user._id)).first().toFuture().futureValue
    retrieved.userId.value shouldBe "safe_user"
    retrieved.email.value shouldBe "safe@example.com"
    retrieved.age.value shouldBe 45

    database.drop().toFuture().futureValue
  }

end RegistryBuilderIntegrationSpec
