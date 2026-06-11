package io.github.mbannour.mongo.repository

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import io.github.mbannour.mongo.codecs.RegistryBuilder
import io.github.mbannour.mongo.dsl.*
import org.bson.types.ObjectId
import org.mongodb.scala.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global

/** Integration tests for [[MongoRepository]] against a live MongoDB.
 *
 * Exercises the full stack end-to-end: compile-time codec generation (`RegistryBuilder`),
 * the type-safe DSL (`field`, `Filter`, `Update`, `Sort`), and the effect-agnostic repository
 * (here specialised to `scala.concurrent.Future`).
 */
class RepositoryIntegrationSpec
    extends AnyFlatSpec
    with BeforeAndAfterAll
    with ForAllTestContainer
    with Matchers
    with ScalaFutures:

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  override val container: MongoDBContainer = MongoDBContainer("mongo:6.0.19")

  // ── Domain ──────────────────────────────────────────────────────────────────

  case class Employee(
    _id: ObjectId,
    name: String,
    department: String,
    salary: Double,
    active: Boolean,
    skills: List[String]
  )

  // ── Infrastructure ────────────────────────────────────────────────────────

  private lazy val mongoUri: String =
    s"mongodb://${container.containerIpAddress}:${container.mappedPort(27017)}"

  private lazy val registry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .register[Employee]
      .build

  private lazy val client: MongoClient = MongoClient(mongoUri)
  private lazy val db: MongoDatabase   = client.getDatabase("repo_test_db").withCodecRegistry(registry)

  private lazy val repo: MongoRepository[scala.concurrent.Future, Employee, ObjectId] =
    MongoRepository(db.getCollection[Employee]("employees"), field[Employee](_._id))

  private val aliceId = new ObjectId()
  private val bobId   = new ObjectId()
  private val carolId = new ObjectId()

  private val alice = Employee(aliceId, "Alice", "Engineering", 90000.0, active = true,  List("scala", "mongodb"))
  private val bob   = Employee(bobId,   "Bob",   "Engineering", 75000.0, active = true,  List("java", "kafka"))
  private val carol = Employee(carolId, "Carol", "Marketing",   65000.0, active = false, List("seo"))

  override def beforeAll(): Unit =
    super.beforeAll()
    repo.insertMany(Seq(alice, bob, carol)).futureValue

  override def afterAll(): Unit =
    db.drop().toFuture().futureValue
    client.close()
    super.afterAll()

  // ── Tests ───────────────────────────────────────────────────────────────────

  "findById" should "return the document with the matching _id" in:
    repo.findById(aliceId).futureValue.map(_.name) shouldBe Some("Alice")

  it should "return None for an unknown id" in:
    repo.findById(new ObjectId()).futureValue shouldBe None

  "find with a DSL filter and sort" should "return matching documents in order" in:
    val results = repo.find(
      filter = field[Employee](_.department) === "Engineering",
      sort   = Some(field[Employee](_.salary).desc)
    ).futureValue
    results.map(_.name) shouldBe Seq("Alice", "Bob")

  "find with a compound filter" should "honour Filter.and" in:
    val results = repo.find(
      Filter.and(
        field[Employee](_.department) === "Engineering",
        field[Employee](_.salary) > 80_000.0
      )
    ).futureValue
    results.map(_.name) shouldBe Seq("Alice")

  "count" should "count matching documents" in:
    repo.count(field[Employee](_.department) === "Engineering").futureValue shouldBe 2L

  "exists" should "be true when a match exists and false otherwise" in:
    repo.exists(field[Employee](_.name) === "Bob").futureValue shouldBe true
    repo.exists(field[Employee](_.name) === "Nobody").futureValue shouldBe false

  "updateById" should "apply a DSL update and report the modified count" in:
    val modified = repo.updateById(
      bobId,
      Update.combine(field[Employee](_.salary).mul(1.10), field[Employee](_.active) := true)
    ).futureValue
    modified shouldBe 1L
    repo.findById(bobId).futureValue.map(_.salary) shouldBe Some(75000.0 * 1.10)

  "findAndUpdateById" should "return the post-update document" in:
    val updated = repo.findAndUpdateById(
      carolId,
      Update.combine(field[Employee](_.skills).addToSet("analytics"))
    ).futureValue
    updated.map(_.skills) shouldBe Some(List("seo", "analytics"))

  "deleteById" should "remove the document and report the deleted count" in:
    val toDelete = new ObjectId()
    repo.insert(Employee(toDelete, "Temp", "Temp", 1.0, active = false, Nil)).futureValue
    repo.deleteById(toDelete).futureValue shouldBe 1L
    repo.findById(toDelete).futureValue shouldBe None

  "upsertById" should "insert when absent and update when present" in:
    val id = new ObjectId()
    repo.upsertById(
      id,
      Update.combine(
        field[Employee](_.name)       := "Dave",
        field[Employee](_.department) := "Sales",
        field[Employee](_.salary)     := 50_000.0,
        field[Employee](_.active)     := true,
        field[Employee](_.skills).setOnInsert(List("crm"))
      )
    ).futureValue
    repo.findById(id).futureValue.map(_.name) shouldBe Some("Dave")
