package io.github.mbannour.mongo.repository

import io.github.mbannour.mongo.dsl.{BsonEncoder, Field, Filter}
import org.bson.conversions.Bson
import org.mongodb.scala.{FindObservable, MongoCollection}
import org.mongodb.scala.model.{FindOneAndUpdateOptions, ReturnDocument, UpdateOptions}

import scala.reflect.ClassTag

/** A type-safe repository over a [[MongoCollection]] of `Doc` documents keyed by `Id`.
 *
 * Every query/update argument is a plain `Bson`, so the type-safe
 * [[io.github.mbannour.mongo.dsl]] plugs in directly — no string field names anywhere:
 *
 * {{{
 * import io.github.mbannour.mongo.dsl.*
 * import io.github.mbannour.mongo.repository.*
 * import scala.concurrent.ExecutionContext.Implicits.global
 *
 * val repo = MongoRepository[Future, Employee, ObjectId](collection, field[Employee](_._id))
 *
 * repo.find(
 *   filter = Filter.and(
 *     field[Employee](_.department) === "Engineering",
 *     field[Employee](_.salary) > 80_000.0
 *   ),
 *   sort  = Some(field[Employee](_.salary).desc),
 *   limit = 10
 * )
 *
 * repo.updateById(id, Update.combine(field[Employee](_.salary).mul(1.05)))
 * }}}
 *
 * The effect type `F` is abstract: supply a [[MongoAsync]] instance. One for `Future` ships in this
 * module; ZIO / cats-effect instances live in thin integration modules.
 *
 * @param collection the underlying typed collection (its registry must contain a `Doc` codec)
 * @param idField    the document's identity field, typically `field[Doc](_._id)`
 */
final class MongoRepository[F[_], Doc, Id](
    val collection: MongoCollection[Doc],
    val idField: Field[Doc, Id]
)(using F: MongoAsync[F], idEncoder: BsonEncoder[Id], ct: ClassTag[Doc]):

  /** Filter selecting the single document whose id equals `id`. */
  def byId(id: Id): Bson = idField === id

  // ── Create ──────────────────────────────────────────────────────────────────

  /** Insert a single document. */
  def insert(doc: Doc): F[Unit] =
    F.map(F.single(collection.insertOne(doc)))(_ => ())

  /** Insert many documents. A no-op (no driver call) when `docs` is empty. */
  def insertMany(docs: Seq[Doc]): F[Unit] =
    if docs.isEmpty then F.pure(())
    else F.map(F.single(collection.insertMany(docs)))(_ => ())

  // ── Read ────────────────────────────────────────────────────────────────────

  /** Fetch the document with the given id, if present. */
  def findById(id: Id): F[Option[Doc]] = findOne(byId(id))

  /** Fetch the first document matching `filter`, if any. */
  def findOne(filter: Bson): F[Option[Doc]] =
    F.optional(collection.find(filter))

  /** Fetch documents matching `filter`, with optional sort, projection, skip and limit. */
  def find(
      filter: Bson = Filter.empty,
      sort: Option[Bson] = None,
      projection: Option[Bson] = None,
      skip: Int = 0,
      limit: Int = 0
  ): F[Seq[Doc]] =
    var obs: FindObservable[Doc] = collection.find(filter)
    sort.foreach(s => obs = obs.sort(s))
    projection.foreach(p => obs = obs.projection(p))
    if skip > 0 then obs = obs.skip(skip)
    if limit > 0 then obs = obs.limit(limit)
    F.many(obs)

  /** Fetch every document in the collection. */
  def findAll: F[Seq[Doc]] = F.many(collection.find())

  /** Count documents matching `filter` (all documents by default). */
  def count(filter: Bson = Filter.empty): F[Long] =
    F.single(collection.countDocuments(filter))

  /** True if at least one document matches `filter`. */
  def exists(filter: Bson): F[Boolean] =
    F.map(F.optional(collection.find(filter).limit(1)))(_.isDefined)

  // ── Update ──────────────────────────────────────────────────────────────────

  /** Apply `update` to the first document matching `filter`; returns the modified count. */
  def updateOne(filter: Bson, update: Bson): F[Long] =
    F.map(F.single(collection.updateOne(filter, update)))(_.getModifiedCount)

  /** Apply `update` to the document with the given id; returns the modified count. */
  def updateById(id: Id, update: Bson): F[Long] =
    updateOne(byId(id), update)

  /** Apply `update` to every document matching `filter`; returns the modified count. */
  def updateMany(filter: Bson, update: Bson): F[Long] =
    F.map(F.single(collection.updateMany(filter, update)))(_.getModifiedCount)

  /** Update the document with `id` and return the post-update document (`returnDocument = AFTER`). */
  def findAndUpdateById(id: Id, update: Bson): F[Option[Doc]] =
    val opts = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
    F.optional(collection.findOneAndUpdate(byId(id), update, opts))

  /** Insert or update the document with `id` (`upsert = true`). */
  def upsertById(id: Id, update: Bson): F[Unit] =
    val opts = UpdateOptions().upsert(true)
    F.map(F.single(collection.updateOne(byId(id), update, opts)))(_ => ())

  /** Replace the document with `id` wholesale; returns the modified count. */
  def replaceById(id: Id, doc: Doc): F[Long] =
    F.map(F.single(collection.replaceOne(byId(id), doc)))(_.getModifiedCount)

  // ── Delete ──────────────────────────────────────────────────────────────────

  /** Delete the first document matching `filter`; returns the deleted count. */
  def deleteOne(filter: Bson): F[Long] =
    F.map(F.single(collection.deleteOne(filter)))(_.getDeletedCount)

  /** Delete the document with the given id; returns the deleted count. */
  def deleteById(id: Id): F[Long] = deleteOne(byId(id))

  /** Delete every document matching `filter`; returns the deleted count. */
  def deleteMany(filter: Bson): F[Long] =
    F.map(F.single(collection.deleteMany(filter)))(_.getDeletedCount)

object MongoRepository:

  /** Construct a repository for `Doc` keyed by `Id`.
   *
   * The `idField` is typically `field[Doc](_._id)`; its `BsonEncoder[Id]` is resolved implicitly.
   */
  def apply[F[_], Doc, Id](
      collection: MongoCollection[Doc],
      idField: Field[Doc, Id]
  )(using MongoAsync[F], BsonEncoder[Id], ClassTag[Doc]): MongoRepository[F, Doc, Id] =
    new MongoRepository[F, Doc, Id](collection, idField)
