# Repository Abstraction

`mongoscala3codec-repository` provides a **type-safe, effect-agnostic repository** over a MongoDB
collection. It ties the three layers of the library together: compile-time codecs (`RegistryBuilder`),
the type-safe DSL (`field`, `Filter`, `Update`, `Sort`), and ordinary CRUD — with no string field
names and no driver boilerplate.

> **Status:** prototype (introduced in 0.0.12). The API may evolve based on feedback.

## Installation

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec"            % "0.0.11",
  "io.github.mbannour" %% "mongoscala3codec-dsl"        % "0.0.11",
  "io.github.mbannour" %% "mongoscala3codec-repository" % "0.0.11",
  "org.mongodb.scala"  %% "mongo-scala-driver"          % "5.7.0"
)
```

The repository artifact depends on `mongo-scala-driver` directly (it needs `MongoCollection`).

## Quick start

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder
import io.github.mbannour.mongo.dsl.*
import io.github.mbannour.mongo.repository.*
import org.bson.types.ObjectId
import org.mongodb.scala.*
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global

case class Employee(_id: ObjectId, name: String, department: String, salary: Double, active: Boolean)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Employee]
  .build

val db = MongoClient("mongodb://localhost:27017")
  .getDatabase("hr")
  .withCodecRegistry(registry)

// `idField` is just a DSL field reference — typically `_._id`
val repo: MongoRepository[Future, Employee, ObjectId] =
  MongoRepository(db.getCollection[Employee]("employees"), field[Employee](_._id))
```

Everything is driven by the DSL — query/update arguments are plain `Bson`, so the type-safe
`field`/`Filter`/`Update`/`Sort` builders plug straight in:

```scala
// Read with a typed filter + sort + limit
repo.find(
  filter = Filter.and(
    field[Employee](_.department) === "Engineering",
    field[Employee](_.salary) > 80_000.0
  ),
  sort  = Some(field[Employee](_.salary).desc),
  limit = 10
)                                              // Future[Seq[Employee]]

repo.findById(someId)                          // Future[Option[Employee]]
repo.count(field[Employee](_.active) === true) // Future[Long]

// Atomic, typed updates
repo.updateById(id, Update.combine(
  field[Employee](_.salary).mul(1.05),
  field[Employee](_.active) := true
))                                             // Future[Long] (modified count)

repo.findAndUpdateById(id, field[Employee](_.salary).inc(1000.0))
                                               // Future[Option[Employee]] (post-update)

repo.deleteById(id)                            // Future[Long] (deleted count)
```

## API

| Method | Returns | Notes |
|--------|---------|-------|
| `insert(doc)` | `F[Unit]` | single insert |
| `insertMany(docs)` | `F[Unit]` | no-op when empty |
| `findById(id)` | `F[Option[Doc]]` | |
| `findOne(filter)` | `F[Option[Doc]]` | first match |
| `find(filter, sort?, projection?, skip, limit)` | `F[Seq[Doc]]` | all args default |
| `findAll` | `F[Seq[Doc]]` | |
| `count(filter)` | `F[Long]` | all docs by default |
| `exists(filter)` | `F[Boolean]` | |
| `updateOne(filter, update)` / `updateById(id, update)` | `F[Long]` | modified count |
| `updateMany(filter, update)` | `F[Long]` | modified count |
| `findAndUpdateById(id, update)` | `F[Option[Doc]]` | returns post-update doc |
| `upsertById(id, update)` | `F[Unit]` | insert-or-update |
| `replaceById(id, doc)` | `F[Long]` | modified count |
| `deleteOne(filter)` / `deleteById(id)` | `F[Long]` | deleted count |
| `deleteMany(filter)` | `F[Long]` | deleted count |

## Effect-agnostic by design

The repository is parameterised over an effect `F[_]` and talks to the driver only through a small
type class, [`MongoAsync[F]`](../repository/src/main/scala/io/github/mbannour/mongo/repository/MongoAsync.scala):

```scala
trait MongoAsync[F[_]]:
  def single[A](obs: => SingleObservable[A]): F[A]
  def optional[A](obs: => Observable[A]): F[Option[A]]
  def many[A](obs: => Observable[A]): F[Seq[A]]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def pure[A](a: => A): F[A]
```

A `Future` instance ships in this module (`import io.github.mbannour.mongo.repository.*` and an
`ExecutionContext` in scope). Supporting **ZIO**, **cats-effect `IO`**, or **fs2** is a few lines —
provide a `MongoAsync` instance in a thin integration module and the entire repository works unchanged.
This is the recommended path for microservice codebases that standardise on one effect system.

## How the `_id` field works

`MongoRepository` takes an `idField: Field[Doc, Id]` — the same DSL field reference you already use
elsewhere, almost always `field[Doc](_._id)`. The id-based methods (`findById`, `updateById`, …) build
their filter as `idField === id`, so the id type is checked at compile time and a `BsonEncoder[Id]`
(for `ObjectId`, `String`, `Int`, opaque-type wrappers, …) is resolved implicitly.
