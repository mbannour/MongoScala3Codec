package io.github.mbannour.mongo.dsl

import com.mongodb.client.model.{Filters, Updates, Sorts, Projections, TextSearchOptions}
import scala.jdk.CollectionConverters.*
import io.github.mbannour.mongo.dsl.testkit.DslTestKit
import org.bson.conversions.Bson
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Verifies that DSL-produced BSON is equivalent to what the MongoDB driver's own
 *  `Filters`, `Updates`, `Sorts`, and `Projections` builders produce.
 *
 *  Placed in the integration module because that module already depends on the full
 *  `mongo-scala-driver`, giving us access to the official builder API.
 *
 *  No running MongoDB is required — these are pure in-memory BSON comparisons using
 *  [[DslTestKit.assertEquivalentTo]] and [[DslTestKit.assertEqualsJson]].
 */
class DslTestKitFiltersSpec extends AnyFunSuite with Matchers:

  case class User(
    name: String,
    age: Int,
    active: Boolean,
    salary: Double,
    tags: List[String]
  )

  // ── assertEquivalentTo: DSL vs Filters.* ─────────────────────────────────

  test("=== produces explicit $eq form") {
    DslTestKit.assertEqualsJson(
      field[User](_.name) === "Alice",
      """{"name": {"$eq": "Alice"}}"""
    )
  }

  test("=/= matches Filters.ne") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name) =/= "deleted",
      Filters.ne("name", "deleted")
    )
  }

  test("> matches Filters.gt") {
    DslTestKit.assertEquivalentTo(
      field[User](_.age) > 18,
      Filters.gt("age", 18)
    )
  }

  test(">= matches Filters.gte") {
    DslTestKit.assertEquivalentTo(
      field[User](_.age) >= 21,
      Filters.gte("age", 21)
    )
  }

  test("< matches Filters.lt") {
    DslTestKit.assertEquivalentTo(
      field[User](_.salary) < 50000.0,
      Filters.lt("salary", 50000.0)
    )
  }

  test("<= matches Filters.lte") {
    DslTestKit.assertEquivalentTo(
      field[User](_.salary) <= 100000.0,
      Filters.lte("salary", 100000.0)
    )
  }

  test(".in matches Filters.in") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).in("Alice", "Bob"),
      Filters.in("name", "Alice", "Bob")
    )
  }

  test(".nin matches Filters.nin") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).nin("banned", "suspended"),
      Filters.nin("name", "banned", "suspended")
    )
  }

  test("Filter.and produces explicit $and with $eq sub-filters") {
    DslTestKit.assertEqualsJson(
      Filter.and(
        field[User](_.active) === true,
        field[User](_.age) >= 18
      ),
      """{"$and": [{"active": {"$eq": true}}, {"age": {"$gte": 18}}]}"""
    )
  }

  test("Filter.or produces explicit $or with $eq sub-filters") {
    DslTestKit.assertEqualsJson(
      Filter.or(
        field[User](_.age) < 18,
        field[User](_.active) === false
      ),
      """{"$or": [{"age": {"$lt": 18}}, {"active": {"$eq": false}}]}"""
    )
  }

  test(".exists(true) matches Filters.exists") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).exists(),
      Filters.exists("name")
    )
  }

  test(".exists(false) matches Filters.exists(field, false)") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).exists(false),
      Filters.exists("name", false)
    )
  }

  // ── assertEquivalentTo: DSL vs Updates.* ─────────────────────────────────

  test(":= matches Updates.set") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name) := "Bob",
      Updates.set("name", "Bob")
    )
  }

  test(".unset matches Updates.unset") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).unset,
      Updates.unset("name")
    )
  }

  test(".inc matches Updates.inc") {
    DslTestKit.assertEquivalentTo(
      field[User](_.age).inc(1),
      Updates.inc("age", 1)
    )
  }

  test(".push matches Updates.push") {
    DslTestKit.assertEquivalentTo(
      field[User](_.tags).push("scala"),
      Updates.push("tags", "scala")
    )
  }

  test(".addToSet matches Updates.addToSet") {
    DslTestKit.assertEquivalentTo(
      field[User](_.tags).addToSet("functional"),
      Updates.addToSet("tags", "functional")
    )
  }

  // ── assertEquivalentTo: DSL vs Sorts.* ───────────────────────────────────

  test(".asc matches Sorts.ascending") {
    DslTestKit.assertEquivalentTo(
      field[User](_.age).asc,
      Sorts.ascending("age")
    )
  }

  test(".desc matches Sorts.descending") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).desc,
      Sorts.descending("name")
    )
  }

  test("Sort.combine matches Sorts.orderBy") {
    DslTestKit.assertEquivalentTo(
      Sort.combine(field[User](_.age).desc, field[User](_.name).asc),
      Sorts.orderBy(Sorts.descending("age"), Sorts.ascending("name"))
    )
  }

  // ── assertEquivalentTo: DSL vs Projections.* ─────────────────────────────

  test(".include matches Projections.include") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).include,
      Projections.include("name")
    )
  }

  test(".exclude matches Projections.exclude") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).exclude,
      Projections.exclude("name")
    )
  }

  test("Projection.excludeId matches Projections.excludeId") {
    DslTestKit.assertEquivalentTo(
      Projection.excludeId,
      Projections.excludeId()
    )
  }

  test("Projection.combine matches Projections.fields") {
    DslTestKit.assertEquivalentTo(
      Projection.combine(
        field[User](_.name).include,
        field[User](_.age).include,
        Projection.excludeId
      ),
      Projections.fields(
        Projections.include("name"),
        Projections.include("age"),
        Projections.excludeId()
      )
    )
  }

  // ── assertEqualsJson against raw MongoDB query strings ───────────────────

  test("assertEqualsJson: equality filter matches MongoDB query string") {
    DslTestKit.assertEqualsJson(
      field[User](_.name) === "Alice",
      """{"name": {"$eq": "Alice"}}"""
    )
  }

  test("assertEqualsJson: range filter matches MongoDB query string") {
    DslTestKit.assertEqualsJson(
      field[User](_.age) > 18,
      """{"age": {"$gt": 18}}"""
    )
  }

  test("assertEqualsJson: compound filter matches MongoDB query string") {
    DslTestKit.assertEqualsJson(
      Filter.and(
        field[User](_.active) === true,
        field[User](_.salary) > 50000.0
      ),
      """{"$and": [{"active": {"$eq": true}}, {"salary": {"$gt": 50000.0}}]}"""
    )
  }

  test("assertEqualsJson: $set update matches MongoDB update string") {
    DslTestKit.assertEqualsJson(
      field[User](_.name) := "Carol",
      """{"$set": {"name": "Carol"}}"""
    )
  }

  test("assertEqualsJson: combined update matches MongoDB update string") {
    DslTestKit.assertEqualsJson(
      Update.combine(
        field[User](_.name) := "Dave",
        field[User](_.age).inc(5)
      ),
      """{"$set": {"name": "Dave"}, "$inc": {"age": 5}}"""
    )
  }

  // ── Cross-check: assertEqualsJson agrees with assertEquivalentTo ──────────

  test("assertEqualsJson and assertEquivalentTo agree on equality filter") {
    val dslFilter = field[User](_.age) >= 21
    DslTestKit.assertEqualsJson(dslFilter, """{"age": {"$gte": 21}}""")
    DslTestKit.assertEquivalentTo(dslFilter, Filters.gte("age", 21))
  }

  // ── New filter operators ──────────────────────────────────────────────────

  test(".all matches Filters.all") {
    DslTestKit.assertEquivalentTo(
      field[User](_.tags).all("scala", "fp"),
      Filters.all("tags", "scala", "fp")
    )
  }

  test(".hasType(string) matches Filters.type(string)") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).hasType("string"),
      Filters.`type`("name", "string")
    )
  }

  test(".mod matches Filters.mod") {
    DslTestKit.assertEquivalentTo(
      field[User](_.age).mod(2L, 0L),
      Filters.mod("age", 2L, 0L)
    )
  }

  test("Filter.text matches Filters.text") {
    DslTestKit.assertEquivalentTo(
      Filter.text("coffee shop"),
      Filters.text("coffee shop")
    )
  }

  test("Filter.text with language matches Filters.text with TextSearchOptions") {
    DslTestKit.assertEquivalentTo(
      Filter.text("café", language = Some("fr")),
      Filters.text("café", new TextSearchOptions().language("fr"))
    )
  }

  // ── New update operators ──────────────────────────────────────────────────

  test(".pullAll matches Updates.pullAll") {
    DslTestKit.assertEquivalentTo(
      field[User](_.tags).pullAll("old", "outdated"),
      Updates.pullAll("tags", List("old", "outdated").asJava)
    )
  }

  test(".pushEach matches Updates.pushEach") {
    DslTestKit.assertEquivalentTo(
      field[User](_.tags).pushEach(Seq("a", "b")),
      Updates.pushEach("tags", List("a", "b").asJava)
    )
  }

  test(".rename matches Updates.rename") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).rename("fullName"),
      Updates.rename("name", "fullName")
    )
  }

  test(".currentDate matches Updates.currentDate") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).currentDate,
      Updates.currentDate("name")
    )
  }

  test(".currentTimestamp matches Updates.currentTimestamp") {
    DslTestKit.assertEquivalentTo(
      field[User](_.name).currentTimestamp,
      Updates.currentTimestamp("name")
    )
  }

  // ── assertEqualsJson for new operators ────────────────────────────────────

  test("assertEqualsJson: $all filter matches MongoDB query string") {
    DslTestKit.assertEqualsJson(
      field[User](_.tags).all("scala", "fp"),
      """{"tags": {"$all": ["scala", "fp"]}}"""
    )
  }

  test("assertEqualsJson: $mod filter matches MongoDB query string") {
    DslTestKit.assertEqualsJson(
      field[User](_.age).mod(2L, 0L),
      """{"age": {"$mod": [2, 0]}}"""
    )
  }

  test("assertEqualsJson: $text filter matches MongoDB query string") {
    DslTestKit.assertEqualsJson(
      Filter.text("hello world"),
      """{"$text": {"$search": "hello world"}}"""
    )
  }

  test("assertEqualsJson: $pullAll update matches MongoDB update string") {
    DslTestKit.assertEqualsJson(
      field[User](_.tags).pullAll("a", "b"),
      """{"$pullAll": {"tags": ["a", "b"]}}"""
    )
  }

  test("assertEqualsJson: pushEach update matches MongoDB update string") {
    DslTestKit.assertEqualsJson(
      field[User](_.tags).pushEach(Seq("x", "y")),
      """{"$push": {"tags": {"$each": ["x", "y"]}}}"""
    )
  }

  test("assertEqualsJson: $rename update matches MongoDB update string") {
    DslTestKit.assertEqualsJson(
      field[User](_.name).rename("fullName"),
      """{"$rename": {"name": "fullName"}}"""
    )
  }

  // ── $pop and $bit operators ───────────────────────────────────────────────

  test(".pop matches Updates.popLast") {
    DslTestKit.assertEquivalentTo(
      field[User](_.tags).pop,
      Updates.popLast("tags")
    )
  }

  test(".popFirst matches Updates.popFirst") {
    DslTestKit.assertEquivalentTo(
      field[User](_.tags).popFirst,
      Updates.popFirst("tags")
    )
  }

  test(".bitAnd matches Updates.bitwiseAnd (Int)") {
    DslTestKit.assertEquivalentTo(
      field[User](_.age).bitAnd(0xFF),
      Updates.bitwiseAnd("age", 0xFF)
    )
  }

  test(".bitOr matches Updates.bitwiseOr (Int)") {
    DslTestKit.assertEquivalentTo(
      field[User](_.age).bitOr(0x04),
      Updates.bitwiseOr("age", 0x04)
    )
  }

  test(".bitXor matches Updates.bitwiseXor (Int)") {
    DslTestKit.assertEquivalentTo(
      field[User](_.age).bitXor(0x0F),
      Updates.bitwiseXor("age", 0x0F)
    )
  }

  test(".bitAnd matches Updates.bitwiseAnd (Long)") {
    DslTestKit.assertEquivalentTo(
      field[User](_.age).bitAnd(0xFFFFFFFFL),
      Updates.bitwiseAnd("age", 0xFFFFFFFFL)
    )
  }

  test("assertEqualsJson: $pop removes last element") {
    DslTestKit.assertEqualsJson(
      field[User](_.tags).pop,
      """{"$pop": {"tags": 1}}"""
    )
  }

  test("assertEqualsJson: $pop removes first element") {
    DslTestKit.assertEqualsJson(
      field[User](_.tags).popFirst,
      """{"$pop": {"tags": -1}}"""
    )
  }

  test("assertEqualsJson: $bit and operation") {
    DslTestKit.assertEqualsJson(
      field[User](_.age).bitAnd(5),
      """{"$bit": {"age": {"and": 5}}}"""
    )
  }

  test("assertEqualsJson: $bit or operation") {
    DslTestKit.assertEqualsJson(
      field[User](_.age).bitOr(3),
      """{"$bit": {"age": {"or": 3}}}"""
    )
  }

  test("assertEqualsJson: $bit xor operation") {
    DslTestKit.assertEqualsJson(
      field[User](_.age).bitXor(7),
      """{"$bit": {"age": {"xor": 7}}}"""
    )
  }

end DslTestKitFiltersSpec
