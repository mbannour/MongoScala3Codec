package io.github.mbannour.mongo.dsl

import org.bson.{BsonArray, BsonBoolean, BsonDocument, BsonInt32, BsonInt64, BsonNull, BsonString, BsonValue}
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters.*

/** Aggregation pipeline stage builders.
 *
 * Each method returns a `Bson` stage document suitable for use in a [[Pipeline]]:
 *
 * {{{
 * import io.github.mbannour.mongo.dsl.*
 *
 * case class Employee(name: String, dept: String, salary: Int, active: Boolean)
 *
 * val pipeline = Pipeline(
 *   Stage.`match`(field[Employee](_.active) === true),
 *   Stage.group(field[Employee](_.dept).ref)(
 *     "headcount" -> Accumulator.count,
 *     "totalPay"  -> Accumulator.sum(field[Employee](_.salary).ref)
 *   ),
 *   Stage.sort(Sort.descending("totalPay")),
 *   Stage.limit(5)
 * )
 * }}}
 */
object Stage:

  // ── Simple stages ──────────────────────────────────────────────────────────

  /** Filter documents. Accepts any DSL filter produced by `field[T]` operators or `Filter.*`. */
  def `match`(filter: Bson): Bson =
    new BsonDocument("$match", Field.toDoc(filter))

  /** Sort documents. Accepts `.asc`/`.desc` or `Sort.combine(...)`. */
  def sort(sort: Bson): Bson =
    new BsonDocument("$sort", Field.toDoc(sort))

  def limit(n: Long): Bson =
    new BsonDocument("$limit", new BsonInt64(n))

  def skip(n: Long): Bson =
    new BsonDocument("$skip", new BsonInt64(n))

  /** Count documents into `fieldName`. Produces `{ $count: "fieldName" }`. */
  def count(fieldName: String): Bson =
    new BsonDocument("$count", new BsonString(fieldName))

  /** Randomly sample `size` documents. */
  def sample(size: Long): Bson =
    new BsonDocument("$sample", new BsonDocument("size", new BsonInt64(size)))

  /** Write pipeline output to `collectionName` (replaces existing content). */
  def out(collectionName: String): Bson =
    new BsonDocument("$out", new BsonString(collectionName))

  /** Write pipeline output to `collectionName` in another database. */
  def out(db: String, collectionName: String): Bson =
    new BsonDocument("$out",
      new BsonDocument("db", new BsonString(db)).append("coll", new BsonString(collectionName))
    )

  /** Merge pipeline output into a collection.
   *
   * @param into    target collection name
   * @param on      merge key field(s); defaults to `"_id"`
   * @param whenMatched   action when a match is found: `"replace"`, `"merge"`, `"keepExisting"`, `"fail"`
   * @param whenNotMatched action when no match: `"insert"`, `"discard"`, `"fail"`
   */
  def merge(
    into: String,
    on: String = "_id",
    whenMatched: String = "merge",
    whenNotMatched: String = "insert"
  ): Bson =
    new BsonDocument("$merge", new BsonDocument()
      .append("into", new BsonString(into))
      .append("on", new BsonString(on))
      .append("whenMatched", new BsonString(whenMatched))
      .append("whenNotMatched", new BsonString(whenNotMatched))
    )

  /** Group by the expression value and sort by count descending. */
  def sortByCount(expr: BsonValue): Bson =
    new BsonDocument("$sortByCount", expr)

  // ── $unwind ────────────────────────────────────────────────────────────────

  /** Deconstruct an array field into separate documents.
   *
   * @param path                    field path prefixed with `$` (e.g. `"$tags"` or `field[T](_.tags).ref.getValue`)
   * @param preserveNullAndEmpty    if `true`, output a document when the array is `null`, missing, or empty
   * @param includeArrayIndex       optional field name to receive the array index
   */
  def unwind(
    path: String,
    preserveNullAndEmpty: Boolean = false,
    includeArrayIndex: Option[String] = None
  ): Bson =
    if !preserveNullAndEmpty && includeArrayIndex.isEmpty then
      new BsonDocument("$unwind", new BsonString(path))
    else
      val spec = new BsonDocument("path", new BsonString(path))
      if preserveNullAndEmpty then spec.append("preserveNullAndEmptyArrays", new BsonBoolean(true))
      includeArrayIndex.foreach(idx => spec.append("includeArrayIndex", new BsonString(idx)))
      new BsonDocument("$unwind", spec)

  // ── $project ───────────────────────────────────────────────────────────────

  /** Reshape documents.
   *
   * Accepts [[Field]] inclusion/exclusion specs and [[computed]] expressions:
   * {{{
   * Stage.project(
   *   field[Employee](_.name).include,
   *   field[Employee](_.dept).include,
   *   Projection.excludeId,
   *   Stage.computed("monthlySalary", Expr.divide(field[Employee](_.salary).ref, Expr.literal(12)))
   * )
   * }}}
   */
  def project(specs: Bson*): Bson =
    val doc = new BsonDocument()
    specs.foreach(s => doc.putAll(Field.toDoc(s)))
    new BsonDocument("$project", doc)

  /** A single computed field spec for use inside [[project]] or [[addFields]]. */
  def computed(name: String, expr: BsonValue): Bson =
    new BsonDocument(name, expr)

  // ── $addFields / $set ──────────────────────────────────────────────────────

  /** Add or overwrite fields with computed expressions.
   *
   * {{{
   * Stage.addFields(
   *   "fullName"  -> Expr.concat(field[Employee](_.first).ref, Expr.literal(" "), field[Employee](_.last).ref),
   *   "isManager" -> Expr.gte(field[Employee](_.reports).ref, Expr.literal(1))
   * )
   * }}}
   */
  def addFields(fields: (String, BsonValue)*): Bson =
    val doc = new BsonDocument()
    fields.foreach((k, v) => doc.append(k, v))
    new BsonDocument("$addFields", doc)

  /** Alias for [[addFields]] (MongoDB 4.2+). */
  def set(fields: (String, BsonValue)*): Bson =
    val doc = new BsonDocument()
    fields.foreach((k, v) => doc.append(k, v))
    new BsonDocument("$set", doc)

  /** Remove fields from documents (`$unset`). */
  def unset(fields: String*): Bson =
    if fields.size == 1 then
      new BsonDocument("$unset", new BsonString(fields.head))
    else
      new BsonDocument("$unset", new BsonArray(fields.map(new BsonString(_)).toList.asJava))

  // ── $replaceRoot / $replaceWith ────────────────────────────────────────────

  /** Replace the root document with `newRoot`.
   *
   * {{{
   * Stage.replaceRoot(Expr.fieldRef("$address"))
   * Stage.replaceRoot(Expr.mergeObjects(Expr.fieldRef("$$ROOT"), Expr.fieldRef("$extra")))
   * }}}
   */
  def replaceRoot(newRoot: BsonValue): Bson =
    new BsonDocument("$replaceRoot", new BsonDocument("newRoot", newRoot))

  /** Alias for [[replaceRoot]] with a simpler syntax (MongoDB 4.2+). */
  def replaceWith(newRoot: BsonValue): Bson =
    new BsonDocument("$replaceWith", newRoot)

  // ── $group ─────────────────────────────────────────────────────────────────

  /** Group documents by `id` and compute accumulator expressions.
   *
   * Use `field[T](_.path).ref` for the id expression, or any `BsonValue` expression.
   *
   * {{{
   * Stage.group(field[Employee](_.dept).ref)(
   *   "headcount" -> Accumulator.count,
   *   "avgSalary" -> Accumulator.avg(field[Employee](_.salary).ref),
   *   "names"     -> Accumulator.push(field[Employee](_.name).ref)
   * )
   * }}}
   */
  def group(id: BsonValue)(accumulators: (String, BsonValue)*): Bson =
    val doc = new BsonDocument("_id", id)
    accumulators.foreach((k, v) => doc.append(k, v))
    new BsonDocument("$group", doc)

  /** Group all documents into a single result (no grouping key). */
  def groupAll(accumulators: (String, BsonValue)*): Bson =
    group(new BsonNull())(accumulators*)

  // ── $lookup ────────────────────────────────────────────────────────────────

  /** Simple equality join with another collection.
   *
   * @param from         the collection to join
   * @param localField   field from the input documents
   * @param foreignField field from the `from` collection
   * @param as           output array field name
   */
  def lookup(from: String, localField: String, foreignField: String, as: String): Bson =
    new BsonDocument("$lookup", new BsonDocument()
      .append("from", new BsonString(from))
      .append("localField", new BsonString(localField))
      .append("foreignField", new BsonString(foreignField))
      .append("as", new BsonString(as))
    )

  /** Pipeline-style join — lets you filter and transform the joined documents.
   *
   * @param from     the collection to join
   * @param let      variables mapping to expressions in the joined pipeline (field name → expression)
   * @param pipeline sub-pipeline to run on the joined collection
   * @param as       output array field name
   */
  def lookupPipeline(
    from: String,
    let: Seq[(String, BsonValue)] = Seq.empty,
    pipeline: Pipeline,
    as: String
  ): Bson =
    val spec = new BsonDocument()
      .append("from", new BsonString(from))
      .append("pipeline", pipeline.toBsonArray)
      .append("as", new BsonString(as))
    if let.nonEmpty then
      val letDoc = new BsonDocument()
      let.foreach((k, v) => letDoc.append(k, v))
      spec.append("let", letDoc)
    new BsonDocument("$lookup", spec)

  // ── $facet ─────────────────────────────────────────────────────────────────

  /** Run multiple sub-pipelines on the same input, each producing a separate output field.
   *
   * {{{
   * Stage.facet(
   *   "byDept"  -> Pipeline(Stage.group(field[Employee](_.dept).ref)("count" -> Accumulator.count)),
   *   "top10"   -> Pipeline(Stage.sort(field[Employee](_.salary).desc), Stage.limit(10))
   * )
   * }}}
   */
  def facet(branches: (String, Pipeline)*): Bson =
    val doc = new BsonDocument()
    branches.foreach((name, pipe) => doc.append(name, pipe.toBsonArray))
    new BsonDocument("$facet", doc)

  // ── $bucket / $bucketAuto ──────────────────────────────────────────────────

  /** Categorise documents into manually-defined buckets.
   *
   * @param groupBy     expression to group by
   * @param boundaries  ascending boundary values (N boundaries produce N-1 buckets)
   * @param default     label for documents that fall outside all boundaries
   * @param output      additional accumulator fields (same as [[group]])
   */
  def bucket(
    groupBy: BsonValue,
    boundaries: Seq[BsonValue],
    default: Option[BsonValue] = None,
    output: Seq[(String, BsonValue)] = Seq.empty
  ): Bson =
    val spec = new BsonDocument()
      .append("groupBy", groupBy)
      .append("boundaries", new BsonArray(boundaries.toList.asJava))
    default.foreach(d => spec.append("default", d))
    if output.nonEmpty then
      val outDoc = new BsonDocument()
      output.foreach((k, v) => outDoc.append(k, v))
      spec.append("output", outDoc)
    new BsonDocument("$bucket", spec)

  /** Automatically categorise documents into `numBuckets` equally-populated buckets. */
  def bucketAuto(
    groupBy: BsonValue,
    numBuckets: Int,
    output: Seq[(String, BsonValue)] = Seq.empty
  ): Bson =
    val spec = new BsonDocument()
      .append("groupBy", groupBy)
      .append("buckets", new BsonInt32(numBuckets))
    if output.nonEmpty then
      val outDoc = new BsonDocument()
      output.foreach((k, v) => outDoc.append(k, v))
      spec.append("output", outDoc)
    new BsonDocument("$bucketAuto", spec)

  // ── $graphLookup ───────────────────────────────────────────────────────────

  /** Recursive graph traversal lookup.
   *
   * @param from              collection to search
   * @param startWith         expression for initial values
   * @param connectFromField  field in the `from` collection to follow
   * @param connectToField    field in the `from` collection to match against
   * @param as                output array field name
   * @param maxDepth          optional maximum recursion depth
   * @param depthField        optional field name to store the depth of each result
   */
  def graphLookup(
    from: String,
    startWith: BsonValue,
    connectFromField: String,
    connectToField: String,
    as: String,
    maxDepth: Option[Int] = None,
    depthField: Option[String] = None
  ): Bson =
    val spec = new BsonDocument()
      .append("from", new BsonString(from))
      .append("startWith", startWith)
      .append("connectFromField", new BsonString(connectFromField))
      .append("connectToField", new BsonString(connectToField))
      .append("as", new BsonString(as))
    maxDepth.foreach(d => spec.append("maxDepth", new BsonInt32(d)))
    depthField.foreach(f => spec.append("depthField", new BsonString(f)))
    new BsonDocument("$graphLookup", spec)
