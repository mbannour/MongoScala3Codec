package io.github.mbannour.mongo.dsl

import org.bson.{BsonDocument, BsonInt32, BsonNull}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AggregationSpec extends AnyFunSuite with Matchers:

  case class Employee(name: String, dept: String, salary: Int, active: Boolean, tags: List[String])
  case class Order(orderId: String, amount: Double, items: List[String])

  // ── Field.ref ─────────────────────────────────────────────────────────────

  test("Field.ref prepends $ to the field path") {
    field[Employee](_.name).ref.getValue   shouldBe "$name"
    field[Employee](_.salary).ref.getValue shouldBe "$salary"
  }

  test("Field.ref respects nested path") {
    case class Address(city: String)
    case class User(address: Option[Address])
    import io.github.mbannour.fields.MongoPath.syntax.?
    field[User](_.address.?.city).ref.getValue shouldBe "$address.city"
  }

  // ── Expr.fieldRef ─────────────────────────────────────────────────────────

  test("Expr.fieldRef adds $ prefix when absent") {
    Expr.fieldRef("salary").getValue   shouldBe "$salary"
    Expr.fieldRef("$salary").getValue  shouldBe "$salary"
  }

  // ── Expr.literal ─────────────────────────────────────────────────────────

  test("Expr.literal wraps value in $literal") {
    val doc = Expr.literal(12)
    doc.get("$literal").asInt32().getValue shouldBe 12
  }

  test("Expr.literal works with strings and booleans") {
    Expr.literal("hello").get("$literal").asString().getValue  shouldBe "hello"
    Expr.literal(true).get("$literal").asBoolean().getValue    shouldBe true
  }

  // ── Arithmetic expressions ─────────────────────────────────────────────────

  test("Expr.add produces { $add: [...] }") {
    val doc = Expr.add(Expr.fieldRef("salary"), Expr.literal(1000))
    val arr = doc.getArray("$add")
    arr.size() shouldBe 2
    arr.get(0).asString().getValue shouldBe "$salary"
  }

  test("Expr.subtract produces { $subtract: [a, b] }") {
    val doc = Expr.subtract(Expr.fieldRef("salary"), Expr.literal(500))
    doc.getArray("$subtract").size() shouldBe 2
  }

  test("Expr.multiply produces { $multiply: [...] }") {
    val doc = Expr.multiply(Expr.fieldRef("price"), Expr.fieldRef("qty"))
    doc.getArray("$multiply").size() shouldBe 2
  }

  test("Expr.divide produces { $divide: [a, b] }") {
    val doc = Expr.divide(Expr.fieldRef("salary"), Expr.literal(12))
    doc.getArray("$divide").get(1).asDocument().get("$literal").asInt32().getValue shouldBe 12
  }

  test("Expr.abs, ceil, floor, sqrt, trunc wrap a single expression") {
    Expr.abs(Expr.fieldRef("x")).containsKey("$abs")   shouldBe true
    Expr.ceil(Expr.fieldRef("x")).containsKey("$ceil") shouldBe true
    Expr.floor(Expr.fieldRef("x")).containsKey("$floor") shouldBe true
    Expr.sqrt(Expr.fieldRef("x")).containsKey("$sqrt") shouldBe true
    Expr.trunc(Expr.fieldRef("x")).containsKey("$trunc") shouldBe true
  }

  test("Expr.round includes place argument") {
    val doc = Expr.round(Expr.fieldRef("price"), 2)
    val arr = doc.getArray("$round")
    arr.get(1).asInt32().getValue shouldBe 2
  }

  test("Expr.pow produces { $pow: [base, exp] }") {
    val doc = Expr.pow(Expr.fieldRef("x"), Expr.literal(2))
    doc.getArray("$pow").size() shouldBe 2
  }

  // ── Conditional expressions ────────────────────────────────────────────────

  test("Expr.cond produces { $cond: [if, then, else] }") {
    val doc = Expr.cond(
      Expr.gte(Expr.fieldRef("age"), Expr.literal(18)),
      Expr.literal("adult"),
      Expr.literal("minor")
    )
    val arr = doc.getArray("$cond")
    arr.size() shouldBe 3
  }

  test("Expr.ifNull produces { $ifNull: [expr, replacement] }") {
    val doc = Expr.ifNull(Expr.fieldRef("nickname"), Expr.fieldRef("name"))
    doc.getArray("$ifNull").size() shouldBe 2
  }

  test("Expr.switch produces branches + default") {
    val doc = Expr.switch(
      Expr.gte(Expr.fieldRef("score"), Expr.literal(90)) -> Expr.literal("A"),
      Expr.gte(Expr.fieldRef("score"), Expr.literal(80)) -> Expr.literal("B")
    )(Expr.literal("C"))
    val spec = doc.getDocument("$switch")
    spec.getArray("branches").size() shouldBe 2
    spec.get("default").asDocument().get("$literal").asString().getValue shouldBe "C"
  }

  // ── Logical expressions ────────────────────────────────────────────────────

  test("Expr.and / or / not produce expression-form arrays") {
    val andDoc = Expr.and(Expr.fieldRef("a"), Expr.fieldRef("b"))
    andDoc.getArray("$and").size() shouldBe 2

    val orDoc = Expr.or(Expr.fieldRef("x"), Expr.fieldRef("y"))
    orDoc.getArray("$or").size() shouldBe 2

    val notDoc = Expr.not(Expr.fieldRef("active"))
    notDoc.getArray("$not").size() shouldBe 1
  }

  // ── Comparison expressions ─────────────────────────────────────────────────

  test("Expr comparison operators produce two-element arrays") {
    Expr.eq(Expr.fieldRef("x"), Expr.literal(1)).getArray("$eq").size()   shouldBe 2
    Expr.ne(Expr.fieldRef("x"), Expr.literal(1)).getArray("$ne").size()   shouldBe 2
    Expr.gt(Expr.fieldRef("x"), Expr.literal(1)).getArray("$gt").size()   shouldBe 2
    Expr.gte(Expr.fieldRef("x"), Expr.literal(1)).getArray("$gte").size() shouldBe 2
    Expr.lt(Expr.fieldRef("x"), Expr.literal(1)).getArray("$lt").size()   shouldBe 2
    Expr.lte(Expr.fieldRef("x"), Expr.literal(1)).getArray("$lte").size() shouldBe 2
  }

  // ── String expressions ─────────────────────────────────────────────────────

  test("Expr.concat produces { $concat: [...] }") {
    val doc = Expr.concat(Expr.fieldRef("first"), Expr.literal(" "), Expr.fieldRef("last"))
    doc.getArray("$concat").size() shouldBe 3
  }

  test("Expr.toUpper / toLower wrap a single expression") {
    Expr.toUpper(Expr.fieldRef("name")).containsKey("$toUpper") shouldBe true
    Expr.toLower(Expr.fieldRef("name")).containsKey("$toLower") shouldBe true
  }

  test("Expr.trim / ltrim / rtrim wrap input in a spec document") {
    Expr.trim(Expr.fieldRef("name")).getDocument("$trim").containsKey("input")  shouldBe true
    Expr.ltrim(Expr.fieldRef("name")).getDocument("$ltrim").containsKey("input") shouldBe true
    Expr.rtrim(Expr.fieldRef("name")).getDocument("$rtrim").containsKey("input") shouldBe true
  }

  test("Expr.split produces { $split: [str, delimiter] }") {
    val doc = Expr.split(Expr.fieldRef("email"), Expr.literal("@"))
    doc.getArray("$split").size() shouldBe 2
  }

  test("Expr.strLenCP wraps a single expression") {
    Expr.strLenCP(Expr.fieldRef("name")).containsKey("$strLenCP") shouldBe true
  }

  test("Expr.regexMatch includes input, regex, and optional options") {
    val doc = Expr.regexMatch(Expr.fieldRef("email"), "^admin", "i")
    val spec = doc.getDocument("$regexMatch")
    spec.getString("regex").getValue   shouldBe "^admin"
    spec.getString("options").getValue shouldBe "i"
  }

  // ── Type-conversion expressions ────────────────────────────────────────────

  test("Expr.toString / toInt / toLong / toDouble / toBool wrap single expressions") {
    Expr.toString(Expr.fieldRef("age")).containsKey("$toString")  shouldBe true
    Expr.toInt(Expr.fieldRef("str")).containsKey("$toInt")        shouldBe true
    Expr.toLong(Expr.fieldRef("n")).containsKey("$toLong")        shouldBe true
    Expr.toDouble(Expr.fieldRef("n")).containsKey("$toDouble")    shouldBe true
    Expr.toBool(Expr.fieldRef("n")).containsKey("$toBool")        shouldBe true
    Expr.typeOf(Expr.fieldRef("x")).containsKey("$type")          shouldBe true
  }

  test("Expr.convert produces a spec with input and to") {
    val doc = Expr.convert(Expr.fieldRef("price"), "int")
    val spec = doc.getDocument("$convert")
    spec.getString("to").getValue shouldBe "int"
  }

  // ── Date expressions ───────────────────────────────────────────────────────

  test("Expr date parts wrap a single expression") {
    Expr.year(Expr.fieldRef("createdAt")).containsKey("$year")         shouldBe true
    Expr.month(Expr.fieldRef("createdAt")).containsKey("$month")       shouldBe true
    Expr.dayOfMonth(Expr.fieldRef("d")).containsKey("$dayOfMonth")     shouldBe true
    Expr.hour(Expr.fieldRef("d")).containsKey("$hour")                 shouldBe true
    Expr.minute(Expr.fieldRef("d")).containsKey("$minute")             shouldBe true
    Expr.second(Expr.fieldRef("d")).containsKey("$second")             shouldBe true
    Expr.millisecond(Expr.fieldRef("d")).containsKey("$millisecond")   shouldBe true
    Expr.isoWeek(Expr.fieldRef("d")).containsKey("$isoWeek")           shouldBe true
    Expr.isoDayOfWeek(Expr.fieldRef("d")).containsKey("$isoDayOfWeek") shouldBe true
  }

  test("Expr.dateToString includes format and date") {
    val doc = Expr.dateToString("%Y-%m-%d", Expr.fieldRef("createdAt"))
    val spec = doc.getDocument("$dateToString")
    spec.getString("format").getValue shouldBe "%Y-%m-%d"
    spec.containsKey("date") shouldBe true
  }

  test("Expr.dateAdd includes startDate, unit, and amount") {
    val doc = Expr.dateAdd(Expr.fieldRef("start"), "day", Expr.literal(7))
    val spec = doc.getDocument("$dateAdd")
    spec.getString("unit").getValue shouldBe "day"
  }

  test("Expr.dateDiff includes startDate, endDate, and unit") {
    val doc = Expr.dateDiff(Expr.fieldRef("start"), Expr.fieldRef("end"), "month")
    val spec = doc.getDocument("$dateDiff")
    spec.getString("unit").getValue shouldBe "month"
  }

  // ── Array expressions ──────────────────────────────────────────────────────

  test("Expr.size wraps a single expression") {
    Expr.size(Expr.fieldRef("tags")).containsKey("$size") shouldBe true
  }

  test("Expr.arrayElemAt produces a two-element array") {
    val doc = Expr.arrayElemAt(Expr.fieldRef("scores"), 0)
    doc.getArray("$arrayElemAt").get(1).asInt32().getValue shouldBe 0
  }

  test("Expr.first / last wrap a single expression") {
    Expr.first(Expr.fieldRef("items")).containsKey("$first") shouldBe true
    Expr.last(Expr.fieldRef("items")).containsKey("$last")   shouldBe true
  }

  test("Expr.slice with two args produces a two-element array") {
    val doc = Expr.slice(Expr.fieldRef("items"), 3)
    doc.getArray("$slice").size() shouldBe 2
  }

  test("Expr.slice with position and n produces a three-element array") {
    val doc = Expr.slice(Expr.fieldRef("items"), 1, 3)
    doc.getArray("$slice").size() shouldBe 3
  }

  test("Expr.reverseArray wraps a single expression") {
    Expr.reverseArray(Expr.fieldRef("tags")).containsKey("$reverseArray") shouldBe true
  }

  test("Expr.concatArrays produces { $concatArrays: [...] }") {
    val doc = Expr.concatArrays(Expr.fieldRef("a"), Expr.fieldRef("b"))
    doc.getArray("$concatArrays").size() shouldBe 2
  }

  test("Expr.filter produces input / as / cond spec") {
    val doc = Expr.filter(Expr.fieldRef("scores"), "s", Expr.gte(Expr.fieldRef("$$s"), Expr.literal(50)))
    val spec = doc.getDocument("$filter")
    spec.getString("as").getValue shouldBe "s"
    spec.containsKey("cond") shouldBe true
  }

  test("Expr.map produces input / as / in spec") {
    val doc = Expr.map(Expr.fieldRef("items"), "item", Expr.toUpper(Expr.fieldRef("$$item")))
    val spec = doc.getDocument("$map")
    spec.getString("as").getValue shouldBe "item"
  }

  test("Expr.reduce produces input / initialValue / in spec") {
    val doc = Expr.reduce(
      Expr.fieldRef("nums"),
      Expr.literal(0),
      Expr.add(Expr.fieldRef("$$value"), Expr.fieldRef("$$this"))
    )
    val spec = doc.getDocument("$reduce")
    spec.containsKey("initialValue") shouldBe true
  }

  test("Expr.range produces a two-element array") {
    Expr.range(Expr.literal(0), Expr.literal(10)).getArray("$range").size() shouldBe 2
  }

  test("Expr.range with step produces a three-element array") {
    Expr.range(Expr.literal(0), Expr.literal(10), Expr.literal(2)).getArray("$range").size() shouldBe 3
  }

  test("Expr.in produces { $in: [expr, array] }") {
    val doc = Expr.in(Expr.fieldRef("dept"), Expr.fieldRef("validDepts"))
    doc.getArray("$in").size() shouldBe 2
  }

  test("Expr.sortArray includes input and sortBy") {
    val doc = Expr.sortArray(Expr.fieldRef("items"), new BsonInt32(1))
    val spec = doc.getDocument("$sortArray")
    spec.containsKey("input") shouldBe true
    spec.containsKey("sortBy") shouldBe true
  }

  test("Expr.zip produces inputs array") {
    val doc = Expr.zip(Expr.fieldRef("a"), Expr.fieldRef("b"))
    doc.getDocument("$zip").getArray("inputs").size() shouldBe 2
  }

  // ── Object expressions ─────────────────────────────────────────────────────

  test("Expr.mergeObjects produces { $mergeObjects: [...] }") {
    val doc = Expr.mergeObjects(Expr.fieldRef("a"), Expr.fieldRef("b"))
    doc.getArray("$mergeObjects").size() shouldBe 2
  }

  test("Expr.objectToArray / arrayToObject wrap a single expression") {
    Expr.objectToArray(Expr.fieldRef("doc")).containsKey("$objectToArray") shouldBe true
    Expr.arrayToObject(Expr.fieldRef("pairs")).containsKey("$arrayToObject") shouldBe true
  }

  // ── Set expressions ────────────────────────────────────────────────────────

  test("Expr.setUnion / setIntersection / setDifference / setEquals") {
    Expr.setUnion(Expr.fieldRef("a"), Expr.fieldRef("b")).containsKey("$setUnion")        shouldBe true
    Expr.setIntersection(Expr.fieldRef("a"), Expr.fieldRef("b")).containsKey("$setIntersection") shouldBe true
    Expr.setDifference(Expr.fieldRef("a"), Expr.fieldRef("b")).containsKey("$setDifference") shouldBe true
    Expr.setEquals(Expr.fieldRef("a"), Expr.fieldRef("b")).containsKey("$setEquals")      shouldBe true
  }

  test("Expr.anyElementTrue / allElementsTrue wrap a single expression") {
    Expr.anyElementTrue(Expr.fieldRef("flags")).containsKey("$anyElementTrue") shouldBe true
    Expr.allElementsTrue(Expr.fieldRef("flags")).containsKey("$allElementsTrue") shouldBe true
  }

  // ── Accumulator ───────────────────────────────────────────────────────────

  test("Accumulator.sum with expr produces { $sum: expr }") {
    val doc = Accumulator.sum(Expr.fieldRef("salary"))
    doc.get("$sum").asString().getValue shouldBe "$salary"
  }

  test("Accumulator.sum with int produces { $sum: n }") {
    Accumulator.sum(1).get("$sum").asInt32().getValue shouldBe 1
  }

  test("Accumulator.avg / min / max / first / last / push / addToSet") {
    Accumulator.avg(Expr.fieldRef("x")).containsKey("$avg")      shouldBe true
    Accumulator.min(Expr.fieldRef("x")).containsKey("$min")      shouldBe true
    Accumulator.max(Expr.fieldRef("x")).containsKey("$max")      shouldBe true
    Accumulator.first(Expr.fieldRef("x")).containsKey("$first")  shouldBe true
    Accumulator.last(Expr.fieldRef("x")).containsKey("$last")    shouldBe true
    Accumulator.push(Expr.fieldRef("x")).containsKey("$push")    shouldBe true
    Accumulator.addToSet(Expr.fieldRef("x")).containsKey("$addToSet") shouldBe true
  }

  test("Accumulator.count produces { $count: {} }") {
    Accumulator.count.get("$count") shouldBe a[BsonDocument]
    Accumulator.count.getDocument("$count").isEmpty shouldBe true
  }

  test("Accumulator.stdDevPop / stdDevSamp / mergeObjects") {
    Accumulator.stdDevPop(Expr.fieldRef("x")).containsKey("$stdDevPop")    shouldBe true
    Accumulator.stdDevSamp(Expr.fieldRef("x")).containsKey("$stdDevSamp")  shouldBe true
    Accumulator.mergeObjects(Expr.fieldRef("x")).containsKey("$mergeObjects") shouldBe true
  }

  // ── Stage.$match ──────────────────────────────────────────────────────────

  test("Stage.match wraps a DSL filter in $match") {
    val stage = Stage.`match`(field[Employee](_.active) === true).asInstanceOf[BsonDocument]
    stage.containsKey("$match") shouldBe true
    stage.getDocument("$match").containsKey("active") shouldBe true
  }

  // ── Stage.$sort ───────────────────────────────────────────────────────────

  test("Stage.sort wraps a sort spec in $sort") {
    val stage = Stage.sort(field[Employee](_.salary).desc).asInstanceOf[BsonDocument]
    stage.getDocument("$sort").getInt32("salary").getValue shouldBe -1
  }

  // ── Stage.$limit / $skip ──────────────────────────────────────────────────

  test("Stage.limit produces { $limit: n }") {
    Stage.limit(10).asInstanceOf[BsonDocument].getInt64("$limit").getValue shouldBe 10L
  }

  test("Stage.skip produces { $skip: n }") {
    Stage.skip(5).asInstanceOf[BsonDocument].getInt64("$skip").getValue shouldBe 5L
  }

  // ── Stage.$count ──────────────────────────────────────────────────────────

  test("Stage.count produces { $count: fieldName }") {
    Stage.count("total").asInstanceOf[BsonDocument].getString("$count").getValue shouldBe "total"
  }

  // ── Stage.$sample ─────────────────────────────────────────────────────────

  test("Stage.sample produces { $sample: { size: n } }") {
    Stage.sample(100L).asInstanceOf[BsonDocument].getDocument("$sample").getInt64("size").getValue shouldBe 100L
  }

  // ── Stage.$out ────────────────────────────────────────────────────────────

  test("Stage.out produces { $out: collectionName }") {
    Stage.out("results").asInstanceOf[BsonDocument].getString("$out").getValue shouldBe "results"
  }

  test("Stage.out with db produces { $out: { db, coll } }") {
    val doc = Stage.out("mydb", "results").asInstanceOf[BsonDocument].getDocument("$out")
    doc.getString("db").getValue   shouldBe "mydb"
    doc.getString("coll").getValue shouldBe "results"
  }

  // ── Stage.$merge ──────────────────────────────────────────────────────────

  test("Stage.merge produces $merge spec with defaults") {
    val doc = Stage.merge("archive").asInstanceOf[BsonDocument].getDocument("$merge")
    doc.getString("into").getValue         shouldBe "archive"
    doc.getString("on").getValue           shouldBe "_id"
    doc.getString("whenMatched").getValue  shouldBe "merge"
    doc.getString("whenNotMatched").getValue shouldBe "insert"
  }

  // ── Stage.$unwind ─────────────────────────────────────────────────────────

  test("Stage.unwind with defaults produces shorthand string form") {
    val stage = Stage.unwind("$tags").asInstanceOf[BsonDocument]
    stage.getString("$unwind").getValue shouldBe "$tags"
  }

  test("Stage.unwind with preserveNull produces full spec form") {
    val stage = Stage.unwind("$tags", preserveNullAndEmpty = true).asInstanceOf[BsonDocument]
    val spec = stage.getDocument("$unwind")
    spec.getString("path").getValue shouldBe "$tags"
    spec.getBoolean("preserveNullAndEmptyArrays").getValue shouldBe true
  }

  test("Stage.unwind with includeArrayIndex appends that field") {
    val stage = Stage.unwind("$tags", includeArrayIndex = Some("tagIndex")).asInstanceOf[BsonDocument]
    stage.getDocument("$unwind").getString("includeArrayIndex").getValue shouldBe "tagIndex"
  }

  // ── Stage.$project ────────────────────────────────────────────────────────

  test("Stage.project merges inclusion specs") {
    val stage = Stage.project(
      field[Employee](_.name).include,
      field[Employee](_.dept).include,
      Projection.excludeId
    ).asInstanceOf[BsonDocument]
    val spec = stage.getDocument("$project")
    spec.getInt32("name").getValue shouldBe 1
    spec.getInt32("dept").getValue shouldBe 1
    spec.getInt32("_id").getValue  shouldBe 0
  }

  test("Stage.computed creates a single-field document for use in project") {
    val c = Stage.computed("monthly", Expr.divide(Expr.fieldRef("salary"), Expr.literal(12)))
    c.asInstanceOf[BsonDocument].containsKey("monthly") shouldBe true
  }

  test("Stage.project with computed field embeds expression") {
    val stage = Stage.project(
      field[Employee](_.name).include,
      Stage.computed("monthly", Expr.divide(field[Employee](_.salary).ref, Expr.literal(12)))
    ).asInstanceOf[BsonDocument]
    val spec = stage.getDocument("$project")
    spec.containsKey("name")    shouldBe true
    spec.containsKey("monthly") shouldBe true
  }

  // ── Stage.$addFields / $set ────────────────────────────────────────────────

  test("Stage.addFields produces { $addFields: { ... } }") {
    val stage = Stage.addFields(
      "isActive" -> Expr.eq(field[Employee](_.active).ref, Expr.literal(true))
    ).asInstanceOf[BsonDocument]
    stage.getDocument("$addFields").containsKey("isActive") shouldBe true
  }

  test("Stage.set is an alias for addFields with $set key") {
    val stage = Stage.set(
      "upper" -> Expr.toUpper(field[Employee](_.name).ref)
    ).asInstanceOf[BsonDocument]
    stage.getDocument("$set").containsKey("upper") shouldBe true
  }

  test("Stage.unset with one field produces string form") {
    Stage.unset("tmp").asInstanceOf[BsonDocument].getString("$unset").getValue shouldBe "tmp"
  }

  test("Stage.unset with multiple fields produces array form") {
    val doc = Stage.unset("a", "b", "c").asInstanceOf[BsonDocument]
    doc.getArray("$unset").size() shouldBe 3
  }

  // ── Stage.$replaceRoot / $replaceWith ──────────────────────────────────────

  test("Stage.replaceRoot wraps newRoot in { $replaceRoot: { newRoot: ... } }") {
    val stage = Stage.replaceRoot(Expr.fieldRef("$address")).asInstanceOf[BsonDocument]
    stage.getDocument("$replaceRoot").containsKey("newRoot") shouldBe true
  }

  test("Stage.replaceWith produces { $replaceWith: expr }") {
    val stage = Stage.replaceWith(Expr.fieldRef("$embedded")).asInstanceOf[BsonDocument]
    stage.getString("$replaceWith").getValue shouldBe "$embedded"
  }

  // ── Stage.$group ──────────────────────────────────────────────────────────

  test("Stage.group produces { $group: { _id: expr, ...accumulators } }") {
    val stage = Stage.group(field[Employee](_.dept).ref)(
      "headcount" -> Accumulator.count,
      "total"     -> Accumulator.sum(field[Employee](_.salary).ref)
    ).asInstanceOf[BsonDocument]
    val spec = stage.getDocument("$group")
    spec.getString("_id").getValue       shouldBe "$dept"
    spec.containsKey("headcount")        shouldBe true
    spec.containsKey("total")            shouldBe true
  }

  test("Stage.groupAll uses null id") {
    val stage = Stage.groupAll("count" -> Accumulator.sum(1)).asInstanceOf[BsonDocument]
    stage.getDocument("$group").get("_id") shouldBe a[BsonNull]
  }

  // ── Stage.$lookup ─────────────────────────────────────────────────────────

  test("Stage.lookup produces standard equi-join spec") {
    val stage = Stage.lookup("orders", "userId", "customerId", "userOrders")
      .asInstanceOf[BsonDocument].getDocument("$lookup")
    stage.getString("from").getValue          shouldBe "orders"
    stage.getString("localField").getValue    shouldBe "userId"
    stage.getString("foreignField").getValue  shouldBe "customerId"
    stage.getString("as").getValue            shouldBe "userOrders"
  }

  test("Stage.lookupPipeline includes pipeline and optional let") {
    val sub = Pipeline(
      Stage.`match`(field[Order](_.amount) > 100.0)
    )
    val stage = Stage.lookupPipeline("orders", Seq("uid" -> Expr.fieldRef("_id")), sub, "bigOrders")
      .asInstanceOf[BsonDocument].getDocument("$lookup")
    stage.getString("from").getValue shouldBe "orders"
    stage.getString("as").getValue   shouldBe "bigOrders"
    stage.containsKey("let")         shouldBe true
    stage.containsKey("pipeline")    shouldBe true
  }

  // ── Stage.$sortByCount ────────────────────────────────────────────────────

  test("Stage.sortByCount wraps expression in $sortByCount") {
    Stage.sortByCount(field[Employee](_.dept).ref)
      .asInstanceOf[BsonDocument].getString("$sortByCount").getValue shouldBe "$dept"
  }

  // ── Stage.$facet ──────────────────────────────────────────────────────────

  test("Stage.facet produces { $facet: { name: [stages], ... } }") {
    val stage = Stage.facet(
      "byDept"  -> Pipeline(Stage.group(field[Employee](_.dept).ref)("count" -> Accumulator.count)),
      "top3"    -> Pipeline(Stage.sort(field[Employee](_.salary).desc), Stage.limit(3))
    ).asInstanceOf[BsonDocument]
    val spec = stage.getDocument("$facet")
    spec.containsKey("byDept") shouldBe true
    spec.containsKey("top3")   shouldBe true
    spec.getArray("top3").size() shouldBe 2
  }

  // ── Stage.$bucket ─────────────────────────────────────────────────────────

  test("Stage.bucket produces groupBy + boundaries spec") {
    val stage = Stage.bucket(
      field[Employee](_.salary).ref,
      Seq(Expr.literal(0), Expr.literal(50000), Expr.literal(100000), Expr.literal(200000)),
      default = Some(Expr.literal("other"))
    ).asInstanceOf[BsonDocument]
    val spec = stage.getDocument("$bucket")
    spec.containsKey("groupBy")    shouldBe true
    spec.getArray("boundaries").size() shouldBe 4
    spec.containsKey("default")   shouldBe true
  }

  test("Stage.bucketAuto produces groupBy + buckets spec") {
    val stage = Stage.bucketAuto(field[Employee](_.salary).ref, 5)
      .asInstanceOf[BsonDocument]
    val spec = stage.getDocument("$bucketAuto")
    spec.getInt32("buckets").getValue shouldBe 5
  }

  // ── Stage.$graphLookup ────────────────────────────────────────────────────

  test("Stage.graphLookup produces required fields") {
    val stage = Stage.graphLookup(
      from = "employees",
      startWith = Expr.fieldRef("managerId"),
      connectFromField = "managerId",
      connectToField = "_id",
      as = "reportingChain",
      maxDepth = Some(5),
      depthField = Some("depth")
    ).asInstanceOf[BsonDocument]
    val spec = stage.getDocument("$graphLookup")
    spec.getString("from").getValue             shouldBe "employees"
    spec.getString("connectFromField").getValue shouldBe "managerId"
    spec.getString("as").getValue               shouldBe "reportingChain"
    spec.getInt32("maxDepth").getValue          shouldBe 5
    spec.getString("depthField").getValue       shouldBe "depth"
  }

  // ── Pipeline ──────────────────────────────────────────────────────────────

  test("Pipeline collects stages in order") {
    val p = Pipeline(
      Stage.`match`(field[Employee](_.active) === true),
      Stage.limit(10)
    )
    p.stages.size shouldBe 2
  }

  test("Pipeline.:+ appends a stage") {
    val p = Pipeline(Stage.limit(5)) :+ Stage.skip(1)
    p.stages.size shouldBe 2
  }

  test("Pipeline.++ concatenates two pipelines") {
    val a = Pipeline(Stage.limit(5))
    val b = Pipeline(Stage.skip(2))
    (a ++ b).stages.size shouldBe 2
  }

  test("Pipeline.empty has no stages") {
    Pipeline.empty.stages shouldBe empty
  }

  test("Pipeline.toBsonArray produces a BsonArray of stage documents") {
    val p = Pipeline(
      Stage.`match`(field[Employee](_.active) === true),
      Stage.count("n")
    )
    val arr = p.toBsonArray
    arr.size() shouldBe 2
  }

  // ── End-to-end: composite pipelines ───────────────────────────────────────

  test("realistic salary-by-dept pipeline compiles and structures correctly") {
    val pipeline = Pipeline(
      Stage.`match`(Filter.and(
        field[Employee](_.active) === true,
        field[Employee](_.salary) > 0
      )),
      Stage.group(field[Employee](_.dept).ref)(
        "headcount" -> Accumulator.count,
        "avgSalary" -> Accumulator.avg(field[Employee](_.salary).ref),
        "names"     -> Accumulator.push(field[Employee](_.name).ref)
      ),
      Stage.sort(Sort.descending("avgSalary")),
      Stage.project(
        Stage.computed("dept",      Expr.fieldRef("_id")),
        Stage.computed("headcount", Expr.fieldRef("headcount")),
        Stage.computed("avgSalary", Expr.round(Expr.fieldRef("avgSalary"), 0)),
        Projection.excludeId
      ),
      Stage.limit(10)
    )
    pipeline.stages.size shouldBe 5
    pipeline.toBsonArray.size() shouldBe 5
  }

  test("addFields pipeline with conditional expression") {
    val pipeline = Pipeline(
      Stage.addFields(
        "level" -> Expr.switch(
          Expr.gte(field[Employee](_.salary).ref, Expr.literal(100_000)) -> Expr.literal("senior"),
          Expr.gte(field[Employee](_.salary).ref, Expr.literal(60_000))  -> Expr.literal("mid")
        )(Expr.literal("junior")),
        "monthly" -> Expr.divide(field[Employee](_.salary).ref, Expr.literal(12))
      )
    )
    pipeline.stages.size shouldBe 1
    pipeline.toBsonArray.get(0).asDocument().containsKey("$addFields") shouldBe true
  }

end AggregationSpec
