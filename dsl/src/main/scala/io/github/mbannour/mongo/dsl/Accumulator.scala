package io.github.mbannour.mongo.dsl

import org.bson.{BsonDocument, BsonInt32, BsonValue}

/** Accumulator expressions for use inside [[Stage.group]].
 *
 * Each method returns a `BsonValue` (always a `BsonDocument`) that should be passed as the
 * second element of a `(fieldName, accumulator)` tuple:
 *
 * {{{
 * Stage.group(field[Employee](_.dept).ref)(
 *   "headcount" -> Accumulator.count,
 *   "avgSalary" -> Accumulator.avg(field[Employee](_.salary).ref),
 *   "maxSalary" -> Accumulator.max(field[Employee](_.salary).ref),
 *   "names"     -> Accumulator.push(field[Employee](_.name).ref)
 * )
 * }}}
 */
object Accumulator:

  /** Sum of `expr` across all documents in the group.
   *
   * Pass `Expr.literal(1)` or `Accumulator.sum(1)` to count documents.
   */
  def sum(expr: BsonValue): BsonDocument = new BsonDocument("$sum", expr)

  /** Shorthand for `$sum: n` — commonly used to count with a constant. */
  def sum(n: Int): BsonDocument = new BsonDocument("$sum", new BsonInt32(n))

  /** Average of `expr` across all documents in the group. */
  def avg(expr: BsonValue): BsonDocument = new BsonDocument("$avg", expr)

  /** Minimum value of `expr` in the group. */
  def min(expr: BsonValue): BsonDocument = new BsonDocument("$min", expr)

  /** Maximum value of `expr` in the group. */
  def max(expr: BsonValue): BsonDocument = new BsonDocument("$max", expr)

  /** Value of `expr` from the first document in the group (document order matters). */
  def first(expr: BsonValue): BsonDocument = new BsonDocument("$first", expr)

  /** Value of `expr` from the last document in the group (document order matters). */
  def last(expr: BsonValue): BsonDocument = new BsonDocument("$last", expr)

  /** Build an array of `expr` values from all documents in the group (may contain duplicates). */
  def push(expr: BsonValue): BsonDocument = new BsonDocument("$push", expr)

  /** Build a set of unique `expr` values from all documents in the group. */
  def addToSet(expr: BsonValue): BsonDocument = new BsonDocument("$addToSet", expr)

  /** Count the number of documents in the group (MongoDB 5.0+).
   *
   * For earlier versions, use `Accumulator.sum(1)` instead.
   */
  val count: BsonDocument = new BsonDocument("$count", new BsonDocument())

  /** Population standard deviation of `expr` values in the group. */
  def stdDevPop(expr: BsonValue): BsonDocument = new BsonDocument("$stdDevPop", expr)

  /** Sample standard deviation of `expr` values in the group. */
  def stdDevSamp(expr: BsonValue): BsonDocument = new BsonDocument("$stdDevSamp", expr)

  /** Merge all `expr` documents into a single document in the group. */
  def mergeObjects(expr: BsonValue): BsonDocument = new BsonDocument("$mergeObjects", expr)

  /** Accumulate a percentile of `expr` values (MongoDB 7.0+).
   *
   * @param input  numeric expression
   * @param p      percentile(s) to compute, each in [0, 1]
   * @param method `"approximate"` (only option in 7.0)
   */
  def percentile(input: BsonValue, p: Seq[Double], method: String = "approximate"): BsonDocument =
    import org.bson.{BsonArray, BsonDouble}
    import scala.jdk.CollectionConverters.*
    new BsonDocument("$percentile", new BsonDocument()
      .append("input", input)
      .append("p", new BsonArray(p.map(new BsonDouble(_)).toList.asJava))
      .append("method", new BsonDocument("$literal", new org.bson.BsonString(method)))
    )
