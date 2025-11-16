package io.github.mbannour.examples

import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.mongodb.scala.model.{Filters, Updates}
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase, ObservableFuture, SingleObservableFuture}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.Try

/** Example 2: Nested Case Classes
  *
  * Demonstrates:
  *   - Nested case class structures
  *   - Automatic codec generation for nested types
  *   - Registration order (nested types first)
  *   - Querying nested fields
  */
object NestedStructuresExample:

  // Nested domain models
  case class Address(
      street: String,
      city: String,
      state: String,
      zipCode: String
  )

  case class ContactInfo(
      email: String,
      phone: String,
      address: Address
  )

  case class Company(
      _id: ObjectId,
      name: String,
      founded: Int,
      headquarters: Address,
      contact: ContactInfo,
      employeeCount: Int
  )

  def main(args: Array[String]): Unit =
    println("=" * 60)
    println("Example 2: Nested Case Class Structures")
    println("=" * 60)

    val mongoClient: MongoClient = MongoClient("mongodb://localhost:27017")
    val database: MongoDatabase = mongoClient.getDatabase("examples_db")

    // Register nested types FIRST, then parent types
    val registry: CodecRegistry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .registerAll[(Address, ContactInfo, Company)]
      .register[ContactInfo]
      .build

    val collection: MongoCollection[Company] = database
      .getCollection[Company]("companies")
      .withCodecRegistry(registry)

    Try {
      // Create companies with nested structures
      println("\n1. Creating companies with nested data...")

      val techCorp = Company(
        _id = new ObjectId(),
        name = "TechCorp Inc.",
        founded = 2015,
        headquarters = Address(
          street = "123 Tech Street",
          city = "San Francisco",
          state = "CA",
          zipCode = "94105"
        ),
        contact = ContactInfo(
          email = "info@techcorp.com",
          phone = "+1-555-0100",
          address = Address(
            street = "456 Contact Ave",
            city = "San Francisco",
            state = "CA",
            zipCode = "94105"
          )
        ),
        employeeCount = 250
      )

      val startupLabs = Company(
        _id = new ObjectId(),
        name = "Startup Labs",
        founded = 2020,
        headquarters = Address(
          street = "789 Innovation Blvd",
          city = "Austin",
          state = "TX",
          zipCode = "73301"
        ),
        contact = ContactInfo(
          email = "hello@startuplabs.io",
          phone = "+1-555-0200",
          address = Address(
            street = "789 Innovation Blvd",
            city = "Austin",
            state = "TX",
            zipCode = "73301"
          )
        ),
        employeeCount = 50
      )

      Await.result(
        collection.insertMany(Seq(techCorp, startupLabs)).toFuture(),
        5.seconds
      )
      println("✓ Inserted 2 companies with nested structures")

      // Query all companies
      println("\n2. Retrieving companies...")
      val companies = Await.result(collection.find().toFuture(), 5.seconds)

      companies.foreach { company =>
        println(s"\n  Company: ${company.name}")
        println(s"  Founded: ${company.founded}")
        println(s"  HQ: ${company.headquarters.city}, ${company.headquarters.state}")
        println(s"  Contact: ${company.contact.email}")
        println(s"  Employees: ${company.employeeCount}")
      }

      // Query by nested field using dot notation
      println("\n3. Querying by nested field (city = 'San Francisco')...")
      val sfCompanies = Await.result(
        collection.find(Filters.equal("headquarters.city", "San Francisco")).toFuture(),
        5.seconds
      )

      println(s"✓ Found ${sfCompanies.size} companies in San Francisco:")
      sfCompanies.foreach(c => println(s"   - ${c.name}"))

      // Query by deeply nested field
      println("\n4. Querying by deeply nested field (contact.address.state = 'TX')...")
      val txCompanies = Await.result(
        collection.find(Filters.equal("contact.address.state", "TX")).toFuture(),
        5.seconds
      )

      println(s"✓ Found ${txCompanies.size} companies with TX contact address:")
      txCompanies.foreach(c => println(s"   - ${c.name}"))

      // Update nested field
      println("\n5. Updating nested field...")
      Await.result(
        collection
          .updateOne(
            Filters.equal("name", "TechCorp Inc."),
            Updates.set("headquarters.city", "Palo Alto")
          )
          .toFuture(),
        5.seconds
      )

      val updated = Await.result(
        collection.find(Filters.equal("name", "TechCorp Inc.")).first().toFuture(),
        5.seconds
      )
      println(s"✓ Updated TechCorp HQ city to: ${updated.headquarters.city}")

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

end NestedStructuresExample
