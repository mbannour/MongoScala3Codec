package io.github.mbannour.mongo.dsl

import io.github.mbannour.fields.MongoPath.syntax.{?, each}
import org.bson.{BsonArray, BsonDocument, BsonInt32, BsonNull, BsonString}
import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DslSpec extends AnyFunSuite with Matchers:

  case class User(
    name: String,
    age: Int,
    active: Boolean,
    scores: List[Int],
    @BsonProperty("email_address") email: String
  )

  case class Address(street: String, city: String, @BsonProperty("zip") zipCode: Int)

  case class Profile(bio: String, address: Option[Address])

  case class UserNested(name: String, age: Int, profile: Option[Profile], tags: List[String])

  // ── Field path resolution ─────────────────────────────────────────────────

  test("field resolves a simple path") {
    field[User](_.name).path shouldBe "name"
    field[User](_.age).path  shouldBe "age"
  }

  test("field resolves a nested path") {
    field[UserNested](_.profile.?.bio).path          shouldBe "profile.bio"
    field[UserNested](_.profile.?.address.?.city).path shouldBe "profile.address.city"
  }

  test("field respects @BsonProperty annotation") {
    field[User](_.email).path shouldBe "email_address"
  }

  test("field respects @BsonProperty in nested path") {
    field[UserNested](_.profile.?.address.?.zipCode).path shouldBe "profile.address.zip"
  }

  test("field resolves element field paths with .each on case-class arrays") {
    case class Skill(name: String, level: String)
    case class Worker(skills: List[Skill])
    field[Worker](_.skills.each.name).path  shouldBe "skills.name"
    field[Worker](_.skills.each.level).path shouldBe "skills.level"
  }

  // ── Equality filters ──────────────────────────────────────────────────────

  test("=== produces an equality filter") {
    val doc = (field[User](_.name) === "John").asInstanceOf[BsonDocument]
    doc.getDocument("name").getString("$eq").getValue shouldBe "John"
  }

  test("=/= produces an inequality filter") {
    val doc = (field[User](_.name) =/= "John").asInstanceOf[BsonDocument]
    doc.getDocument("name").getString("$ne").getValue shouldBe "John"
  }

  // ── Comparison filters ────────────────────────────────────────────────────

  test("> produces a greater-than filter") {
    val doc = (field[User](_.age) > 25).asInstanceOf[BsonDocument]
    doc.getDocument("age").getInt32("$gt").getValue shouldBe 25
  }

  test(">= produces a greater-than-or-equal filter") {
    val doc = (field[User](_.age) >= 18).asInstanceOf[BsonDocument]
    doc.getDocument("age").getInt32("$gte").getValue shouldBe 18
  }

  test("< produces a less-than filter") {
    val doc = (field[User](_.age) < 65).asInstanceOf[BsonDocument]
    doc.getDocument("age").getInt32("$lt").getValue shouldBe 65
  }

  test("<= produces a less-than-or-equal filter") {
    val doc = (field[User](_.age) <= 100).asInstanceOf[BsonDocument]
    doc.getDocument("age").getInt32("$lte").getValue shouldBe 100
  }

  // ── Set membership filters ────────────────────────────────────────────────

  test("in produces a set-membership filter") {
    val doc = field[User](_.name).in("Alice", "Bob", "Carol").asInstanceOf[BsonDocument]
    val arr = doc.getDocument("name").getArray("$in")
    arr.size() shouldBe 3
    arr.get(0).asString().getValue shouldBe "Alice"
    arr.get(1).asString().getValue shouldBe "Bob"
    arr.get(2).asString().getValue shouldBe "Carol"
  }

  test("nin produces a not-in-set filter") {
    val doc = field[User](_.name).nin("Alice", "Bob").asInstanceOf[BsonDocument]
    val arr = doc.getDocument("name").getArray("$nin")
    arr.size() shouldBe 2
  }

  // ── Existence and null filters ────────────────────────────────────────────

  test("exists(true) produces a must-exist filter") {
    val doc = field[User](_.name).exists().asInstanceOf[BsonDocument]
    doc.getDocument("name").getBoolean("$exists").getValue shouldBe true
  }

  test("exists(false) produces a must-not-exist filter") {
    val doc = field[User](_.name).exists(false).asInstanceOf[BsonDocument]
    doc.getDocument("name").getBoolean("$exists").getValue shouldBe false
  }

  test("isNull matches null or missing field") {
    val doc = field[User](_.name).isNull.asInstanceOf[BsonDocument]
    doc.getDocument("name").get("$eq") shouldBe a[BsonNull]
  }

  test("notNull matches non-null field") {
    val doc = field[User](_.name).notNull.asInstanceOf[BsonDocument]
    doc.getDocument("name").get("$ne") shouldBe a[BsonNull]
  }

  // ── Regex filter ──────────────────────────────────────────────────────────

  test("regex produces a regex filter") {
    val doc = field[User](_.name).regex("^Jo").asInstanceOf[BsonDocument]
    doc.getDocument("name").getString("$regex").getValue shouldBe "^Jo"
  }

  test("regex with options appends $options") {
    val doc = field[User](_.name).regex("^jo", "i").asInstanceOf[BsonDocument]
    val inner = doc.getDocument("name")
    inner.getString("$regex").getValue shouldBe "^jo"
    inner.getString("$options").getValue shouldBe "i"
  }

  // ── Array filters ─────────────────────────────────────────────────────────

  test("hasSize produces an array size filter") {
    val doc = field[User](_.scores).hasSize(3).asInstanceOf[BsonDocument]
    doc.getDocument("scores").getInt32("$size").getValue shouldBe 3
  }

  test("elemMatch wraps a sub-filter") {
    val inner = (field[User](_.age) > 5).asInstanceOf[BsonDocument]
    val doc   = field[User](_.scores).elemMatch(inner).asInstanceOf[BsonDocument]
    doc.getDocument("scores").containsKey("$elemMatch") shouldBe true
  }

  // ── Filter combinators ────────────────────────────────────────────────────

  test("Filter.and combines filters with $and") {
    val doc = Filter.and(
      field[User](_.name) === "John",
      field[User](_.age) > 25
    ).asInstanceOf[BsonDocument]
    doc.containsKey("$and") shouldBe true
    doc.getArray("$and").size() shouldBe 2
  }

  test("Filter.and with a single filter is a no-op wrapper") {
    val single = field[User](_.name) === "John"
    Filter.and(single) shouldBe single
  }

  test("Filter.and with no arguments returns empty document") {
    Filter.and().asInstanceOf[BsonDocument].isEmpty shouldBe true
  }

  test("Filter.or combines filters with $or") {
    val doc = Filter.or(
      field[User](_.active) === true,
      field[User](_.age) > 65
    ).asInstanceOf[BsonDocument]
    doc.containsKey("$or") shouldBe true
    doc.getArray("$or").size() shouldBe 2
  }

  test("Filter.nor produces $nor") {
    val doc = Filter.nor(
      field[User](_.name) === "banned"
    ).asInstanceOf[BsonDocument]
    doc.containsKey("$nor") shouldBe true
    doc.getArray("$nor").size() shouldBe 1
  }

  test("Filter.not negates a filter via $nor") {
    val doc = Filter.not(field[User](_.active) === true).asInstanceOf[BsonDocument]
    doc.containsKey("$nor") shouldBe true
    doc.getArray("$nor").size() shouldBe 1
  }

  test("Filter.empty is an empty document") {
    Filter.empty.asInstanceOf[BsonDocument].isEmpty shouldBe true
  }

  // ── Update operators ──────────────────────────────────────────────────────

  test(":= produces a $set update") {
    val doc = (field[User](_.name) := "Jane").asInstanceOf[BsonDocument]
    doc.getDocument("$set").getString("name").getValue shouldBe "Jane"
  }

  test("unset produces a $unset update") {
    val doc = field[User](_.name).unset.asInstanceOf[BsonDocument]
    doc.getDocument("$unset").containsKey("name") shouldBe true
  }

  test("setOnInsert produces a $setOnInsert update") {
    val doc = field[User](_.age).setOnInsert(0).asInstanceOf[BsonDocument]
    doc.getDocument("$setOnInsert").getInt32("age").getValue shouldBe 0
  }

  test("inc produces a $inc update") {
    val doc = field[User](_.age).inc(1).asInstanceOf[BsonDocument]
    doc.getDocument("$inc").getInt32("age").getValue shouldBe 1
  }

  test("mul produces a $mul update") {
    val doc = field[User](_.age).mul(2).asInstanceOf[BsonDocument]
    doc.getDocument("$mul").getInt32("age").getValue shouldBe 2
  }

  test("min produces a $min update") {
    val doc = field[User](_.age).min(0).asInstanceOf[BsonDocument]
    doc.getDocument("$min").getInt32("age").getValue shouldBe 0
  }

  test("max produces a $max update") {
    val doc = field[User](_.age).max(120).asInstanceOf[BsonDocument]
    doc.getDocument("$max").getInt32("age").getValue shouldBe 120
  }

  test("push produces a $push update") {
    val doc = field[User](_.scores).push(42).asInstanceOf[BsonDocument]
    doc.getDocument("$push").getInt32("scores").getValue shouldBe 42
  }

  test("pull produces a $pull update") {
    val doc = field[User](_.scores).pull(5).asInstanceOf[BsonDocument]
    doc.getDocument("$pull").getInt32("scores").getValue shouldBe 5
  }

  test("addToSet produces a $addToSet update") {
    val doc = field[User](_.scores).addToSet(99).asInstanceOf[BsonDocument]
    doc.getDocument("$addToSet").getInt32("scores").getValue shouldBe 99
  }

  // ── Update.combine ────────────────────────────────────────────────────────

  test("Update.combine merges updates with different operators") {
    val doc = Update.combine(
      field[User](_.name) := "Jane",
      field[User](_.age).inc(1)
    ).asInstanceOf[BsonDocument]
    doc.getDocument("$set").getString("name").getValue shouldBe "Jane"
    doc.getDocument("$inc").getInt32("age").getValue shouldBe 1
  }

  test("Update.combine coalesces two $set updates") {
    val doc = Update.combine(
      field[User](_.name)   := "Jane",
      field[User](_.active) := false
    ).asInstanceOf[BsonDocument]
    val set = doc.getDocument("$set")
    set.getString("name").getValue   shouldBe "Jane"
    set.getBoolean("active").getValue shouldBe false
  }

  test("Update.combine with no arguments produces an empty document") {
    Update.combine().asInstanceOf[BsonDocument].isEmpty shouldBe true
  }

  // ── Sort operators ────────────────────────────────────────────────────────

  test("asc produces ascending sort") {
    val doc = field[User](_.age).asc.asInstanceOf[BsonDocument]
    doc.getInt32("age").getValue shouldBe 1
  }

  test("desc produces descending sort") {
    val doc = field[User](_.age).desc.asInstanceOf[BsonDocument]
    doc.getInt32("age").getValue shouldBe -1
  }

  test("Sort.combine merges sort specifications preserving order") {
    val doc = Sort.combine(
      field[User](_.age).desc,
      field[User](_.name).asc
    ).asInstanceOf[BsonDocument]
    doc.getInt32("age").getValue  shouldBe -1
    doc.getInt32("name").getValue shouldBe 1
    doc.keySet().toArray.toList   shouldBe List("age", "name")
  }

  test("Sort.ascending and Sort.descending accept raw paths") {
    Sort.ascending("age").asInstanceOf[BsonDocument].getInt32("age").getValue   shouldBe 1
    Sort.descending("age").asInstanceOf[BsonDocument].getInt32("age").getValue  shouldBe -1
  }

  // ── Projection operators ──────────────────────────────────────────────────

  test("include produces an include projection") {
    val doc = field[User](_.name).include.asInstanceOf[BsonDocument]
    doc.getInt32("name").getValue shouldBe 1
  }

  test("exclude produces an exclude projection") {
    val doc = field[User](_.name).exclude.asInstanceOf[BsonDocument]
    doc.getInt32("name").getValue shouldBe 0
  }

  test("Projection.combine merges projection specs") {
    val doc = Projection.combine(
      field[User](_.name).include,
      field[User](_.age).include,
      Projection.excludeId
    ).asInstanceOf[BsonDocument]
    doc.getInt32("name").getValue shouldBe 1
    doc.getInt32("age").getValue  shouldBe 1
    doc.getInt32("_id").getValue  shouldBe 0
  }

  test("Projection.excludeId produces { _id: 0 }") {
    Projection.excludeId.asInstanceOf[BsonDocument].getInt32("_id").getValue shouldBe 0
  }

  test("Projection.slice produces a $slice projection") {
    val doc = Projection.slice("scores", 5).asInstanceOf[BsonDocument]
    doc.getDocument("scores").getInt32("$slice").getValue shouldBe 5
  }

  test("Projection.slice with skip and limit produces a $slice array") {
    val doc = Projection.slice("scores", 2, 5).asInstanceOf[BsonDocument]
    val arr = doc.getDocument("scores").getArray("$slice")
    arr.get(0).asInt32().getValue shouldBe 2
    arr.get(1).asInt32().getValue shouldBe 5
  }

  // ── BsonEncoder ───────────────────────────────────────────────────────────

  test("BsonEncoder encodes primitive types") {
    BsonEncoder[String].encode("hello")  shouldBe new BsonString("hello")
    BsonEncoder[Int].encode(42).asInt32().getValue    shouldBe 42
    BsonEncoder[Long].encode(99L).asInt64().getValue  shouldBe 99L
    BsonEncoder[Double].encode(3.14).asDouble().getValue should be (3.14 +- 0.001)
    BsonEncoder[Boolean].encode(true).asBoolean().getValue shouldBe true
  }

  test("BsonEncoder encodes Option") {
    BsonEncoder[Option[String]].encode(Some("x")) shouldBe new BsonString("x")
    BsonEncoder[Option[String]].encode(None)      shouldBe a[BsonNull]
  }

  test("BsonEncoder encodes List") {
    val arr = BsonEncoder[List[Int]].encode(List(1, 2, 3)).asInstanceOf[BsonArray]
    arr.size()                       shouldBe 3
    arr.get(0).asInt32().getValue    shouldBe 1
  }

  test("BsonEncoder.apply summons an instance") {
    val enc = BsonEncoder[Int]
    enc.encode(7).asInt32().getValue shouldBe 7
  }

  // ── Type safety (compile-time checks) ────────────────────────────────────
  // The expressions below would fail to compile if the types were wrong.
  // They demonstrate that Field[Doc, A] operators only accept values of type A.

  // ── $all filter ───────────────────────────────────────────────────────────

  test("all produces a $all array filter") {
    val doc = field[User](_.scores).all(1, 2, 3).asInstanceOf[BsonDocument]
    val arr = doc.getDocument("scores").getArray("$all")
    arr.size() shouldBe 3
    arr.get(0).asInt32().getValue shouldBe 1
    arr.get(2).asInt32().getValue shouldBe 3
  }

  // ── $type filter ──────────────────────────────────────────────────────────

  test("hasType with string produces a $type filter") {
    val doc = field[User](_.name).hasType("string").asInstanceOf[BsonDocument]
    doc.getDocument("name").getString("$type").getValue shouldBe "string"
  }

  test("hasType with int produces a $type filter") {
    val doc = field[User](_.age).hasType(16).asInstanceOf[BsonDocument]
    doc.getDocument("age").getInt32("$type").getValue shouldBe 16
  }

  // ── $mod filter ───────────────────────────────────────────────────────────

  test("mod produces a $mod filter with divisor and remainder") {
    val doc = field[User](_.age).mod(2L, 0L).asInstanceOf[BsonDocument]
    val arr = doc.getDocument("age").getArray("$mod")
    arr.size() shouldBe 2
    arr.get(0).asInt64().getValue shouldBe 2L
    arr.get(1).asInt64().getValue shouldBe 0L
  }

  // ── Filter.text ───────────────────────────────────────────────────────────

  test("Filter.text produces a $text filter with $search") {
    val doc = Filter.text("coffee shop").asInstanceOf[BsonDocument]
    doc.getDocument("$text").getString("$search").getValue shouldBe "coffee shop"
  }

  test("Filter.text with language appends $language") {
    val doc = Filter.text("café", language = Some("fr")).asInstanceOf[BsonDocument]
    val inner = doc.getDocument("$text")
    inner.getString("$search").getValue shouldBe "café"
    inner.getString("$language").getValue shouldBe "fr"
  }

  test("Filter.text with caseSensitive appends $caseSensitive") {
    val doc = Filter.text("Hello", caseSensitive = Some(true)).asInstanceOf[BsonDocument]
    doc.getDocument("$text").getBoolean("$caseSensitive").getValue shouldBe true
  }

  test("Filter.text with diacriticSensitive appends $diacriticSensitive") {
    val doc = Filter.text("résumé", diacriticSensitive = Some(true)).asInstanceOf[BsonDocument]
    doc.getDocument("$text").getBoolean("$diacriticSensitive").getValue shouldBe true
  }

  // ── $pullAll update ───────────────────────────────────────────────────────

  test("pullAll produces a $pullAll update") {
    val doc = field[User](_.scores).pullAll(1, 2, 3).asInstanceOf[BsonDocument]
    val arr = doc.getDocument("$pullAll").getArray("scores")
    arr.size() shouldBe 3
    arr.get(0).asInt32().getValue shouldBe 1
    arr.get(1).asInt32().getValue shouldBe 2
    arr.get(2).asInt32().getValue shouldBe 3
  }

  // ── pushEach update ───────────────────────────────────────────────────────

  test("pushEach produces $push with $each") {
    val doc = field[User](_.scores).pushEach(Seq(10, 20, 30)).asInstanceOf[BsonDocument]
    val each = doc.getDocument("$push").getDocument("scores").getArray("$each")
    each.size() shouldBe 3
    each.get(0).asInt32().getValue shouldBe 10
    each.get(2).asInt32().getValue shouldBe 30
  }

  test("pushEach with position appends $position") {
    val doc = field[User](_.scores).pushEach(Seq(5), position = Some(0)).asInstanceOf[BsonDocument]
    val modifiers = doc.getDocument("$push").getDocument("scores")
    modifiers.getInt32("$position").getValue shouldBe 0
  }

  test("pushEach with slice appends $slice") {
    val doc = field[User](_.scores).pushEach(Seq(5), slice = Some(10)).asInstanceOf[BsonDocument]
    val modifiers = doc.getDocument("$push").getDocument("scores")
    modifiers.getInt32("$slice").getValue shouldBe 10
  }

  test("pushEach with sort appends $sort") {
    val sortDoc = new BsonDocument("score", new BsonInt32(-1))
    val doc = field[User](_.scores).pushEach(Seq(5), sort = Some(sortDoc)).asInstanceOf[BsonDocument]
    val modifiers = doc.getDocument("$push").getDocument("scores")
    modifiers.containsKey("$sort") shouldBe true
  }

  // ── $rename update ────────────────────────────────────────────────────────

  test("rename produces a $rename update") {
    val doc = field[User](_.name).rename("fullName").asInstanceOf[BsonDocument]
    doc.getDocument("$rename").getString("name").getValue shouldBe "fullName"
  }

  // ── $currentDate update ───────────────────────────────────────────────────

  test("currentDate produces a $currentDate update with boolean true") {
    val doc = field[User](_.name).currentDate.asInstanceOf[BsonDocument]
    doc.getDocument("$currentDate").getBoolean("name").getValue shouldBe true
  }

  test("currentTimestamp produces a $currentDate update with timestamp type") {
    val doc = field[User](_.name).currentTimestamp.asInstanceOf[BsonDocument]
    doc.getDocument("$currentDate").getDocument("name").getString("$type").getValue shouldBe "timestamp"
  }

  // ── $pop update ───────────────────────────────────────────────────────────

  test("pop produces $pop with 1 (removes last element)") {
    val doc = field[User](_.scores).pop.asInstanceOf[BsonDocument]
    doc.getDocument("$pop").getInt32("scores").getValue shouldBe 1
  }

  test("popFirst produces $pop with -1 (removes first element)") {
    val doc = field[User](_.scores).popFirst.asInstanceOf[BsonDocument]
    doc.getDocument("$pop").getInt32("scores").getValue shouldBe -1
  }

  // ── $bit update ───────────────────────────────────────────────────────────

  test("bitAnd produces $bit with and operation (Int)") {
    val doc = field[User](_.age).bitAnd(0xFF).asInstanceOf[BsonDocument]
    doc.getDocument("$bit").getDocument("age").getInt32("and").getValue shouldBe 0xFF
  }

  test("bitOr produces $bit with or operation (Int)") {
    val doc = field[User](_.age).bitOr(0x04).asInstanceOf[BsonDocument]
    doc.getDocument("$bit").getDocument("age").getInt32("or").getValue shouldBe 0x04
  }

  test("bitXor produces $bit with xor operation (Int)") {
    val doc = field[User](_.age).bitXor(0x0F).asInstanceOf[BsonDocument]
    doc.getDocument("$bit").getDocument("age").getInt32("xor").getValue shouldBe 0x0F
  }

  test("bitAnd produces $bit with and operation (Long)") {
    val doc = field[User](_.age).bitAnd(0xFFFFFFFFL).asInstanceOf[BsonDocument]
    doc.getDocument("$bit").getDocument("age").getInt64("and").getValue shouldBe 0xFFFFFFFFL
  }

  test("bitOr produces $bit with or operation (Long)") {
    val doc = field[User](_.age).bitOr(0x04L).asInstanceOf[BsonDocument]
    doc.getDocument("$bit").getDocument("age").getInt64("or").getValue shouldBe 0x04L
  }

  test("bitXor produces $bit with xor operation (Long)") {
    val doc = field[User](_.age).bitXor(0x0FL).asInstanceOf[BsonDocument]
    doc.getDocument("$bit").getDocument("age").getInt64("xor").getValue shouldBe 0x0FL
  }

  // ── Type safety (compile-time checks) ────────────────────────────────────
  // The expressions below would fail to compile if the types were wrong.
  // They demonstrate that Field[Doc, A] operators only accept values of type A.

  test("field infers the correct type parameter") {
    val nameField  = field[User](_.name)   // Field[User, String]
    val ageField   = field[User](_.age)    // Field[User, Int]
    val boolField  = field[User](_.active) // Field[User, Boolean]

    // Each operator only accepts the matching type:
    nameField === "x"    // String ✓
    ageField > 0         // Int ✓
    boolField === false  // Boolean ✓

    succeed
  }
