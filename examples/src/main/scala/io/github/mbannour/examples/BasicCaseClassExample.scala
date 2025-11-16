package io.github.mbannour.examples

import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.mongodb.scala.model.{Filters, Updates}
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase, ObservableFuture, SingleObservableFuture}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.{Try, Using}

/** Example 1: Basic Case Class with Primitive Types
  *
  * Demonstrates:
  *   - Simple case class serialization
  *   - Primitive types (String, Int, Double, Boolean)
  *   - ObjectId as _id field
  *   - Basic CRUD operations
  */
object BasicCaseClassExample:

  // Domain model - simple case class with primitives
  case class User(
      _id: ObjectId,
      name: String,
      age: Int,
      email: String,
      isActive: Boolean
  )

  def main(args: Array[String]): Unit =
    println("=" * 60)
    println("Example 1: Basic Case Class with Primitive Types")
    println("=" * 60)

    // Connect to MongoDB (assumes local MongoDB on default port)
    val mongoClient: MongoClient = MongoClient("mongodb://localhost:27017")
    val database: MongoDatabase = mongoClient.getDatabase("examples_db")

    // Create codec registry with User codec
    val registry: CodecRegistry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[User]
      .build

    // Get collection with codec registry
    val collection: MongoCollection[User] = database
      .getCollection[User]("users")
      .withCodecRegistry(registry)

    Try {
      // 1. INSERT - Create and insert a user
      println("\n1. Inserting a new user...")
      val user = User(
        _id = new ObjectId(),
        name = "Alice Johnson",
        age = 30,
        email = "alice@example.com",
        isActive = true
      )

      Await.result(collection.insertOne(user).toFuture(), 5.seconds)
      println(s"✓ Inserted user: ${user.name} (ID: ${user._id})")

      // 2. FIND ONE - Retrieve the user
      println("\n2. Finding the user...")
      val foundUser = Await.result(
        collection.find().first().toFuture(),
        5.seconds
      )
      println(s"✓ Found user: ${foundUser.name}, Age: ${foundUser.age}, Active: ${foundUser.isActive}")

      // 3. FIND ALL - Insert more users and find all
      println("\n3. Inserting multiple users...")
      val users = Seq(
        User(new ObjectId(), "Bob Smith", 25, "bob@example.com", true),
        User(new ObjectId(), "Carol White", 35, "carol@example.com", false),
        User(new ObjectId(), "David Brown", 28, "david@example.com", true)
      )
      Await.result(collection.insertMany(users).toFuture(), 5.seconds)
      println(s"✓ Inserted ${users.size} more users")

      val allUsers = Await.result(collection.find().toFuture(), 5.seconds)
      println(s"\n✓ Total users in database: ${allUsers.size}")
      allUsers.foreach(u => println(s"   - ${u.name} (${u.age} years old)"))

      // 4. UPDATE - Update a user
      println("\n4. Updating user...")
      Await.result(
        collection
          .updateOne(
            Filters.equal("name", "Alice Johnson"),
            Updates.set("age", 31)
          )
          .toFuture(),
        5.seconds
      )

      val updatedUser = Await.result(
        collection.find(Filters.equal("name", "Alice Johnson")).first().toFuture(),
        5.seconds
      )
      println(s"✓ Updated Alice's age to: ${updatedUser.age}")

      // 5. DELETE - Delete a user
      println("\n5. Deleting user...")
      Await.result(
        collection.deleteOne(Filters.equal("name", "Bob Smith")).toFuture(),
        5.seconds
      )
      println("✓ Deleted Bob Smith")

      val remainingUsers = Await.result(collection.find().toFuture(), 5.seconds)
      println(s"✓ Remaining users: ${remainingUsers.size}")

      println("\n" + "=" * 60)
      println("Example completed successfully!")
      println("=" * 60)
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

end BasicCaseClassExample
