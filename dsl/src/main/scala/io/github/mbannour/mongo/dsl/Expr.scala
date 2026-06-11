package io.github.mbannour.mongo.dsl

import org.bson.{BsonArray, BsonDocument, BsonInt32, BsonString, BsonValue}

import scala.jdk.CollectionConverters.*

/** Aggregation expression builders.
 *
 * All methods return `BsonDocument` (a subtype of `BsonValue`), so they compose freely:
 * {{{
 * import io.github.mbannour.mongo.dsl.*
 *
 * case class Employee(name: String, salary: Int, dept: String)
 *
 * // "$salary" field reference
 * val salRef = field[Employee](_.salary).ref
 *
 * // Computed expressions
 * val monthly   = Expr.divide(salRef, Expr.literal(12))
 * val withBonus = Expr.multiply(salRef, Expr.literal(1.1))
 * val label     = Expr.cond(
 *   Expr.gte(salRef, Expr.literal(100_000)),
 *   Expr.literal("senior"),
 *   Expr.literal("junior")
 * )
 * }}}
 */
object Expr:

  // ── Field reference & literal ─────────────────────────────────────────────

  /** A field reference expression: prepends `$` if not already present.
   *
   * Prefer `field[T](_.path).ref` for compile-time path resolution.
   */
  def fieldRef(path: String): BsonString =
    new BsonString(if path.startsWith("$") then path else "$" + path)

  /** Wrap `value` in `{ $literal: value }` to prevent interpretation as an operator. */
  def literal[A](value: A)(using enc: BsonEncoder[A]): BsonDocument =
    new BsonDocument("$literal", enc.encode(value))

  // ── Arithmetic ────────────────────────────────────────────────────────────

  def add(exprs: BsonValue*): BsonDocument =
    new BsonDocument("$add", new BsonArray(exprs.toList.asJava))

  def subtract(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$subtract", new BsonArray(List(a, b).asJava))

  def multiply(exprs: BsonValue*): BsonDocument =
    new BsonDocument("$multiply", new BsonArray(exprs.toList.asJava))

  def divide(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$divide", new BsonArray(List(a, b).asJava))

  def mod(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$mod", new BsonArray(List(a, b).asJava))

  def abs(expr: BsonValue): BsonDocument = new BsonDocument("$abs", expr)
  def ceil(expr: BsonValue): BsonDocument = new BsonDocument("$ceil", expr)
  def floor(expr: BsonValue): BsonDocument = new BsonDocument("$floor", expr)

  def round(expr: BsonValue, place: Int = 0): BsonDocument =
    new BsonDocument("$round", new BsonArray(List(expr, new BsonInt32(place)).asJava))

  def sqrt(expr: BsonValue): BsonDocument = new BsonDocument("$sqrt", expr)

  def pow(base: BsonValue, exp: BsonValue): BsonDocument =
    new BsonDocument("$pow", new BsonArray(List(base, exp).asJava))

  def log(number: BsonValue, base: BsonValue): BsonDocument =
    new BsonDocument("$log", new BsonArray(List(number, base).asJava))

  def log10(expr: BsonValue): BsonDocument = new BsonDocument("$log10", expr)
  def exp(expr: BsonValue): BsonDocument   = new BsonDocument("$exp", expr)
  def trunc(expr: BsonValue): BsonDocument = new BsonDocument("$trunc", expr)

  // ── Conditional ───────────────────────────────────────────────────────────

  /** `{ $cond: [ifExpr, thenExpr, elseExpr] }` — ternary conditional. */
  def cond(ifExpr: BsonValue, thenExpr: BsonValue, elseExpr: BsonValue): BsonDocument =
    new BsonDocument("$cond", new BsonArray(List(ifExpr, thenExpr, elseExpr).asJava))

  /** `{ $ifNull: [expr, replacement] }` — substitute `replacement` when `expr` is null/missing. */
  def ifNull(expr: BsonValue, replacement: BsonValue): BsonDocument =
    new BsonDocument("$ifNull", new BsonArray(List(expr, replacement).asJava))

  /** `{ $switch: { branches: [...], default: defaultExpr } }`.
   *
   * {{{
   * Expr.switch(
   *   Expr.gte(salRef, Expr.literal(100_000)) -> Expr.literal("senior"),
   *   Expr.gte(salRef, Expr.literal(50_000))  -> Expr.literal("mid")
   * )(Expr.literal("junior"))
   * }}}
   */
  def switch(branches: (BsonValue, BsonValue)*)(default: BsonValue): BsonDocument =
    val branchArray = new BsonArray(branches.map { (cond, then_) =>
      new BsonDocument("case", cond).append("then", then_)
    }.toList.asJava)
    new BsonDocument("$switch",
      new BsonDocument("branches", branchArray).append("default", default)
    )

  // ── Logical (expression form) ─────────────────────────────────────────────

  def and(exprs: BsonValue*): BsonDocument =
    new BsonDocument("$and", new BsonArray(exprs.toList.asJava))

  def or(exprs: BsonValue*): BsonDocument =
    new BsonDocument("$or", new BsonArray(exprs.toList.asJava))

  /** `{ $not: [expr] }` — logical NOT (expression form, not query filter). */
  def not(expr: BsonValue): BsonDocument =
    new BsonDocument("$not", new BsonArray(List(expr).asJava))

  // ── Comparison (expression form, return boolean) ──────────────────────────

  def eq(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$eq", new BsonArray(List(a, b).asJava))

  def ne(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$ne", new BsonArray(List(a, b).asJava))

  def gt(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$gt", new BsonArray(List(a, b).asJava))

  def gte(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$gte", new BsonArray(List(a, b).asJava))

  def lt(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$lt", new BsonArray(List(a, b).asJava))

  def lte(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$lte", new BsonArray(List(a, b).asJava))

  def cmp(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$cmp", new BsonArray(List(a, b).asJava))

  // ── String ────────────────────────────────────────────────────────────────

  def concat(strs: BsonValue*): BsonDocument =
    new BsonDocument("$concat", new BsonArray(strs.toList.asJava))

  def toUpper(str: BsonValue): BsonDocument = new BsonDocument("$toUpper", str)
  def toLower(str: BsonValue): BsonDocument = new BsonDocument("$toLower", str)

  def trim(input: BsonValue): BsonDocument =
    new BsonDocument("$trim", new BsonDocument("input", input))

  def ltrim(input: BsonValue): BsonDocument =
    new BsonDocument("$ltrim", new BsonDocument("input", input))

  def rtrim(input: BsonValue): BsonDocument =
    new BsonDocument("$rtrim", new BsonDocument("input", input))

  def substrCP(str: BsonValue, start: BsonValue, length: BsonValue): BsonDocument =
    new BsonDocument("$substrCP", new BsonArray(List(str, start, length).asJava))

  def strLenCP(str: BsonValue): BsonDocument = new BsonDocument("$strLenCP", str)
  def strLenBytes(str: BsonValue): BsonDocument = new BsonDocument("$strLenBytes", str)

  def split(str: BsonValue, delimiter: BsonValue): BsonDocument =
    new BsonDocument("$split", new BsonArray(List(str, delimiter).asJava))

  def indexOfCP(str: BsonValue, substr: BsonValue): BsonDocument =
    new BsonDocument("$indexOfCP", new BsonArray(List(str, substr).asJava))

  def regexMatch(input: BsonValue, regex: String, options: String = ""): BsonDocument =
    val spec = new BsonDocument("input", input).append("regex", new BsonString(regex))
    if options.nonEmpty then spec.append("options", new BsonString(options))
    new BsonDocument("$regexMatch", spec)

  // ── Type conversion ───────────────────────────────────────────────────────

  def toString(expr: BsonValue): BsonDocument  = new BsonDocument("$toString", expr)
  def toInt(expr: BsonValue): BsonDocument     = new BsonDocument("$toInt", expr)
  def toLong(expr: BsonValue): BsonDocument    = new BsonDocument("$toLong", expr)
  def toDouble(expr: BsonValue): BsonDocument  = new BsonDocument("$toDouble", expr)
  def toDecimal(expr: BsonValue): BsonDocument = new BsonDocument("$toDecimal", expr)
  def toDate(expr: BsonValue): BsonDocument    = new BsonDocument("$toDate", expr)
  def toObjectId(expr: BsonValue): BsonDocument = new BsonDocument("$toObjectId", expr)
  def toBool(expr: BsonValue): BsonDocument    = new BsonDocument("$toBool", expr)
  def typeOf(expr: BsonValue): BsonDocument    = new BsonDocument("$type", expr)

  /** General type conversion with optional `onError` and `onNull` fallbacks. */
  def convert(input: BsonValue, to: String, onError: Option[BsonValue] = None, onNull: Option[BsonValue] = None): BsonDocument =
    val spec = new BsonDocument("input", input).append("to", new BsonString(to))
    onError.foreach(e => spec.append("onError", e))
    onNull.foreach(n => spec.append("onNull", n))
    new BsonDocument("$convert", spec)

  // ── Date ──────────────────────────────────────────────────────────────────

  def year(date: BsonValue): BsonDocument        = new BsonDocument("$year", date)
  def month(date: BsonValue): BsonDocument       = new BsonDocument("$month", date)
  def dayOfMonth(date: BsonValue): BsonDocument  = new BsonDocument("$dayOfMonth", date)
  def dayOfWeek(date: BsonValue): BsonDocument   = new BsonDocument("$dayOfWeek", date)
  def dayOfYear(date: BsonValue): BsonDocument   = new BsonDocument("$dayOfYear", date)
  def hour(date: BsonValue): BsonDocument        = new BsonDocument("$hour", date)
  def minute(date: BsonValue): BsonDocument      = new BsonDocument("$minute", date)
  def second(date: BsonValue): BsonDocument      = new BsonDocument("$second", date)
  def millisecond(date: BsonValue): BsonDocument = new BsonDocument("$millisecond", date)
  def week(date: BsonValue): BsonDocument        = new BsonDocument("$week", date)
  def isoWeek(date: BsonValue): BsonDocument     = new BsonDocument("$isoWeek", date)
  def isoDayOfWeek(date: BsonValue): BsonDocument = new BsonDocument("$isoDayOfWeek", date)

  def dateToString(format: String, date: BsonValue, timezone: Option[String] = None): BsonDocument =
    val spec = new BsonDocument("format", new BsonString(format)).append("date", date)
    timezone.foreach(tz => spec.append("timezone", new BsonString(tz)))
    new BsonDocument("$dateToString", spec)

  def dateFromString(dateString: BsonValue, format: Option[String] = None, timezone: Option[String] = None): BsonDocument =
    val spec = new BsonDocument("dateString", dateString)
    format.foreach(f => spec.append("format", new BsonString(f)))
    timezone.foreach(tz => spec.append("timezone", new BsonString(tz)))
    new BsonDocument("$dateFromString", spec)

  def dateAdd(startDate: BsonValue, unit: String, amount: BsonValue, timezone: Option[String] = None): BsonDocument =
    val spec = new BsonDocument("startDate", startDate)
      .append("unit", new BsonString(unit))
      .append("amount", amount)
    timezone.foreach(tz => spec.append("timezone", new BsonString(tz)))
    new BsonDocument("$dateAdd", spec)

  def dateDiff(startDate: BsonValue, endDate: BsonValue, unit: String, timezone: Option[String] = None): BsonDocument =
    val spec = new BsonDocument("startDate", startDate)
      .append("endDate", endDate)
      .append("unit", new BsonString(unit))
    timezone.foreach(tz => spec.append("timezone", new BsonString(tz)))
    new BsonDocument("$dateDiff", spec)

  // ── Array ─────────────────────────────────────────────────────────────────

  def size(expr: BsonValue): BsonDocument = new BsonDocument("$size", expr)

  def arrayElemAt(array: BsonValue, index: BsonValue): BsonDocument =
    new BsonDocument("$arrayElemAt", new BsonArray(List(array, index).asJava))

  def arrayElemAt(array: BsonValue, index: Int): BsonDocument =
    arrayElemAt(array, new BsonInt32(index))

  def first(array: BsonValue): BsonDocument = new BsonDocument("$first", array)
  def last(array: BsonValue): BsonDocument  = new BsonDocument("$last", array)

  def reverseArray(expr: BsonValue): BsonDocument = new BsonDocument("$reverseArray", expr)

  def concatArrays(arrays: BsonValue*): BsonDocument =
    new BsonDocument("$concatArrays", new BsonArray(arrays.toList.asJava))

  def slice(array: BsonValue, n: Int): BsonDocument =
    new BsonDocument("$slice", new BsonArray(List(array, new BsonInt32(n)).asJava))

  def slice(array: BsonValue, position: Int, n: Int): BsonDocument =
    new BsonDocument("$slice", new BsonArray(List(array, new BsonInt32(position), new BsonInt32(n)).asJava))

  def indexOfArray(array: BsonValue, search: BsonValue): BsonDocument =
    new BsonDocument("$indexOfArray", new BsonArray(List(array, search).asJava))

  def in(expr: BsonValue, array: BsonValue): BsonDocument =
    new BsonDocument("$in", new BsonArray(List(expr, array).asJava))

  def range(start: BsonValue, end: BsonValue): BsonDocument =
    new BsonDocument("$range", new BsonArray(List(start, end).asJava))

  def range(start: BsonValue, end: BsonValue, step: BsonValue): BsonDocument =
    new BsonDocument("$range", new BsonArray(List(start, end, step).asJava))

  def filter(input: BsonValue, as: String, cond: BsonValue): BsonDocument =
    new BsonDocument("$filter", new BsonDocument()
      .append("input", input)
      .append("as", new BsonString(as))
      .append("cond", cond)
    )

  def map(input: BsonValue, as: String, in: BsonValue): BsonDocument =
    new BsonDocument("$map", new BsonDocument()
      .append("input", input)
      .append("as", new BsonString(as))
      .append("in", in)
    )

  def reduce(input: BsonValue, initialValue: BsonValue, in: BsonValue): BsonDocument =
    new BsonDocument("$reduce", new BsonDocument()
      .append("input", input)
      .append("initialValue", initialValue)
      .append("in", in)
    )

  def zip(inputs: BsonValue*): BsonDocument =
    new BsonDocument("$zip", new BsonDocument("inputs", new BsonArray(inputs.toList.asJava)))

  def sortArray(input: BsonValue, sortBy: BsonValue): BsonDocument =
    new BsonDocument("$sortArray", new BsonDocument("input", input).append("sortBy", sortBy))

  // ── Object ────────────────────────────────────────────────────────────────

  def mergeObjects(objects: BsonValue*): BsonDocument =
    new BsonDocument("$mergeObjects", new BsonArray(objects.toList.asJava))

  def objectToArray(doc: BsonValue): BsonDocument = new BsonDocument("$objectToArray", doc)
  def arrayToObject(pairs: BsonValue): BsonDocument = new BsonDocument("$arrayToObject", pairs)

  // ── Set ───────────────────────────────────────────────────────────────────

  def setUnion(sets: BsonValue*): BsonDocument =
    new BsonDocument("$setUnion", new BsonArray(sets.toList.asJava))

  def setIntersection(sets: BsonValue*): BsonDocument =
    new BsonDocument("$setIntersection", new BsonArray(sets.toList.asJava))

  def setDifference(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$setDifference", new BsonArray(List(a, b).asJava))

  def setEquals(sets: BsonValue*): BsonDocument =
    new BsonDocument("$setEquals", new BsonArray(sets.toList.asJava))

  def setIsSubset(a: BsonValue, b: BsonValue): BsonDocument =
    new BsonDocument("$setIsSubset", new BsonArray(List(a, b).asJava))

  def anyElementTrue(array: BsonValue): BsonDocument = new BsonDocument("$anyElementTrue", array)
  def allElementsTrue(array: BsonValue): BsonDocument = new BsonDocument("$allElementsTrue", array)
