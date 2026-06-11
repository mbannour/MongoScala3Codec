package io.github.mbannour.mongo.dsl

import org.bson.types.ObjectId
import org.bson.{BsonArray, BsonBoolean, BsonDouble, BsonInt32, BsonInt64, BsonNull, BsonObjectId, BsonString, BsonValue}

import scala.jdk.CollectionConverters.*

/** Type class for converting Scala values to [[BsonValue]].
 *
 * Provides given instances for all common Scala primitives and collection types.
 * Extend by providing additional given instances for custom types.
 */
trait BsonEncoder[A]:
  def encode(value: A): BsonValue

object BsonEncoder:
  def apply[A](using enc: BsonEncoder[A]): BsonEncoder[A] = enc

  given BsonEncoder[String]  with { def encode(v: String)  = new BsonString(v) }
  given BsonEncoder[Boolean] with { def encode(v: Boolean) = new BsonBoolean(v) }
  given BsonEncoder[Int]     with { def encode(v: Int)     = new BsonInt32(v) }
  given BsonEncoder[Long]    with { def encode(v: Long)    = new BsonInt64(v) }
  given BsonEncoder[Double]  with { def encode(v: Double)  = new BsonDouble(v) }
  given BsonEncoder[Float]   with { def encode(v: Float)   = new BsonDouble(v.toDouble) }
  given BsonEncoder[Byte]    with { def encode(v: Byte)    = new BsonInt32(v.toInt) }
  given BsonEncoder[Short]   with { def encode(v: Short)   = new BsonInt32(v.toInt) }
  given BsonEncoder[ObjectId] with { def encode(v: ObjectId) = new BsonObjectId(v) }
  given BsonEncoder[BsonValue] with { def encode(v: BsonValue) = v }

  given [A](using enc: BsonEncoder[A]): BsonEncoder[Option[A]] with
    def encode(value: Option[A]): BsonValue = value match
      case Some(v) => enc.encode(v)
      case None    => new BsonNull()

  given [A](using enc: BsonEncoder[A]): BsonEncoder[List[A]] with
    def encode(value: List[A]): BsonValue =
      new BsonArray(value.map(enc.encode).asJava)

  given [A](using enc: BsonEncoder[A]): BsonEncoder[Seq[A]] with
    def encode(value: Seq[A]): BsonValue =
      new BsonArray(value.map(enc.encode).toList.asJava)

  given [A](using enc: BsonEncoder[A]): BsonEncoder[Set[A]] with
    def encode(value: Set[A]): BsonValue =
      new BsonArray(value.map(enc.encode).toList.asJava)
