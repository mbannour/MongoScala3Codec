package io.github.mbannour.mongo.dsl

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import io.github.mbannour.fields.MongoPath.syntax.?
import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.bson.types.ObjectId
import org.mongodb.scala.bson.annotations.BsonProperty
import org.mongodb.scala.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

/** Integration tests for the type-safe query/update DSL.
 *
 * Each test family verifies that DSL-generated [[org.bson.conversions.Bson]] documents are accepted
 * by the MongoDB Scala driver and produce the correct results against a live MongoDB instance
 * (started via TestContainers).
 */
class DslIntegrationSpec extends AnyFlatSpec
    with BeforeAndAfterAll
    with ForAllTestContainer
    with Matchers
    with ScalaFutures:

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  override val container: MongoDBContainer = MongoDBContainer("mongo:6.0.19")

  // ── Domain models ─────────────────────────────────────────────────────────

  case class Address(city: String, @BsonProperty("zip") zipCode: Int)

  case class Employee(
    _id: ObjectId,
    name: String,
    department: String,
    salary: Double,
    age: Int,
    active: Boolean,
    address: Option[Address],
    skills: List[String]
  )

  // ── Infrastructure ────────────────────────────────────────────────────────

  private lazy val mongoUri: String =
    s"mongodb://${container.containerIpAddress}:${container.mappedPort(27017)}"

  private lazy val registry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .register[Address]
      .register[Employee]
      .build

  private lazy val client: MongoClient   = MongoClient(mongoUri)
  private lazy val db: MongoDatabase     = client.getDatabase("dsl_test_db").withCodecRegistry(registry)
  private lazy val col: MongoCollection[Employee] = db.getCollection[Employee]("employees")

  // ── Seed data ─────────────────────────────────────────────────────────────

  private lazy val alice = Employee(new ObjectId(), "Alice", "Engineering", 90000.0, 30, active = true,  Some(Address("Paris",  75001)), List("scala", "mongodb"))
  private lazy val bob   = Employee(new ObjectId(), "Bob",   "Engineering", 75000.0, 25, active = true,  Some(Address("London", 10001)), List("java",  "kafka"))
  private lazy val carol = Employee(new ObjectId(), "Carol", "Marketing",   65000.0, 35, active = false, Some(Address("Paris",  75002)), List("marketing", "analytics"))
  private lazy val dave  = Employee(new ObjectId(), "Dave",  "Engineering", 85000.0, 28, active = true,  None,                          List("scala", "kafka"))

  override def beforeAll(): Unit =
    super.beforeAll()
    col.insertMany(Seq(alice, bob, carol, dave)).toFuture().futureValue

  override def afterAll(): Unit =
    db.drop().toFuture().futureValue
    client.close()
    super.afterAll()

  // ── Filter: equality and inequality ──────────────────────────────────────

  "field[T](_.f) ===" should "filter documents matching an exact field value" in:
    val results = col.find(field[Employee](_.name) === "Alice").toFuture().futureValue
    results.map(_.name) shouldBe List("Alice")

  "field[T](_.f) =/=" should "exclude documents matching the value" in:
    val results = col
      .find(field[Employee](_.department) =/= "Engineering")
      .toFuture().futureValue
    results.map(_.name) shouldBe List("Carol")

  // ── Filter: numeric comparisons ───────────────────────────────────────────

  "field[T](_.f) >" should "return documents where the field is above the threshold" in:
    val results = col
      .find(field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave") `and` (field[Employee](_.salary) > 80000.0))
      .toFuture().futureValue
    results.map(_.name) should contain allOf ("Alice", "Dave")
    results.map(_.name) should not contain "Bob"
    results.map(_.name) should not contain "Carol"

  "field[T](_.f) >=" should "include the boundary value" in:
    val results = col
      .find(field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave") `and` (field[Employee](_.age) >= 30))
      .toFuture().futureValue
    results.map(_.name) should contain allOf ("Alice", "Carol")

  "field[T](_.f) <" should "return documents where the field is below the threshold" in:
    val results = col
      .find(field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave") `and` (field[Employee](_.age) < 29))
      .toFuture().futureValue
    results.map(_.name) should contain("Bob")
    results.map(_.name) should not contain "Carol"

  "field[T](_.f) <=" should "include the boundary value" in:
    val results = col
      .find(field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave") `and` (field[Employee](_.age) <= 25))
      .toFuture().futureValue
    results.map(_.name) shouldBe List("Bob")

  // ── Filter: set membership ────────────────────────────────────────────────

  "Field.in" should "return documents whose field is one of the given values" in:
    val results = col
      .find(field[Employee](_.name).in("Alice", "Carol"))
      .toFuture().futureValue
    results.map(_.name) should contain allOf ("Alice", "Carol")
    results should have size 2

  "Field.nin" should "exclude documents whose field is one of the given values" in:
    val results = col
      .find(
        Filter.and(
          field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave"),
          field[Employee](_.name).nin("Alice", "Bob")
        )
      ).toFuture().futureValue
    results.map(_.name) should contain allOf ("Carol", "Dave")
    results should have size 2

  // ── Filter: logical combinators ───────────────────────────────────────────

  "Filter.and" should "combine multiple conditions with AND" in:
    val filter = Filter.and(
      field[Employee](_.department) === "Engineering",
      field[Employee](_.age) > 27,
      field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave")
    )
    val results = col.find(filter).toFuture().futureValue
    results.map(_.name) should contain allOf ("Alice", "Dave")
    results.map(_.name) should not contain "Bob"
    results.map(_.name) should not contain "Carol"

  "Filter.or" should "return documents matching any of the conditions" in:
    val filter = Filter.and(
      field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave"),
      Filter.or(
        field[Employee](_.active) === false,
        field[Employee](_.salary) > 88000.0
      )
    )
    val results = col.find(filter).toFuture().futureValue
    results.map(_.name) should contain allOf ("Alice", "Carol")
    results.map(_.name) should not contain "Bob"

  "Filter.not" should "negate a condition" in:
    val filter = Filter.and(
      field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave"),
      Filter.not(field[Employee](_.department) === "Engineering")
    )
    val results = col.find(filter).toFuture().futureValue
    results.map(_.name) shouldBe List("Carol")

  "Filter.nor" should "exclude documents matching any condition" in:
    val filter = Filter.and(
      field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave"),
      Filter.nor(
        field[Employee](_.department) === "Marketing",
        field[Employee](_.age) < 28
      )
    )
    val results = col.find(filter).toFuture().futureValue
    results.map(_.name) should contain allOf ("Alice", "Dave")

  // ── Filter: nested document paths ────────────────────────────────────────

  "Nested field via .?" should "filter on a sub-document field" in:
    val results = col
      .find(field[Employee](_.address.?.city) === "Paris")
      .toFuture().futureValue
    results.map(_.name) should contain allOf ("Alice", "Carol")
    results.map(_.name) should not contain "Bob"
    results.map(_.name) should not contain "Dave"

  "@BsonProperty in nested path" should "be resolved correctly in the generated filter" in:
    // Alice: zip 75001, Carol: zip 75002, Bob: zip 10001, Dave: no address
    val filter = Filter.and(
      field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave"),
      field[Employee](_.address.?.zipCode) > 75001
    )
    val results = col.find(filter).toFuture().futureValue
    results.map(_.name) shouldBe List("Carol")

  // ── Filter: existence and null checks ────────────────────────────────────

  "Field.exists(true)" should "match documents where the field is present" in:
    val results = col
      .find(
        Filter.and(
          field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave"),
          field[Employee](_.address).exists()
        )
      ).toFuture().futureValue
    results.map(_.name) should not contain "Dave"
    results should have size 3

  "Field.exists(false)" should "match documents where the field is absent" in:
    val results = col
      .find(
        Filter.and(
          field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave"),
          field[Employee](_.address).exists(false)
        )
      ).toFuture().futureValue
    results.map(_.name) shouldBe List("Dave")

  // ── Filter: regex ─────────────────────────────────────────────────────────

  "Field.regex" should "match documents whose field matches the pattern" in:
    val results = col
      .find(field[Employee](_.name).regex("^(Alice|Carol)$"))
      .toFuture().futureValue
    results.map(_.name) should contain allOf ("Alice", "Carol")
    results should have size 2

  "Field.regex with options" should "support case-insensitive matching" in:
    val results = col
      .find(field[Employee](_.name).regex("alice", "i"))
      .toFuture().futureValue
    results.map(_.name) shouldBe List("Alice")

  // ── Update: $set ─────────────────────────────────────────────────────────

  "Update :=" should "set a field to a new value" in:
    val id  = new ObjectId()
    val emp = Employee(id, "TestSet", "IT", 50000.0, 40, active = true, None, Nil)
    col.insertOne(emp).toFuture().futureValue
    col.updateOne(
      field[Employee](_._id) === id,
      field[Employee](_.department) := "Finance"
    ).toFuture().futureValue
    val updated = col.find(field[Employee](_._id) === id).first().toFuture().futureValue
    updated.department shouldBe "Finance"

  "Update :=" should "set a boolean field" in:
    val id  = new ObjectId()
    val emp = Employee(id, "TestBoolSet", "IT", 50000.0, 40, active = true, None, Nil)
    col.insertOne(emp).toFuture().futureValue
    col.updateOne(
      field[Employee](_._id) === id,
      field[Employee](_.active) := false
    ).toFuture().futureValue
    val updated = col.find(field[Employee](_._id) === id).first().toFuture().futureValue
    updated.active shouldBe false

  // ── Update: $inc ──────────────────────────────────────────────────────────

  "Update.inc" should "increment a numeric field" in:
    val id  = new ObjectId()
    val emp = Employee(id, "TestInc", "IT", 50000.0, 30, active = true, None, Nil)
    col.insertOne(emp).toFuture().futureValue
    col.updateOne(
      field[Employee](_._id) === id,
      field[Employee](_.age).inc(5)
    ).toFuture().futureValue
    val updated = col.find(field[Employee](_._id) === id).first().toFuture().futureValue
    updated.age shouldBe 35

  // ── Update: $unset ────────────────────────────────────────────────────────

  "Update.unset" should "remove a field from the document" in:
    val id  = new ObjectId()
    val emp = Employee(id, "TestUnset", "IT", 50000.0, 30, active = true, Some(Address("Tunis", 1000)), Nil)
    col.insertOne(emp).toFuture().futureValue
    col.updateOne(
      field[Employee](_._id) === id,
      field[Employee](_.address).unset
    ).toFuture().futureValue
    val updated = col.find(field[Employee](_._id) === id).first().toFuture().futureValue
    updated.address shouldBe None

  // ── Update: $push ─────────────────────────────────────────────────────────

  "Update.push" should "append an element to an array field" in:
    val id  = new ObjectId()
    val emp = Employee(id, "TestPush", "IT", 50000.0, 30, active = true, None, List("a", "b"))
    col.insertOne(emp).toFuture().futureValue
    col.updateOne(
      field[Employee](_._id) === id,
      field[Employee](_.skills).push("c")
    ).toFuture().futureValue
    val updated = col.find(field[Employee](_._id) === id).first().toFuture().futureValue
    updated.skills shouldBe List("a", "b", "c")

  // ── Update: $addToSet ─────────────────────────────────────────────────────

  "Update.addToSet" should "add an element only if not already present" in:
    val id  = new ObjectId()
    val emp = Employee(id, "TestAddToSet", "IT", 50000.0, 30, active = true, None, List("x"))
    col.insertOne(emp).toFuture().futureValue
    // Adding "x" again should be a no-op; adding "y" should succeed
    col.updateOne(field[Employee](_._id) === id, field[Employee](_.skills).addToSet("x")).toFuture().futureValue
    col.updateOne(field[Employee](_._id) === id, field[Employee](_.skills).addToSet("y")).toFuture().futureValue
    val updated = col.find(field[Employee](_._id) === id).first().toFuture().futureValue
    updated.skills should contain allOf ("x", "y")
    updated.skills.count(_ == "x") shouldBe 1

  // ── Update.combine ────────────────────────────────────────────────────────

  "Update.combine" should "merge $set and $inc into a single atomic update" in:
    val id  = new ObjectId()
    val emp = Employee(id, "TestCombine", "IT", 50000.0, 30, active = true, None, Nil)
    col.insertOne(emp).toFuture().futureValue
    col.updateOne(
      field[Employee](_._id) === id,
      Update.combine(
        field[Employee](_.department) := "Data",
        field[Employee](_.age).inc(1),
        field[Employee](_.salary) := 60000.0
      )
    ).toFuture().futureValue
    val updated = col.find(field[Employee](_._id) === id).first().toFuture().futureValue
    updated.department shouldBe "Data"
    updated.age        shouldBe 31
    updated.salary     shouldBe 60000.0

  "Update.combine" should "coalesce two $set operations on different fields" in:
    val id  = new ObjectId()
    val emp = Employee(id, "TestCoalesce", "IT", 50000.0, 30, active = true, None, Nil)
    col.insertOne(emp).toFuture().futureValue
    col.updateOne(
      field[Employee](_._id) === id,
      Update.combine(
        field[Employee](_.name)   := "Renamed",
        field[Employee](_.active) := false
      )
    ).toFuture().futureValue
    val updated = col.find(field[Employee](_._id) === id).first().toFuture().futureValue
    updated.name   shouldBe "Renamed"
    updated.active shouldBe false

  // ── Sort ──────────────────────────────────────────────────────────────────

  "Field.asc / Field.desc" should "order results ascending and descending" in:
    val baseFilter = field[Employee](_.name).in("Alice", "Bob", "Dave")
    val asc  = col.find(baseFilter).sort(field[Employee](_.age).asc).toFuture().futureValue
    val desc = col.find(baseFilter).sort(field[Employee](_.age).desc).toFuture().futureValue
    asc.map(_.name)  shouldBe List("Bob", "Dave", "Alice")
    desc.map(_.name) shouldBe List("Alice", "Dave", "Bob")

  "Sort.combine" should "sort by multiple fields" in:
    // department ASC then salary DESC → Engineering(Alice 90k, Dave 85k, Bob 75k), Marketing(Carol 65k)
    val sort   = Sort.combine(field[Employee](_.department).asc, field[Employee](_.salary).desc)
    val filter = field[Employee](_.name).in("Alice", "Bob", "Carol", "Dave")
    val result = col.find(filter).sort(sort).toFuture().futureValue
    result.map(_.name) shouldBe List("Alice", "Dave", "Bob", "Carol")

  // ── Projection ────────────────────────────────────────────────────────────

  "Projection.combine" should "include only the selected fields in raw BSON" in:
    val proj   = Projection.combine(
      field[Employee](_.name).include,
      field[Employee](_.salary).include,
      Projection.excludeId
    )
    val rawCol = db.getCollection("employees")
    val docs   = rawCol
      .find(field[Employee](_.name) === "Alice")
      .projection(proj)
      .toFuture().futureValue
    val doc = docs.head
    doc.containsKey("name")       shouldBe true
    doc.containsKey("salary")     shouldBe true
    doc.containsKey("_id")        shouldBe false
    doc.containsKey("age")        shouldBe false
    doc.containsKey("department") shouldBe false

  "Projection.exclude" should "drop specific fields from the result" in:
    val proj   = Projection.combine(
      field[Employee](_.skills).exclude,
      field[Employee](_.address).exclude
    )
    val rawCol = db.getCollection("employees")
    val docs   = rawCol
      .find(field[Employee](_.name) === "Alice")
      .projection(proj)
      .toFuture().futureValue
    val doc = docs.head
    doc.containsKey("skills")  shouldBe false
    doc.containsKey("address") shouldBe false
    doc.containsKey("name")    shouldBe true
    doc.containsKey("age")     shouldBe true

  // ── Codec + DSL integration ───────────────────────────────────────────────

  "DSL filters" should "integrate transparently with RegistryBuilder codec round-trips" in:
    // Ensure that a document written via a typed collection can be found via DSL filter
    val id   = new ObjectId()
    val emp  = Employee(id, "Integration", "CrossCheck", 99999.0, 99, active = true, Some(Address("Geneva", 1200)), List("dsl"))
    col.insertOne(emp).toFuture().futureValue

    val found = col
      .find(
        Filter.and(
          field[Employee](_._id)           === id,
          field[Employee](_.department)    === "CrossCheck",
          field[Employee](_.address.?.city) === "Geneva"
        )
      ).first().toFuture().futureValue

    found._id       shouldBe id
    found.name      shouldBe "Integration"
    found.salary    shouldBe 99999.0
    found.skills    shouldBe List("dsl")

  // ── @BsonProperty integration ─────────────────────────────────────────────

  "@BsonProperty" should "be respected in both codec storage and DSL filter paths" in:
    // Address.zipCode is stored as "zip" in MongoDB due to @BsonProperty.
    // The DSL resolves field[Employee](_.address.?.zipCode) to "address.zip"
    // so the filter must match the actual stored key.
    val id   = new ObjectId()
    val emp  = Employee(id, "ZipTest", "IT", 50000.0, 30, active = true, Some(Address("Rome", 99999)), Nil)
    col.insertOne(emp).toFuture().futureValue

    val found = col
      .find(
        Filter.and(
          field[Employee](_._id)                === id,
          field[Employee](_.address.?.zipCode)   === 99999
        )
      ).first().toFuture().futureValue

    found.address.get.zipCode shouldBe 99999

  // ── Helper extension for cleaner test expressions ─────────────────────────

  extension (left: org.bson.conversions.Bson)
    private infix def and(right: org.bson.conversions.Bson): org.bson.conversions.Bson =
      Filter.and(left, right)

end DslIntegrationSpec
