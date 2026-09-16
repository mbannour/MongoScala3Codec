package io.github.mbannour.mongo.codecs.compatibility

import org.bson.BsonDocument
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonDiscriminator
import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

// Sealed hierarchies are declared at file level, as in SealedTraitCompatibilitySpec.
sealed trait Shape
case class Dot(label: String) extends Shape
case class Pair(left: Shape, right: Shape) extends Shape

// The same shape again, with Task 13's custom discriminator values, to check the two compose.
sealed trait Labelled

@BsonDiscriminator("leaf")
case class LabelledLeaf(value: String) extends Labelled

@BsonDiscriminator("branch")
case class LabelledBranch(left: Labelled, right: Labelled) extends Labelled

/** BSON compatibility tests for types that refer to themselves. See [[UserCompatibilitySpec]] for the package-level contract.
  *
  * A recursive field is encoded through a registry lookup rather than by expanding the field's type into the generated code, and a
  * generated codec registers itself in the registry it hands to its own fields. A self-reference therefore resolves to the codec being
  * built, and recursion depth is a runtime property of the value rather than something fixed at compile time. These tests freeze that,
  * including for documents the library never wrote itself.
  *
  * Recursive *types* are in scope; cyclic runtime object graphs are not, and every value here is finite.
  */
class RecursiveCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class Node(value: String, next: Option[Node])
  case class Category(name: String, children: List[Category])
  case class Parent(name: String, child: Option[Child])
  case class Child(name: String, parent: Option[Parent])

  case class Address(city: String)
  case class User(name: String, address: Address)

  private val strings = CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())

  "A self-recursive type reached through Option" should "encode to the frozen representation and round-trip to an equal value" in {
    val registry = RegistryBuilder.from(strings).register[Node].build

    given codec: Codec[Node] = registry.get(classOf[Node])

    val node = Node("a", Some(Node("b", None)))

    // None stays an explicit BSON null, exactly as OptionCompatibilitySpec freezes it.
    CodecTestKit.assertBsonStructure(
      node,
      BsonDocument.parse("""{"value": "a", "next": {"value": "b", "next": null}}""")
    )

    CodecTestKit.roundTrip(node) shouldBe node
  }

  // Construction must stand on its own: a codec whose field type is the codec being built is the case
  // most likely to recurse forever or hand out a half-built instance, and it would do so here rather
  // than at the first encode.
  it should "finish building its codec before any value is encoded" in {
    val registry = RegistryBuilder.from(strings).register[Node].build

    val codec = registry.get(classOf[Node])

    codec.getEncoderClass shouldBe classOf[Node]
    // The nested lookup resolves to the very codec being built, not to a second copy of it.
    registry.get(classOf[Node]) should be theSameInstanceAs codec
  }

  // Recursion is a property of the type, not of every value: a chain that stops immediately is ordinary.
  it should "encode a terminal value, where no recursion occurs at runtime" in {
    val registry = RegistryBuilder.from(strings).register[Node].build

    given codec: Codec[Node] = registry.get(classOf[Node])

    val leaf = Node("leaf", None)

    CodecTestKit.assertBsonStructure(leaf, BsonDocument.parse("""{"value": "leaf", "next": null}"""))
    CodecTestKit.roundTrip(leaf) shouldBe leaf
  }

  it should "nest as deeply as the value does, not to some fixed depth" in {
    val registry = RegistryBuilder.from(strings).register[Node].build

    given codec: Codec[Node] = registry.get(classOf[Node])

    val node = Node("a", Some(Node("b", Some(Node("c", Some(Node("d", None)))))))

    CodecTestKit.assertBsonStructure(
      node,
      BsonDocument.parse(
        """{"value": "a", "next": {"value": "b", "next": {"value": "c", "next": {"value": "d", "next": null}}}}"""
      )
    )

    CodecTestKit.roundTrip(node) shouldBe node
  }

  it should "decode a hand-written document that was never produced by encode" in {
    val registry = RegistryBuilder.from(strings).register[Node].build

    given codec: Codec[Node] = registry.get(classOf[Node])

    val golden = BsonDocument.parse("""{"value": "a", "next": {"value": "b", "next": null}}""")

    CodecTestKit.fromBsonDocument[Node](golden) shouldBe Node("a", Some(Node("b", None)))
  }

  // A sanity check that recursion is genuine rather than unrolled a fixed number of times. This is not a
  // stress test, and no claim is made about arbitrary depth: deep BSON hits JVM and MongoDB limits anyway.
  it should "round-trip a chain of ten nodes" in {
    val registry = RegistryBuilder.from(strings).register[Node].build

    given codec: Codec[Node] = registry.get(classOf[Node])

    val chain = (1 to 10).foldRight(Option.empty[Node])((i, rest) => Some(Node(s"n$i", rest))).get

    CodecTestKit.roundTrip(chain) shouldBe chain
  }

  "A self-recursive type reached through List" should "encode to the frozen representation and round-trip to an equal value" in {
    val registry = RegistryBuilder.from(strings).register[Category].build

    given codec: Codec[Category] = registry.get(classOf[Category])

    val category = Category("root", List(Category("child-1", Nil), Category("child-2", Nil)))

    CodecTestKit.assertBsonStructure(
      category,
      BsonDocument.parse(
        """{"name": "root", "children": [{"name": "child-1", "children": []}, {"name": "child-2", "children": []}]}"""
      )
    )

    CodecTestKit.roundTrip(category) shouldBe category
  }

  it should "decode a hand-written document that was never produced by encode" in {
    val registry = RegistryBuilder.from(strings).register[Category].build

    given codec: Codec[Category] = registry.get(classOf[Category])

    val golden = BsonDocument.parse(
      """{"name": "root", "children": [{"name": "child-1", "children": []}, {"name": "child-2", "children": []}]}"""
    )

    CodecTestKit.fromBsonDocument[Category](golden) shouldBe
      Category("root", List(Category("child-1", Nil), Category("child-2", Nil)))
  }

  it should "nest through the collection as deeply as the value does" in {
    val registry = RegistryBuilder.from(strings).register[Category].build

    given codec: Codec[Category] = registry.get(classOf[Category])

    val category = Category(
      "root",
      List(Category("child-1", Nil), Category("child-2", List(Category("grandchild", Nil))))
    )

    val golden = BsonDocument.parse(
      """{"name": "root",
         "children": [{"name": "child-1", "children": []},
                      {"name": "child-2", "children": [{"name": "grandchild", "children": []}]}]}"""
    )

    CodecTestKit.assertBsonStructure(category, golden)
    CodecTestKit.roundTrip(category) shouldBe category
    CodecTestKit.fromBsonDocument[Category](golden) shouldBe category
  }

  it should "finish building its codec before any value is encoded" in {
    val registry = RegistryBuilder.from(strings).register[Category].build

    registry.get(classOf[Category]).getEncoderClass shouldBe classOf[Category]
  }

  "Two mutually recursive types" should "encode to the frozen representation and round-trip to an equal value" in {
    val registry = RegistryBuilder.from(strings).register[Parent].register[Child].build

    given codec: Codec[Parent] = registry.get(classOf[Parent])

    val parent = Parent("p", Some(Child("c", None)))

    CodecTestKit.assertBsonStructure(
      parent,
      BsonDocument.parse("""{"name": "p", "child": {"name": "c", "parent": null}}""")
    )

    CodecTestKit.roundTrip(parent) shouldBe parent
  }

  it should "decode a hand-written document that was never produced by encode" in {
    val registry = RegistryBuilder.from(strings).register[Parent].register[Child].build

    given codec: Codec[Parent] = registry.get(classOf[Parent])

    val golden = BsonDocument.parse("""{"name": "p", "child": {"name": "c", "parent": null}}""")

    CodecTestKit.fromBsonDocument[Parent](golden) shouldBe Parent("p", Some(Child("c", None)))
  }

  // Neither codec can be finished before the other exists, so construction of both has to complete
  // without either one reaching for the other.
  it should "finish building both codecs before any value is encoded" in {
    val registry = RegistryBuilder.from(strings).register[Parent].register[Child].build

    registry.get(classOf[Parent]).getEncoderClass shouldBe classOf[Parent]
    registry.get(classOf[Child]).getEncoderClass shouldBe classOf[Child]
  }

  "A recursive sealed hierarchy" should "carry a discriminator at every level and round-trip to an equal value" in {
    val registry = RegistryBuilder.from(strings).registerSealed[Shape].build

    given codec: Codec[Shape] = registry.get(classOf[Shape])

    val shape: Shape = Pair(Dot("l"), Pair(Dot("rl"), Dot("rr")))

    CodecTestKit.assertBsonStructure[Shape](
      shape,
      BsonDocument.parse(
        """{"_type": "Pair",
           "left": {"_type": "Dot", "label": "l"},
           "right": {"_type": "Pair",
                     "left": {"_type": "Dot", "label": "rl"},
                     "right": {"_type": "Dot", "label": "rr"}}}"""
      )
    )

    CodecTestKit.roundTrip[Shape](shape) shouldBe shape
  }

  it should "decode a hand-written document that was never produced by encode" in {
    val registry = RegistryBuilder.from(strings).registerSealed[Shape].build

    given codec: Codec[Shape] = registry.get(classOf[Shape])

    val golden = BsonDocument.parse(
      """{"_type": "Pair", "left": {"_type": "Dot", "label": "l"}, "right": {"_type": "Dot", "label": "r"}}"""
    )

    CodecTestKit.fromBsonDocument[Shape](golden) shouldBe Pair(Dot("l"), Dot("r"))
  }

  it should "finish building its codec before any value is encoded" in {
    val registry = RegistryBuilder.from(strings).registerSealed[Shape].build

    registry.get(classOf[Shape]).getEncoderClass shouldBe classOf[Shape]
  }

  it should "use custom discriminator values at every level when the subtypes carry them" in {
    val registry = RegistryBuilder.from(strings).registerSealed[Labelled].build

    given codec: Codec[Labelled] = registry.get(classOf[Labelled])

    val labelled: Labelled = LabelledBranch(LabelledLeaf("l"), LabelledBranch(LabelledLeaf("ml"), LabelledLeaf("mr")))

    CodecTestKit.assertBsonStructure[Labelled](
      labelled,
      BsonDocument.parse(
        """{"_type": "branch",
           "left": {"_type": "leaf", "value": "l"},
           "right": {"_type": "branch",
                     "left": {"_type": "leaf", "value": "ml"},
                     "right": {"_type": "leaf", "value": "mr"}}}"""
      )
    )

    CodecTestKit.roundTrip[Labelled](labelled) shouldBe labelled

    CodecTestKit.fromBsonDocument[Labelled](
      BsonDocument.parse("""{"_type": "branch", "left": {"_type": "leaf", "value": "l"}, "right": {"_type": "leaf", "value": "r"}}""")
    ) shouldBe LabelledBranch(LabelledLeaf("l"), LabelledLeaf("r"))
  }

  // Control: ordinary nesting is not recursion, and must keep working exactly as before.
  "An ordinary nested type" should "stay unaffected" in {
    val registry = RegistryBuilder.from(strings).register[Address].register[User].build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User("Alice", Address("Munich"))

    CodecTestKit.assertBsonStructure(user, BsonDocument.parse("""{"name": "Alice", "address": {"city": "Munich"}}"""))
    CodecTestKit.roundTrip(user) shouldBe user
  }
end RecursiveCompatibilitySpec
