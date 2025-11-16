package io.github.mbannour.examples

import io.github.mbannour.mongo.codecs.{EnumValueCodecProvider, RegistryBuilder}
import org.bson.codecs.{Codec, IntegerCodec, StringCodec}
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.mongodb.scala.model.{Filters, Updates}
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase, ObservableFuture, SingleObservableFuture}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.Try

// IMPORTANT: Enums must be defined at package level (not inside objects)
// for reflection to work properly with EnumValueCodecProvider

// 1. Simple string-based enum
enum Priority:
  case Low, Medium, High, Critical

// 2. Enum with custom field (code pattern)
enum Status(val code: Int):
  case Pending extends Status(0)
  case InProgress extends Status(1)
  case Completed extends Status(2)
  case Cancelled extends Status(3)

// 3. Enum with multiple custom fields
enum Department(val code: String, val budget: Int):
  case Engineering extends Department("ENG", 1000000)
  case Sales extends Department("SLS", 500000)
  case Marketing extends Department("MKT", 300000)
  case HR extends Department("HRD", 200000)

// Domain model using enums
case class Task(
    _id: ObjectId,
    title: String,
    priority: Priority,
    status: Status,
    department: Department,
    assignedTo: String
)

/** Example 3: Scala 3 Enum Support
  *
  * Demonstrates:
  *   - String-based enum serialization
  *   - Ordinal-based enum serialization
  *   - Enums with custom fields (code pattern)
  *   - Multiple enums in one case class
  */
object EnumSupportExample:

  def main(args: Array[String]): Unit =
    println("=" * 60)
    println("Example 3: Scala 3 Enum Support")
    println("=" * 60)

    val mongoClient: MongoClient = MongoClient("mongodb://localhost:27017")
    val database: MongoDatabase = mongoClient.getDatabase("examples_db")

    // Create codec providers for each enum type

    // 1. String-based enum (stores as "Low", "Medium", etc.)
    val priorityProvider = EnumValueCodecProvider.forStringEnum[Priority]

    // 2. Integer-based enum using custom code field
    given Codec[Int] = new IntegerCodec().asInstanceOf[Codec[Int]]
    val statusProvider = EnumValueCodecProvider[Status, Int](
      toValue = _.code,
      fromValue = code =>
        Status.values
          .find(_.code == code)
          .getOrElse(
            throw new IllegalArgumentException(s"Invalid status code: $code")
          )
    )

    // 3. String-based enum using custom code field
    given Codec[String] = new StringCodec()
    val departmentProvider = EnumValueCodecProvider[Department, String](
      toValue = _.code,
      fromValue = code =>
        Department.values
          .find(_.code == code)
          .getOrElse(
            throw new IllegalArgumentException(s"Invalid department code: $code")
          )
    )

    // Build registry with all enum providers
    val registry: CodecRegistry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withProviders(priorityProvider, statusProvider, departmentProvider)
      .register[Task]
      .build

    val collection: MongoCollection[Task] = database
      .getCollection[Task]("tasks")
      .withCodecRegistry(registry)

    Try {
      println("\n1. Creating tasks with different enum values...")

      val tasks = Seq(
        Task(
          new ObjectId(),
          "Implement user authentication",
          Priority.Critical,
          Status.InProgress,
          Department.Engineering,
          "Alice"
        ),
        Task(
          new ObjectId(),
          "Design marketing campaign",
          Priority.High,
          Status.Pending,
          Department.Marketing,
          "Bob"
        ),
        Task(
          new ObjectId(),
          "Close Q4 sales",
          Priority.High,
          Status.InProgress,
          Department.Sales,
          "Carol"
        ),
        Task(
          new ObjectId(),
          "Review job applications",
          Priority.Medium,
          Status.Pending,
          Department.HR,
          "David"
        ),
        Task(
          new ObjectId(),
          "Update website",
          Priority.Low,
          Status.Completed,
          Department.Engineering,
          "Alice"
        )
      )

      Await.result(collection.insertMany(tasks).toFuture(), 5.seconds)
      println(s"✓ Inserted ${tasks.size} tasks with various enum values")

      // Retrieve and display all tasks
      println("\n2. Retrieving all tasks...")
      val allTasks = Await.result(collection.find().toFuture(), 5.seconds)

      allTasks.foreach { task =>
        println(s"\n  Task: ${task.title}")
        println(s"  Priority: ${task.priority} (string)")
        println(s"  Status: ${task.status} (code: ${task.status.code})")
        println(s"  Department: ${task.department} (code: ${task.department.code}, budget: ${task.department.budget})")
        println(s"  Assigned to: ${task.assignedTo}")
      }

      // Query by enum value - Priority
      println("\n3. Querying by enum value (Priority.High)...")
      val highPriorityTasks = Await.result(
        collection.find(Filters.equal("priority", "High")).toFuture(),
        5.seconds
      )
      println(s"✓ Found ${highPriorityTasks.size} high priority tasks:")
      highPriorityTasks.foreach(t => println(s"   - ${t.title}"))

      // Query by enum with custom field (Status code)
      println("\n4. Querying by custom field enum (Status.InProgress with code=1)...")
      val inProgressTasks = Await.result(
        collection.find(Filters.equal("status", 1)).toFuture(),
        5.seconds
      )
      println(s"✓ Found ${inProgressTasks.size} in-progress tasks:")
      inProgressTasks.foreach(t => println(s"   - ${t.title}"))

      // Query by department code
      println("\n5. Querying by department code (Engineering = 'ENG')...")
      val engineeringTasks = Await.result(
        collection.find(Filters.equal("department", "ENG")).toFuture(),
        5.seconds
      )
      println(s"✓ Found ${engineeringTasks.size} engineering tasks:")
      engineeringTasks.foreach(t => println(s"   - ${t.title}"))

      // Update enum field
      println("\n6. Updating enum field...")
      Await.result(
        collection
          .updateOne(
            Filters.equal("title", "Implement user authentication"),
            Updates.set("status", 2) // Status.Completed.code
          )
          .toFuture(),
        5.seconds
      )

      val updatedTask = Await.result(
        collection.find(Filters.equal("title", "Implement user authentication")).first().toFuture(),
        5.seconds
      )
      println(s"✓ Updated task status to: ${updatedTask.status}")

      // Count tasks by priority
      println("\n7. Aggregating tasks by priority...")
      val priorityCounts = allTasks.groupBy(_.priority).view.mapValues(_.size).toMap
      priorityCounts.foreach { case (priority, count) =>
        println(s"   ${priority}: $count tasks")
      }

      println("\n" + "=" * 60)
      println("Example completed successfully!")
      println("=" * 60)
      println("\nKey Points:")
      println("- Priority stored as string: \"Low\", \"Medium\", \"High\", \"Critical\"")
      println("- Status stored as integer code: 0, 1, 2, 3")
      println("- Department stored as string code: \"ENG\", \"SLS\", \"MKT\", \"HRD\"")
    }.recover { case e: Exception =>
      println(s"\n✗ Error: ${e.getMessage}")
      e.printStackTrace()
    }.get

    // Cleanup
    println("\nCleaning up...")
    Await.result(database.drop().toFuture(), 5.seconds)
    mongoClient.close()
    println("✓ Database dropped and connection closed")
  end main

end EnumSupportExample
