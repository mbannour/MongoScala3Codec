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

end RegistryBuilderIntegrationSpec
