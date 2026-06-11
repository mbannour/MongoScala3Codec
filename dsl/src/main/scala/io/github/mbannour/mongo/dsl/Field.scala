package io.github.mbannour.mongo.dsl

import org.bson.conversions.Bson
import org.bson.{BsonArray, BsonBoolean, BsonDocument, BsonInt32, BsonInt64, BsonNull, BsonString, BsonValue}

import scala.jdk.CollectionConverters.*

/** A compile-time resolved reference to a MongoDB document field with its value type [[A]].
 *
 * Instances are created via the [[field]] inline method:
 * {{{
 * case class User(name: String, age: Int, active: Boolean, scores: List[Int])
 *
 * val f = field[User](_.name)   // Field[User, String]
 *
 * // Filter
 * f === "John"
 * f =/= "Jane"
 *
 * // Update
 * f := "Alice"
 * field[User](_.age).inc(1)
 *
 * // Sort & Projection
 * field[User](_.age).desc
 * field[User](_.name).include
 * }}}
 */
final class Field[Doc, A](val path: String):

  // ── Filter operators ──────────────────────────────────────────────────────

  def ===(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument(path, new BsonDocument("$eq", enc.encode(value)))

  def =/=(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument(path, new BsonDocument("$ne", enc.encode(value)))

  def >(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument(path, new BsonDocument("$gt", enc.encode(value)))

  def >=(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument(path, new BsonDocument("$gte", enc.encode(value)))

  def <(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument(path, new BsonDocument("$lt", enc.encode(value)))

  def <=(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument(path, new BsonDocument("$lte", enc.encode(value)))

  def in(values: A*)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument(path, new BsonDocument("$in", new BsonArray(values.map(enc.encode).toList.asJava)))

  def nin(values: A*)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument(path, new BsonDocument("$nin", new BsonArray(values.map(enc.encode).toList.asJava)))

  def exists(mustExist: Boolean = true): Bson =
    new BsonDocument(path, new BsonDocument("$exists", new BsonBoolean(mustExist)))

  def regex(pattern: String): Bson =
    new BsonDocument(path, new BsonDocument("$regex", new BsonString(pattern)))

  def regex(pattern: String, options: String): Bson =
    new BsonDocument(
      path,
      new BsonDocument("$regex", new BsonString(pattern)).append("$options", new BsonString(options))
    )

  def isNull: Bson  = new BsonDocument(path, new BsonDocument("$eq", new BsonNull()))
  def notNull: Bson = new BsonDocument(path, new BsonDocument("$ne", new BsonNull()))

  def hasSize(n: Int): Bson =
    new BsonDocument(path, new BsonDocument("$size", new BsonInt32(n)))

  def elemMatch(filter: Bson): Bson =
    new BsonDocument(path, new BsonDocument("$elemMatch", Field.toDoc(filter)))

  /** Match documents where this array field contains all of the specified values (`$all`). */
  def all[E](values: E*)(using enc: BsonEncoder[E], ev: A <:< Iterable[E]): Bson =
    new BsonDocument(path, new BsonDocument("$all", new BsonArray(values.map(enc.encode).toList.asJava)))

  /** Match documents where this field is of the given BSON type name (`$type`).
   *
   * Common aliases: `"double"`, `"string"`, `"object"`, `"array"`, `"objectId"`,
   * `"bool"`, `"date"`, `"null"`, `"int"`, `"long"`, `"decimal"`.
   */
  def hasType(typeName: String): Bson =
    new BsonDocument(path, new BsonDocument("$type", new BsonString(typeName)))

  /** Match documents where this field is of the given BSON type number (`$type`). */
  def hasType(typeNumber: Int): Bson =
    new BsonDocument(path, new BsonDocument("$type", new BsonInt32(typeNumber)))

  /** Match documents where `this % divisor == remainder` (`$mod`). */
  def mod(divisor: Long, remainder: Long): Bson =
    new BsonDocument(
      path,
      new BsonDocument("$mod", new BsonArray(List(new BsonInt64(divisor), new BsonInt64(remainder)).asJava))
    )

  
  def :=(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument("$set", new BsonDocument(path, enc.encode(value)))

  def unset: Bson =
    new BsonDocument("$unset", new BsonDocument(path, new BsonString("")))

  def setOnInsert(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument("$setOnInsert", new BsonDocument(path, enc.encode(value)))

  def inc(amount: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument("$inc", new BsonDocument(path, enc.encode(amount)))

  def mul(factor: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument("$mul", new BsonDocument(path, enc.encode(factor)))

  def min(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument("$min", new BsonDocument(path, enc.encode(value)))

  def max(value: A)(using enc: BsonEncoder[A]): Bson =
    new BsonDocument("$max", new BsonDocument(path, enc.encode(value)))

  /** Push an element into an array field. [[A]] must be a collection type (List, Seq, etc.). */
  def push[E](value: E)(using enc: BsonEncoder[E], ev: A <:< Iterable[E]): Bson =
    new BsonDocument("$push", new BsonDocument(path, enc.encode(value)))

  /** Remove matching elements from an array field. [[A]] must be a collection type. */
  def pull[E](value: E)(using enc: BsonEncoder[E], ev: A <:< Iterable[E]): Bson =
    new BsonDocument("$pull", new BsonDocument(path, enc.encode(value)))

  /** Add an element to an array only if it is not already present. [[A]] must be a collection type. */
  def addToSet[E](value: E)(using enc: BsonEncoder[E], ev: A <:< Iterable[E]): Bson =
    new BsonDocument("$addToSet", new BsonDocument(path, enc.encode(value)))

  /** Remove all array elements that exactly match one of the listed values (`$pullAll`). */
  def pullAll[E](values: E*)(using enc: BsonEncoder[E], ev: A <:< Iterable[E]): Bson =
    new BsonDocument("$pullAll", new BsonDocument(path, new BsonArray(values.map(enc.encode).toList.asJava)))

  /** Push multiple elements at once, with optional `$position`, `$sort`, and `$slice` modifiers.
   *
   * @param values   elements to append
   * @param position insert at this index (0 = front, negative = relative to end)
   * @param sort     sort the array after pushing — pass a `BsonDocument` sort spec
   *                 (e.g. `field[T](_.score).desc.asInstanceOf[BsonDocument]`)
   *                 or `BsonInt32(1)` / `BsonInt32(-1)` for scalar arrays
   * @param slice    keep only the first `n` elements after pushing (negative = keep last `n`)
   */
  def pushEach[E](
    values: Seq[E],
    position: Option[Int] = None,
    sort: Option[BsonValue] = None,
    slice: Option[Int] = None
  )(using enc: BsonEncoder[E], ev: A <:< Iterable[E]): Bson =
    val modifiers = new BsonDocument("$each", new BsonArray(values.map(enc.encode).toList.asJava))
    position.foreach(p => modifiers.append("$position", new BsonInt32(p)))
    sort.foreach(s => modifiers.append("$sort", s))
    slice.foreach(s => modifiers.append("$slice", new BsonInt32(s)))
    new BsonDocument("$push", new BsonDocument(path, modifiers))

  /** Remove the last element from an array field (`$pop: 1`). */
  def pop: Bson =
    new BsonDocument("$pop", new BsonDocument(path, new BsonInt32(1)))

  /** Remove the first element from an array field (`$pop: -1`). */
  def popFirst: Bson =
    new BsonDocument("$pop", new BsonDocument(path, new BsonInt32(-1)))

  /** Rename this field to `newName` (`$rename`). */
  def rename(newName: String): Bson =
    new BsonDocument("$rename", new BsonDocument(path, new BsonString(newName)))

  /** Set this field to the current date as a BSON Date (`$currentDate`). */
  def currentDate: Bson =
    new BsonDocument("$currentDate", new BsonDocument(path, new BsonBoolean(true)))

  /** Set this field to the current date as a BSON Timestamp (`$currentDate` with `$type: "timestamp"`). */
  def currentTimestamp: Bson =
    new BsonDocument(
      "$currentDate",
      new BsonDocument(path, new BsonDocument("$type", new BsonString("timestamp")))
    )

  /** Bitwise AND on an integer field (`$bit`). */
  def bitAnd(value: Int): Bson =
    new BsonDocument("$bit", new BsonDocument(path, new BsonDocument("and", new BsonInt32(value))))

  def bitAnd(value: Long): Bson =
    new BsonDocument("$bit", new BsonDocument(path, new BsonDocument("and", new BsonInt64(value))))

  /** Bitwise OR on an integer field (`$bit`). */
  def bitOr(value: Int): Bson =
    new BsonDocument("$bit", new BsonDocument(path, new BsonDocument("or", new BsonInt32(value))))

  def bitOr(value: Long): Bson =
    new BsonDocument("$bit", new BsonDocument(path, new BsonDocument("or", new BsonInt64(value))))

  /** Bitwise XOR on an integer field (`$bit`). */
  def bitXor(value: Int): Bson =
    new BsonDocument("$bit", new BsonDocument(path, new BsonDocument("xor", new BsonInt32(value))))

  def bitXor(value: Long): Bson =
    new BsonDocument("$bit", new BsonDocument(path, new BsonDocument("xor", new BsonInt64(value))))

  // ── Sort operators ────────────────────────────────────────────────────────

  /** Aggregation expression reference for this field — `"$path"`.
   *
   * Use wherever a MongoDB aggregation expression expects a field reference:
   * {{{
   * Stage.group(field[Employee](_.dept).ref)(
   *   "total" -> Accumulator.sum(field[Employee](_.salary).ref)
   * )
   * }}}
   */
  def ref: BsonString = new BsonString("$" + path)

  def asc: Bson  = new BsonDocument(path, new BsonInt32(1))
  def desc: Bson = new BsonDocument(path, new BsonInt32(-1))

  // ── Projection operators ──────────────────────────────────────────────────

  def include: Bson = new BsonDocument(path, new BsonInt32(1))
  def exclude: Bson = new BsonDocument(path, new BsonInt32(0))

  override def toString: String = s"Field($path)"

object Field:
  private[dsl] def toDoc(bson: Bson): BsonDocument = bson match
    case doc: BsonDocument => doc
    case _ =>
      throw new IllegalArgumentException(
        s"Expected a BsonDocument from the DSL but got ${bson.getClass.getName}. " +
          "Pass filters produced by the `field` DSL."
      )
