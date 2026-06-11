package io.github.mbannour.mongo.dsl

import org.bson.{BsonArray, BsonDocument}
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters.*

/** An ordered sequence of aggregation [[Stage]] documents.
 *
 * Pass `pipeline.stages` to the MongoDB driver's `collection.aggregate(...)`:
 * {{{
 * val pipeline = Pipeline(
 *   Stage.`match`(field[Employee](_.active) === true),
 *   Stage.group(field[Employee](_.dept).ref)(
 *     "count" -> Accumulator.count,
 *     "avg"   -> Accumulator.avg(field[Employee](_.salary).ref)
 *   ),
 *   Stage.sort(Sort.descending("count")),
 *   Stage.limit(10)
 * )
 *
 * collection.aggregate(pipeline.stages).toFuture()
 * }}}
 */
final class Pipeline(val stages: Seq[Bson]):

  def :+(stage: Bson): Pipeline       = new Pipeline(stages :+ stage)
  def ++(other: Pipeline): Pipeline   = new Pipeline(stages ++ other.stages)

  def toBsonArray: BsonArray =
    new BsonArray(stages.map {
      case doc: BsonDocument => doc
      case other             => other.toBsonDocument()
    }.toList.asJava)

  override def toString: String = s"Pipeline(${stages.mkString(", ")})"

object Pipeline:
  def apply(stages: Bson*): Pipeline = new Pipeline(stages.toSeq)
  val empty: Pipeline                = new Pipeline(Seq.empty)
