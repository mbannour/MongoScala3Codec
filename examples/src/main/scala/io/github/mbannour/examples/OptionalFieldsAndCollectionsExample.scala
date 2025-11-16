package io.github.mbannour.examples

import io.github.mbannour.mongo.codecs.{CodecConfig, NoneHandling, RegistryBuilder}
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.mongodb.scala.model.{Filters, Updates}
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase, ObservableFuture, SingleObservableFuture}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.Try

/** Example 4: Optional Fields and Collections
  *
  * Demonstrates:
  *   - Option[T] fields with NoneHandling.Ignore (omit from BSON)
  *   - Option[T] fields with NoneHandling.Encode (store as null)
  *   - Collections: List, Set, Vector, Map
  *   - Nested collections
  *   - Empty collections vs missing fields
  */
object OptionalFieldsAndCollectionsExample:

  case class SocialLinks(
      twitter: Option[String],
      linkedin: Option[String],
      github: Option[String]
  )

  case class BlogPost(
      _id: ObjectId,
      title: String,
      content: String,
      author: String,
      tags: List[String], // List of tags
      categories: Set[String], // Set of categories (no duplicates)
      relatedPosts: Vector[ObjectId], // Vector of related post IDs
      metadata: Map[String, String], // Key-value metadata
      publishedAt: Option[java.time.Instant], // Optional publish date
      excerpt: Option[String], // Optional excerpt
      socialLinks: Option[SocialLinks] // Optional nested structure
  )

  def main(args: Array[String]): Unit =
    println("=" * 60)
    println("Example 4: Optional Fields and Collections")
    println("=" * 60)

    val mongoClient: MongoClient = MongoClient("mongodb://localhost:27017")
    val database: MongoDatabase = mongoClient.getDatabase("examples_db")

    // Configure to ignore None values (don't store null in DB)
    val codecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    val registry: CodecRegistry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(codecConfig)
      .registerAll[(SocialLinks, BlogPost)]
      .build

    val collection: MongoCollection[BlogPost] = database
      .getCollection[BlogPost]("blog_posts")
      .withCodecRegistry(registry)

    Try {
      println("\n1. Creating blog posts with various optional fields and collections...")

      // Post with ALL fields populated
      val fullPost = BlogPost(
        _id = new ObjectId(),
        title = "Getting Started with Scala 3",
        content = "Scala 3 brings many exciting features...",
        author = "Alice",
        tags = List("scala", "programming", "tutorial"),
        categories = Set("Technology", "Programming"),
        relatedPosts = Vector.empty,
        metadata = Map(
          "readTime" -> "5 min",
          "difficulty" -> "beginner"
        ),
        publishedAt = Some(java.time.Instant.now()),
        excerpt = Some("Learn about Scala 3's new features"),
        socialLinks = Some(
          SocialLinks(
            twitter = Some("@alice_dev"),
            linkedin = Some("alice-developer"),
            github = Some("alice")
          )
        )
      )

      // Post with SOME fields missing (None values won't be stored)
      val partialPost = BlogPost(
        _id = new ObjectId(),
        title = "MongoDB with Scala",
        content = "Working with MongoDB in Scala...",
        author = "Bob",
        tags = List("mongodb", "scala", "database"),
        categories = Set("Technology", "Database"),
        relatedPosts = Vector.empty,
        metadata = Map("readTime" -> "10 min"),
        publishedAt = None, // Not published yet - won't be in BSON
        excerpt = None, // No excerpt - won't be in BSON
        socialLinks = None // No social links - won't be in BSON
      )

      // Post with empty collections
      val minimalPost = BlogPost(
        _id = new ObjectId(),
        title = "Draft Post",
        content = "This is a draft...",
        author = "Carol",
        tags = List.empty, // Empty list - will be stored as []
        categories = Set.empty, // Empty set - will be stored as []
        relatedPosts = Vector.empty,
        metadata = Map.empty, // Empty map - will be stored as {}
        publishedAt = None,
        excerpt = None,
        socialLinks = None
      )

      Await.result(
        collection.insertMany(Seq(fullPost, partialPost, minimalPost)).toFuture(),
        5.seconds
      )
      println("✓ Inserted 3 blog posts with different field configurations")

      // Retrieve and inspect BSON structure
      println("\n2. Retrieving posts and showing field presence...")
      val posts = Await.result(collection.find().toFuture(), 5.seconds)

      posts.foreach { post =>
        println(s"\n  Post: ${post.title}")
        println(s"  Author: ${post.author}")
        println(s"  Tags (${post.tags.size}): ${post.tags.mkString(", ")}")
        println(s"  Categories (${post.categories.size}): ${post.categories.mkString(", ")}")
        println(s"  Metadata entries: ${post.metadata.size}")
        post.metadata.foreach { case (k, v) => println(s"    - $k: $v") }
        println(s"  Published: ${post.publishedAt.map(_.toString).getOrElse("Not published")}")
        println(s"  Excerpt: ${post.excerpt.getOrElse("No excerpt")}")
        println(s"  Social Links: ${post.socialLinks.map(_ => "Present").getOrElse("Not set")}")

        post.socialLinks.foreach { links =>
          println(s"    Twitter: ${links.twitter.getOrElse("N/A")}")
          println(s"    LinkedIn: ${links.linkedin.getOrElse("N/A")}")
          println(s"    GitHub: ${links.github.getOrElse("N/A")}")
        }
      }

      // Query by collection element
      println("\n3. Querying posts by tag...")
      val scalaPosts = Await.result(
        collection.find(Filters.equal("tags", "scala")).toFuture(),
        5.seconds
      )
      println(s"✓ Found ${scalaPosts.size} posts with 'scala' tag:")
      scalaPosts.foreach(p => println(s"   - ${p.title}"))

      // Query by optional field existence
      println("\n4. Querying by optional field existence...")
      val publishedPosts = Await.result(
        collection.find(Filters.exists("publishedAt", true)).toFuture(),
        5.seconds
      )
      println(s"✓ Found ${publishedPosts.size} published posts:")
      publishedPosts.foreach(p => println(s"   - ${p.title}"))

      val unpublishedPosts = Await.result(
        collection.find(Filters.exists("publishedAt", false)).toFuture(),
        5.seconds
      )
      println(s"✓ Found ${unpublishedPosts.size} unpublished posts:")
      unpublishedPosts.foreach(p => println(s"   - ${p.title}"))

      // Update: Add new tags to a post
      println("\n5. Adding tags to a post...")
      Await.result(
        collection
          .updateOne(
            Filters.equal("title", "Draft Post"),
            Updates.set("tags", List("draft", "wip", "scala"))
          )
          .toFuture(),
        5.seconds
      )

      val updated = Await.result(
        collection.find(Filters.equal("title", "Draft Post")).first().toFuture(),
        5.seconds
      )
      println(s"✓ Updated tags: ${updated.tags.mkString(", ")}")

      // Update: Set optional field
      println("\n6. Publishing a draft post...")
      Await.result(
        collection
          .updateOne(
            Filters.equal("title", "Draft Post"),
            Updates.combine(
              Updates.set("publishedAt", java.time.Instant.now()),
              Updates.set("excerpt", "This was a draft, now published!")
            )
          )
          .toFuture(),
        5.seconds
      )

      val published = Await.result(
        collection.find(Filters.equal("title", "Draft Post")).first().toFuture(),
        5.seconds
      )
      println(s"✓ Post now published at: ${published.publishedAt.get}")
      println(s"✓ Excerpt: ${published.excerpt.get}")

      println("\n" + "=" * 60)
      println("Example completed successfully!")
      println("=" * 60)
      println("\nKey Points:")
      println("- None values are NOT stored in MongoDB (NoneHandling.Ignore)")
      println("- Empty collections ARE stored as [] or {}")
      println("- Can query by collection elements using equality")
      println("- Can check field existence with Filters.exists()")
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

end OptionalFieldsAndCollectionsExample
