package io.github.mbannour.mongo.dsl

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.bson.{BsonDocument, BsonValue}
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.mongodb.scala.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

/** Integration tests for the aggregation pipeline DSL.
 *
 * Covers all [[Stage]] builders, [[Accumulator]] expressions, [[Expr]] expressions,
 * and [[Pipeline]] composition against a live MongoDB 6.0 instance via TestContainers.
 */
class AggregationIntegrationSpec extends AnyFlatSpec
    with BeforeAndAfterAll
    with ForAllTestContainer
    with Matchers
    with ScalaFutures:

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  override val container: MongoDBContainer = MongoDBContainer("mongo:6.0.19")

  // ── Domain models ──────────────────────────────────────────────────────────

  case class Address(city: String)

  case class Employee(
    _id: ObjectId,
    name: String,
    dept: String,
    salary: Int,
    age: Int,
    active: Boolean,
    tags: List[String],
    address: Option[Address]
  )

  case class Dept(name: String, budget: Int)

  // ── Infrastructure ─────────────────────────────────────────────────────────

  private lazy val mongoUri: String =
    s"mongodb://${container.containerIpAddress}:${container.mappedPort(27017)}"

  private lazy val registry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .register[Address]
      .register[Employee]
      .register[Dept]
      .build

  private lazy val client: MongoClient  = MongoClient(mongoUri)
  private lazy val db: MongoDatabase    = client.getDatabase("agg_test_db").withCodecRegistry(registry)

  private lazy val empCol: MongoCollection[Employee] = db.getCollection[Employee]("employees")
  private lazy val deptCol: MongoCollection[Dept]    = db.getCollection[Dept]("departments")
  private lazy val rawEmp: MongoCollection[Document] = db.getCollection("employees")
  private lazy val rawDept: MongoCollection[Document] = db.getCollection("departments")

  // ── Seed data ──────────────────────────────────────────────────────────────
  // Engineering: Alice 90k/30, Bob 75k/25, Dave 85k/28(no address)
  // Marketing  : Carol 65k/35(inactive), Eve 72k/29

  private lazy val alice = Employee(new ObjectId(), "Alice", "Engineering", 90000, 30, active = true,
    List("scala", "mongodb", "kafka"), Some(Address("Paris")))
  private lazy val bob   = Employee(new ObjectId(), "Bob",   "Engineering", 75000, 25, active = true,
    List("java", "kafka"),             Some(Address("London")))
  private lazy val carol = Employee(new ObjectId(), "Carol", "Marketing",   65000, 35, active = false,
    List("marketing", "analytics"),   Some(Address("Paris")))
  private lazy val dave  = Employee(new ObjectId(), "Dave",  "Engineering", 85000, 28, active = true,
    List("scala", "kafka", "spark"),  None)
  private lazy val eve   = Employee(new ObjectId(), "Eve",   "Marketing",   72000, 29, active = true,
    List("marketing", "seo"),         Some(Address("Berlin")))

  override def beforeAll(): Unit =
    super.beforeAll()
    empCol.insertMany(Seq(alice, bob, carol, dave, eve)).toFuture().futureValue
    deptCol.insertMany(Seq(Dept("Engineering", 500000), Dept("Marketing", 300000))).toFuture().futureValue

  override def afterAll(): Unit =
    db.drop().toFuture().futureValue
    client.close()
    super.afterAll()

  // ── Helpers ────────────────────────────────────────────────────────────────

  private def agg(stages: Bson*): Seq[Document] =
    rawEmp.aggregate(stages.toSeq).toFuture().futureValue

  private def numInt(doc: Document, key: String): Int =
    doc(key).asNumber().intValue()

  private def numDbl(doc: Document, key: String): Double =
    doc(key).asNumber().doubleValue()

  private def str(doc: Document, key: String): String =
    doc(key).asString().getValue

  private def arr(doc: Document, key: String): Seq[BsonValue] =
    import scala.jdk.CollectionConverters.*
    doc(key).asArray().getValues.asScala.toSeq

  // ── Stage.$match ───────────────────────────────────────────────────────────

  "Stage.match" should "filter documents inside a pipeline" in:
    val results = agg(Stage.`match`(field[Employee](_.dept) === "Engineering"))
    results.map(d => str(d, "name")) should contain theSameElementsAs Seq("Alice", "Bob", "Dave")

  it should "filter with a compound DSL filter" in:
    val results = agg(
      Stage.`match`(Filter.and(
        field[Employee](_.dept) === "Engineering",
        field[Employee](_.salary) > 80000
      ))
    )
    results.map(d => str(d, "name")) should contain theSameElementsAs Seq("Alice", "Dave")

  it should "support $expr with Expr comparison operators" in:
    val results = agg(
      Stage.`match`(new BsonDocument("$expr",
        Expr.gt(field[Employee](_.salary).ref, Expr.literal(80000))
      ))
    )
    results.map(d => str(d, "name")) should contain theSameElementsAs Seq("Alice", "Dave")

  // ── Stage.$count ───────────────────────────────────────────────────────────

  "Stage.count" should "count the number of documents passing through the pipeline" in:
    val results = agg(
      Stage.`match`(field[Employee](_.active) === true),
      Stage.count("total")
    )
    results should have size 1
    numInt(results.head, "total") shouldBe 4

  // ── Stage.$group ───────────────────────────────────────────────────────────

  "Stage.group" should "group documents by department and count them" in:
    val results = agg(
      Stage.group(field[Employee](_.dept).ref)("count" -> Accumulator.sum(1))
    ).sortBy(d => str(d, "_id"))
    results should have size 2
    val eng = results.find(d => str(d, "_id") == "Engineering").get
    val mkt = results.find(d => str(d, "_id") == "Marketing").get
    numInt(eng, "count") shouldBe 3
    numInt(mkt, "count") shouldBe 2

  it should "compute sum and avg accumulators" in:
    val results = agg(
      Stage.group(field[Employee](_.dept).ref)(
        "totalSalary" -> Accumulator.sum(field[Employee](_.salary).ref),
        "avgSalary"   -> Accumulator.avg(field[Employee](_.salary).ref)
      )
    ).sortBy(d => str(d, "_id"))
    val eng = results.find(d => str(d, "_id") == "Engineering").get
    val mkt = results.find(d => str(d, "_id") == "Marketing").get
    numInt(eng, "totalSalary") shouldBe 250000
    numInt(mkt, "totalSalary") shouldBe 137000
    numDbl(eng, "avgSalary")   shouldBe (83333.33 +- 1.0)
    numDbl(mkt, "avgSalary")   shouldBe 68500.0

  it should "compute min and max accumulators" in:
    val results = agg(
      Stage.group(field[Employee](_.dept).ref)(
        "minSalary" -> Accumulator.min(field[Employee](_.salary).ref),
        "maxSalary" -> Accumulator.max(field[Employee](_.salary).ref)
      )
    ).sortBy(d => str(d, "_id"))
    val eng = results.find(d => str(d, "_id") == "Engineering").get
    numInt(eng, "minSalary") shouldBe 75000
    numInt(eng, "maxSalary") shouldBe 90000

  it should "compute first and last accumulators after explicit sort" in:
    val results = agg(
      Stage.`match`(field[Employee](_.dept) === "Engineering"),
      Stage.sort(field[Employee](_.salary).asc),
      Stage.group(field[Employee](_.dept).ref)(
        "first" -> Accumulator.first(field[Employee](_.name).ref),
        "last"  -> Accumulator.last(field[Employee](_.name).ref)
      )
    )
    results should have size 1
    str(results.head, "first") shouldBe "Bob"
    str(results.head, "last")  shouldBe "Alice"

  it should "build an array of values with push accumulator" in:
    val results = agg(
      Stage.`match`(field[Employee](_.dept) === "Engineering"),
      Stage.sort(field[Employee](_.salary).asc),
      Stage.group(field[Employee](_.dept).ref)(
        "names" -> Accumulator.push(field[Employee](_.name).ref)
      )
    )
    val names = arr(results.head, "names").map(_.asString().getValue)
    names shouldBe Seq("Bob", "Dave", "Alice")

  it should "collect unique values with addToSet accumulator" in:
    val results = agg(
      Stage.group(field[Employee](_.active).ref)(
        "depts" -> Accumulator.addToSet(field[Employee](_.dept).ref)
      )
    )
    val active = results.find(d => d("_id").asBoolean().getValue).get
    val depts = arr(active, "depts").map(_.asString().getValue)
    depts should contain theSameElementsAs Seq("Engineering", "Marketing")

  it should "count documents with the $count accumulator" in:
    val results = agg(Stage.groupAll("n" -> Accumulator.count))
    results should have size 1
    numInt(results.head, "n") shouldBe 5

  "Stage.groupAll" should "aggregate all documents into a single result" in:
    val results = agg(
      Stage.groupAll(
        "total"   -> Accumulator.sum(field[Employee](_.salary).ref),
        "highest" -> Accumulator.max(field[Employee](_.salary).ref)
      )
    )
    results should have size 1
    numInt(results.head, "total")   shouldBe 387000
    numInt(results.head, "highest") shouldBe 90000

  // ── Stage.$sort ────────────────────────────────────────────────────────────

  "Stage.sort" should "order pipeline results by a field" in:
    val results = agg(
      Stage.sort(field[Employee](_.salary).asc)
    )
    results.map(d => numInt(d, "salary")) shouldBe Seq(65000, 72000, 75000, 85000, 90000)

  it should "sort by a computed accumulator field" in:
    val results = agg(
      Stage.group(field[Employee](_.dept).ref)("count" -> Accumulator.sum(1)),
      Stage.sort(Sort.descending("count"))
    )
    numInt(results.head, "count") shouldBe 3  // Engineering first

  // ── Stage.$limit / $skip ───────────────────────────────────────────────────

  "Stage.limit" should "return at most n documents" in:
    val results = agg(Stage.sort(field[Employee](_.salary).desc), Stage.limit(3))
    results should have size 3
    numInt(results.head, "salary") shouldBe 90000

  "Stage.skip" should "skip the first n documents" in:
    val results = agg(Stage.sort(field[Employee](_.salary).asc), Stage.skip(3))
    results should have size 2
    numInt(results.head, "salary") shouldBe 85000

  "Stage.limit + Stage.skip" should "implement cursor-style pagination" in:
    val page1 = agg(Stage.sort(field[Employee](_.salary).asc), Stage.limit(2))
    val page2 = agg(Stage.sort(field[Employee](_.salary).asc), Stage.skip(2), Stage.limit(2))
    page1.map(d => numInt(d, "salary")) shouldBe Seq(65000, 72000)
    page2.map(d => numInt(d, "salary")) shouldBe Seq(75000, 85000)

  // ── Stage.$project ─────────────────────────────────────────────────────────

  "Stage.project" should "include only specified fields" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.project(
        field[Employee](_.name).include,
        field[Employee](_.salary).include,
        Projection.excludeId
      )
    )
    results should have size 1
    val doc = results.head
    doc.containsKey("name")   shouldBe true
    doc.containsKey("salary") shouldBe true
    doc.containsKey("_id")    shouldBe false
    doc.containsKey("dept")   shouldBe false
    doc.containsKey("tags")   shouldBe false

  it should "produce a computed field with Stage.computed" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.project(
        Stage.computed("monthly", Expr.divide(field[Employee](_.salary).ref, Expr.literal(12))),
        Projection.excludeId
      )
    )
    numDbl(results.head, "monthly") shouldBe (7500.0 +- 0.01)

  // ── Stage.$addFields / $set ────────────────────────────────────────────────

  "Stage.addFields" should "append a computed field without removing existing fields" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Bob"),
      Stage.addFields(
        "annualBonus" -> Expr.multiply(field[Employee](_.salary).ref, Expr.literal(0.1))
      )
    )
    val doc = results.head
    doc.containsKey("name")        shouldBe true
    doc.containsKey("annualBonus") shouldBe true
    numDbl(doc, "annualBonus")     shouldBe (7500.0 +- 0.01)

  "Stage.set" should "behave identically to addFields" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Carol"),
      Stage.set("isJunior" -> Expr.lt(field[Employee](_.salary).ref, Expr.literal(70000)))
    )
    results.head("isJunior").asBoolean().getValue shouldBe true

  // ── Stage.$unset ───────────────────────────────────────────────────────────

  "Stage.unset" should "remove specified fields from documents" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.unset("tags", "address", "active")
    )
    val doc = results.head
    doc.containsKey("name")    shouldBe true
    doc.containsKey("tags")    shouldBe false
    doc.containsKey("address") shouldBe false
    doc.containsKey("active")  shouldBe false

  // ── Stage.$unwind ──────────────────────────────────────────────────────────

  "Stage.unwind" should "flatten array fields into separate documents" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.unwind("$tags")
    )
    // Alice has 3 tags → 3 documents
    results should have size 3
    results.map(d => str(d, "tags")) should contain theSameElementsAs Seq("scala", "mongodb", "kafka")

  it should "drop documents with null/missing array when preserveNullAndEmpty is false" in:
    val results = agg(Stage.unwind("$address"))
    // Dave has no address (null/missing) → only 4 results
    results should have size 4
    results.map(d => str(d, "name")) should not contain "Dave"

  it should "include null/missing documents when preserveNullAndEmpty is true" in:
    val results = agg(Stage.unwind("$address", preserveNullAndEmpty = true))
    results should have size 5
    results.map(d => str(d, "name")) should contain("Dave")

  it should "record the array index when includeArrayIndex is set" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.unwind("$tags", includeArrayIndex = Some("idx"))
    )
    results.map(d => numInt(d, "idx")) shouldBe Seq(0, 1, 2)

  // ── Stage.$sortByCount ─────────────────────────────────────────────────────

  "Stage.sortByCount" should "count occurrences of each tag and sort descending" in:
    // kafka appears 3 times (Alice, Bob, Dave)
    val results = agg(
      Stage.unwind("$tags"),
      Stage.sortByCount(field[Employee](_.tags).ref)
    )
    str(results.head, "_id")   shouldBe "kafka"
    numInt(results.head, "count") shouldBe 3

  // ── Stage.$replaceRoot / $replaceWith ──────────────────────────────────────

  "Stage.replaceRoot" should "replace each document with a nested sub-document" in:
    val results = agg(
      Stage.`match`(field[Employee](_.address).exists()),
      Stage.replaceRoot(field[Employee](_.address).ref)
    )
    // 4 employees have an address; each result is just the address sub-doc
    results should have size 4
    results.foreach(doc => doc.containsKey("city") shouldBe true)
    results.map(d => str(d, "city")) should contain theSameElementsAs Seq("Paris", "London", "Paris", "Berlin")

  "Stage.replaceWith" should "behave identically to replaceRoot" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.replaceWith(field[Employee](_.address).ref)
    )
    str(results.head, "city") shouldBe "Paris"

  // ── Stage.$lookup ──────────────────────────────────────────────────────────

  "Stage.lookup" should "join employees to departments on dept == name" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.lookup("departments", "dept", "name", "deptInfo")
    )
    results should have size 1
    val deptInfo = arr(results.head, "deptInfo")
    deptInfo should have size 1
    deptInfo.head.asDocument().getString("name").getValue shouldBe "Engineering"

  it should "produce an empty deptInfo array for unknown departments" in:
    // Insert a temp employee with unknown dept
    val tmpId = new ObjectId()
    empCol.insertOne(Employee(tmpId, "Temp", "Unknown", 50000, 25, active = true, Nil, None))
      .toFuture().futureValue
    val results = agg(
      Stage.`match`(field[Employee](_._id) === tmpId),
      Stage.lookup("departments", "dept", "name", "deptInfo")
    )
    arr(results.head, "deptInfo") shouldBe empty
    empCol.deleteOne(field[Employee](_._id) === tmpId).toFuture().futureValue

  "Stage.lookupPipeline" should "run a sub-pipeline on the joined collection" in:
    val subPipeline = Pipeline(
      Stage.`match`(new BsonDocument("$expr",
        Expr.eq(Expr.fieldRef("$$empDept"), Expr.fieldRef("$name"))
      ))
    )
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Bob"),
      Stage.lookupPipeline(
        from = "departments",
        let = Seq("empDept" -> field[Employee](_.dept).ref),
        pipeline = subPipeline,
        as = "deptMatch"
      )
    )
    arr(results.head, "deptMatch") should have size 1

  // ── Stage.$facet ───────────────────────────────────────────────────────────

  "Stage.facet" should "run independent sub-pipelines and merge into one document" in:
    val results = agg(
      Stage.facet(
        "byDept" -> Pipeline(
          Stage.group(field[Employee](_.dept).ref)("count" -> Accumulator.sum(1)),
          Stage.sort(Sort.ascending("_id"))
        ),
        "top3sal" -> Pipeline(
          Stage.sort(field[Employee](_.salary).desc),
          Stage.limit(3),
          Stage.project(field[Employee](_.name).include, Projection.excludeId)
        )
      )
    )
    results should have size 1
    val doc = results.head
    // byDept: Engineering(3) and Marketing(2)
    val byDept = arr(doc, "byDept")
    byDept should have size 2
    // top3sal: Alice(90k), Dave(85k), Bob(75k)
    val top3 = arr(doc, "top3sal")
    top3 should have size 3
    top3.map(_.asDocument().getString("name").getValue) shouldBe Seq("Alice", "Dave", "Bob")

  // ── Stage.$bucket / $bucketAuto ────────────────────────────────────────────

  "Stage.bucket" should "categorise documents into salary ranges" in:
    val results = agg(
      Stage.bucket(
        field[Employee](_.salary).ref,
        boundaries = Seq(Expr.literal(60000), Expr.literal(70000), Expr.literal(80000), Expr.literal(100000)),
        output = Seq("count" -> Accumulator.sum(1), "names" -> Accumulator.push(field[Employee](_.name).ref))
      )
    ).sortBy(d => d("_id").asNumber().intValue())

    results should have size 3
    // [60000, 70000): Carol
    numInt(results(0), "count") shouldBe 1
    // [70000, 80000): Eve, Bob
    numInt(results(1), "count") shouldBe 2
    // [80000, 100000): Dave, Alice
    numInt(results(2), "count") shouldBe 2

  "Stage.bucketAuto" should "split documents into N evenly-populated buckets" in:
    val results = agg(
      Stage.bucketAuto(field[Employee](_.salary).ref, numBuckets = 2,
        output = Seq("count" -> Accumulator.sum(1)))
    )
    results should have size 2
    results.map(d => numInt(d, "count")).sum shouldBe 5

  // ── Stage.$sample ──────────────────────────────────────────────────────────

  "Stage.sample" should "return exactly n random documents" in:
    val results = agg(Stage.sample(3))
    results should have size 3

  // ── Expr: conditional expressions ──────────────────────────────────────────

  "Expr.cond" should "produce a conditional label field" in:
    val results = agg(
      Stage.addFields(
        "level" -> Expr.cond(
          Expr.gte(field[Employee](_.salary).ref, Expr.literal(85000)),
          Expr.literal("senior"),
          Expr.literal("junior")
        )
      ),
      Stage.sort(field[Employee](_.name).asc)
    )
    val levels = results.map(d => str(d, "name") -> str(d, "level")).toMap
    levels("Alice") shouldBe "senior"
    levels("Dave")  shouldBe "senior"
    levels("Bob")   shouldBe "junior"
    levels("Carol") shouldBe "junior"
    levels("Eve")   shouldBe "junior"

  "Expr.ifNull" should "substitute a fallback when a field is null or missing" in:
    val results = agg(
      Stage.addFields(
        "city" -> Expr.ifNull(
          Expr.fieldRef("$address.city"),
          Expr.literal("Unknown")
        )
      ),
      Stage.sort(field[Employee](_.name).asc)
    )
    val cities = results.map(d => str(d, "name") -> str(d, "city")).toMap
    cities("Alice") shouldBe "Paris"
    cities("Dave")  shouldBe "Unknown"

  "Expr.switch" should "select the first matching branch" in:
    val results = agg(
      Stage.addFields(
        "band" -> Expr.switch(
          Expr.gte(field[Employee](_.salary).ref, Expr.literal(85000)) -> Expr.literal("A"),
          Expr.gte(field[Employee](_.salary).ref, Expr.literal(70000)) -> Expr.literal("B")
        )(Expr.literal("C"))
      ),
      Stage.sort(field[Employee](_.name).asc)
    )
    val bands = results.map(d => str(d, "name") -> str(d, "band")).toMap
    bands("Alice") shouldBe "A"
    bands("Dave")  shouldBe "A"
    bands("Bob")   shouldBe "B"
    bands("Eve")   shouldBe "B"
    bands("Carol") shouldBe "C"

  // ── Expr: string expressions ───────────────────────────────────────────────

  "Expr.concat" should "concatenate field references and literals" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.project(
        Stage.computed("label",
          Expr.concat(field[Employee](_.name).ref, Expr.literal(" ("), field[Employee](_.dept).ref, Expr.literal(")"))),
        Projection.excludeId
      )
    )
    str(results.head, "label") shouldBe "Alice (Engineering)"

  "Expr.toUpper / toLower" should "transform string case" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields(
        "upper" -> Expr.toUpper(field[Employee](_.dept).ref),
        "lower" -> Expr.toLower(field[Employee](_.dept).ref)
      )
    )
    str(results.head, "upper") shouldBe "ENGINEERING"
    str(results.head, "lower") shouldBe "engineering"

  // ── Expr: arithmetic expressions ───────────────────────────────────────────

  "Expr.add / subtract / multiply / divide" should "compute correctly on numeric fields" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields(
        "plus100"  -> Expr.add(field[Employee](_.salary).ref, Expr.literal(100)),
        "minus100" -> Expr.subtract(field[Employee](_.salary).ref, Expr.literal(100)),
        "times2"   -> Expr.multiply(field[Employee](_.salary).ref, Expr.literal(2)),
        "half"     -> Expr.divide(field[Employee](_.salary).ref, Expr.literal(2))
      )
    )
    val doc = results.head
    numInt(doc, "plus100")  shouldBe 90100
    numInt(doc, "minus100") shouldBe 89900
    numInt(doc, "times2")   shouldBe 180000
    numDbl(doc, "half")     shouldBe 45000.0

  "Expr.mod" should "compute the modulo" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields("rem" -> Expr.mod(field[Employee](_.age).ref, Expr.literal(7)))
    )
    numInt(results.head, "rem") shouldBe (30 % 7)

  "Expr.abs / ceil / floor / sqrt / round" should "produce correct numeric results" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields(
        "absAge"   -> Expr.abs(field[Employee](_.age).ref),
        "sqrtAge"  -> Expr.sqrt(field[Employee](_.age).ref),
        "roundedK" -> Expr.round(Expr.divide(field[Employee](_.salary).ref, Expr.literal(1000)), 0)
      )
    )
    val doc = results.head
    numInt(doc, "absAge")   shouldBe 30
    numDbl(doc, "sqrtAge")  shouldBe (math.sqrt(30) +- 0.001)
    numDbl(doc, "roundedK") shouldBe 90.0

  // ── Expr: array expressions ────────────────────────────────────────────────

  "Expr.size" should "return the number of elements in an array field" in:
    val results = agg(
      Stage.addFields("tagCount" -> Expr.size(field[Employee](_.tags).ref)),
      Stage.sort(field[Employee](_.name).asc)
    )
    val counts = results.map(d => str(d, "name") -> numInt(d, "tagCount")).toMap
    counts("Alice") shouldBe 3
    counts("Bob")   shouldBe 2
    counts("Carol") shouldBe 2
    counts("Dave")  shouldBe 3
    counts("Eve")   shouldBe 2

  "Expr.filter" should "select array elements matching a condition" in:
    val results = agg(
      Stage.addFields(
        "kafkaTags" -> Expr.filter(
          field[Employee](_.tags).ref,
          "t",
          Expr.eq(Expr.fieldRef("$$t"), Expr.literal("kafka"))
        )
      ),
      Stage.sort(field[Employee](_.name).asc)
    )
    val kafkaCounts = results.map(d => str(d, "name") -> arr(d, "kafkaTags").size).toMap
    kafkaCounts("Alice") shouldBe 1   // ["kafka"]
    kafkaCounts("Bob")   shouldBe 1   // ["kafka"]
    kafkaCounts("Carol") shouldBe 0   // []
    kafkaCounts("Dave")  shouldBe 1   // ["kafka"]
    kafkaCounts("Eve")   shouldBe 0   // []

  "Expr.map" should "transform each element of an array" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Bob"),
      Stage.addFields(
        "upperTags" -> Expr.map(
          field[Employee](_.tags).ref,
          "t",
          Expr.toUpper(Expr.fieldRef("$$t"))
        )
      )
    )
    arr(results.head, "upperTags").map(_.asString().getValue) shouldBe Seq("JAVA", "KAFKA")

  "Expr.arrayElemAt" should "retrieve the element at the given index" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields("firstTag" -> Expr.arrayElemAt(field[Employee](_.tags).ref, 0))
    )
    str(results.head, "firstTag") shouldBe "scala"

  "Expr.concatArrays" should "concatenate two arrays into one" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Bob"),
      Stage.project(
        Stage.computed("doubled",
          Expr.concatArrays(field[Employee](_.tags).ref, field[Employee](_.tags).ref)
        ),
        Projection.excludeId
      )
    )
    val doubled = arr(results.head, "doubled").map(_.asString().getValue)
    doubled should have size 4
    doubled.count(_ == "java")  shouldBe 2
    doubled.count(_ == "kafka") shouldBe 2

  "Expr.in" should "test array membership" in:
    val results = agg(
      Stage.addFields(
        "hasScala" -> Expr.in(Expr.literal("scala"), field[Employee](_.tags).ref)
      ),
      Stage.sort(field[Employee](_.name).asc)
    )
    val hasScala = results.map(d => str(d, "name") -> d("hasScala").asBoolean().getValue).toMap
    hasScala("Alice") shouldBe true
    hasScala("Dave")  shouldBe true
    hasScala("Bob")   shouldBe false

  "Expr.reduce" should "fold an array to a single value" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields(
        "tagLen" -> Expr.reduce(
          field[Employee](_.tags).ref,
          Expr.literal(0),
          Expr.add(Expr.fieldRef("$$value"), Expr.strLenCP(Expr.fieldRef("$$this")))
        )
      )
    )
    // scala(5) + mongodb(7) + kafka(5) = 17
    numInt(results.head, "tagLen") shouldBe 17

  "Expr.reverseArray" should "reverse an array" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields("reversed" -> Expr.reverseArray(field[Employee](_.tags).ref))
    )
    arr(results.head, "reversed").map(_.asString().getValue) shouldBe Seq("kafka", "mongodb", "scala")

  // ── Expr: string utilities ─────────────────────────────────────────────────

  "Expr.strLenCP" should "return the character length of a string" in:
    val results = agg(
      Stage.addFields("nameLen" -> Expr.strLenCP(field[Employee](_.name).ref)),
      Stage.sort(field[Employee](_.name).asc)
    )
    val lens = results.map(d => str(d, "name") -> numInt(d, "nameLen")).toMap
    lens("Alice") shouldBe 5
    lens("Bob")   shouldBe 3
    lens("Carol") shouldBe 5
    lens("Dave")  shouldBe 4
    lens("Eve")   shouldBe 3

  "Expr.split" should "split a string on a delimiter" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields(
        "parts" -> Expr.split(
          Expr.concat(field[Employee](_.name).ref, Expr.literal(","), field[Employee](_.dept).ref),
          Expr.literal(",")
        )
      )
    )
    arr(results.head, "parts").map(_.asString().getValue) shouldBe Seq("Alice", "Engineering")

  "Expr.trim / toUpper chain" should "clean and uppercase a string" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields("clean" -> Expr.toUpper(Expr.trim(field[Employee](_.dept).ref)))
    )
    str(results.head, "clean") shouldBe "ENGINEERING"

  // ── Expr: type conversion ──────────────────────────────────────────────────

  "Expr.toString" should "convert a numeric field to string" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.addFields("salStr" -> Expr.toString(field[Employee](_.salary).ref))
    )
    str(results.head, "salStr") shouldBe "90000"

  // ── Expr: logical / comparison ─────────────────────────────────────────────

  "Expr.and (expression form)" should "return true only when all sub-expressions are true" in:
    val results = agg(
      Stage.addFields(
        "isTarget" -> Expr.and(
          Expr.eq(field[Employee](_.dept).ref, Expr.literal("Engineering")),
          Expr.gt(field[Employee](_.salary).ref, Expr.literal(80000))
        )
      ),
      Stage.sort(field[Employee](_.name).asc)
    )
    val flags = results.map(d => str(d, "name") -> d("isTarget").asBoolean().getValue).toMap
    flags("Alice") shouldBe true
    flags("Dave")  shouldBe true
    flags("Bob")   shouldBe false
    flags("Carol") shouldBe false

  "Expr.not (expression form)" should "negate a boolean expression" in:
    val results = agg(
      Stage.addFields(
        "notActive" -> Expr.not(field[Employee](_.active).ref)
      ),
      Stage.sort(field[Employee](_.name).asc)
    )
    val flags = results.map(d => str(d, "name") -> d("notActive").asBoolean().getValue).toMap
    flags("Carol") shouldBe true
    flags("Alice") shouldBe false

  // ── Expr: object operators ─────────────────────────────────────────────────

  "Expr.mergeObjects" should "merge two documents into one" in:
    val results = agg(
      Stage.`match`(field[Employee](_.name) === "Alice"),
      Stage.project(
        Stage.computed("merged",
          Expr.mergeObjects(
            new BsonDocument("name", Expr.fieldRef("$name")),
            new BsonDocument("dept", Expr.fieldRef("$dept"))
          )
        ),
        Projection.excludeId
      )
    )
    val merged = results.head("merged").asDocument()
    merged.getString("name").getValue shouldBe "Alice"
    merged.getString("dept").getValue shouldBe "Engineering"

  // ── Stage.$out ─────────────────────────────────────────────────────────────

  "Stage.out" should "write pipeline results to a target collection" in:
    agg(
      Stage.`match`(field[Employee](_.dept) === "Engineering"),
      Stage.project(field[Employee](_.name).include, Projection.excludeId),
      Stage.out("eng_names")
    )
    val stored = db.getCollection("eng_names").find().toFuture().futureValue
    stored should have size 3
    stored.map(d => d("name").asString().getValue) should contain theSameElementsAs Seq("Alice", "Bob", "Dave")
    db.getCollection("eng_names").drop().toFuture().futureValue

  // ── Pipeline composition ───────────────────────────────────────────────────

  "Pipeline.:+" should "append a stage and produce correct results" in:
    val base = Pipeline(Stage.`match`(field[Employee](_.dept) === "Engineering"))
    val extended = base :+ Stage.count("n")
    val results = rawEmp.aggregate(extended.stages).toFuture().futureValue
    numInt(results.head, "n") shouldBe 3

  "Pipeline.++" should "concatenate two pipelines" in:
    val filterPipe = Pipeline(Stage.`match`(field[Employee](_.active) === true))
    val sortPipe   = Pipeline(Stage.sort(field[Employee](_.salary).desc), Stage.limit(2))
    val combined   = filterPipe ++ sortPipe
    val results    = rawEmp.aggregate(combined.stages).toFuture().futureValue
    results should have size 2
    numInt(results.head, "salary") shouldBe 90000

  // ── End-to-end: full pipeline ──────────────────────────────────────────────

  "A full aggregation pipeline" should "compose match → group → sort → project → limit correctly" in:
    val pipeline = Pipeline(
      Stage.`match`(field[Employee](_.active) === true),
      Stage.group(field[Employee](_.dept).ref)(
        "headcount" -> Accumulator.count,
        "avgSalary" -> Accumulator.avg(field[Employee](_.salary).ref),
        "employees" -> Accumulator.push(field[Employee](_.name).ref)
      ),
      Stage.sort(Sort.descending("headcount")),
      Stage.project(
        Stage.computed("dept",       Expr.fieldRef("$_id")),
        Stage.computed("headcount",  Expr.fieldRef("$headcount")),
        Stage.computed("avgSalary",  Expr.round(Expr.fieldRef("$avgSalary"), 0)),
        Projection.excludeId
      ),
      Stage.limit(5)
    )
    val results = rawEmp.aggregate(pipeline.stages).toFuture().futureValue
    results should have size 2
    val eng = results.find(d => str(d, "dept") == "Engineering").get
    val mkt = results.find(d => str(d, "dept") == "Marketing").get
    numInt(eng, "headcount") shouldBe 3
    numInt(mkt, "headcount") shouldBe 1   // Carol is inactive, so only Eve
    numDbl(eng, "avgSalary") shouldBe (83333.0 +- 1.0)
    numDbl(mkt, "avgSalary") shouldBe 72000.0

  "A facet + bucket pipeline" should "run multiple analytics simultaneously" in:
    val pipeline = Pipeline(
      Stage.facet(
        "salaryBuckets" -> Pipeline(
          Stage.bucket(
            field[Employee](_.salary).ref,
            boundaries = Seq(Expr.literal(60000), Expr.literal(75000), Expr.literal(100000)),
            output = Seq("count" -> Accumulator.sum(1))
          )
        ),
        "tagCloud" -> Pipeline(
          Stage.unwind("$tags"),
          Stage.sortByCount(field[Employee](_.tags).ref),
          Stage.limit(3)
        )
      )
    )
    val results = rawEmp.aggregate(pipeline.stages).toFuture().futureValue
    results should have size 1
    val doc = results.head

    // salary buckets: [60k,75k) = Carol, Eve  →  [75k,100k) = Bob, Dave, Alice
    val buckets = arr(doc, "salaryBuckets")
    buckets should have size 2
    buckets.map(_.asDocument().getInt32("count").getValue).sum shouldBe 5

    // top-3 tags includes kafka with count 3
    val top3 = arr(doc, "tagCloud")
    top3.head.asDocument().getString("_id").getValue shouldBe "kafka"

end AggregationIntegrationSpec
