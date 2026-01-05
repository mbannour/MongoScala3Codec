package io.github.mbannour.mongo.codecs

import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter, BsonValue}

/** Testing utilities for BSON codecs.
  *
  * Provides helper methods for testing codec symmetry and round-trip encoding/decoding.
  *
  * Key features:
  *   - Round-trip testing with customizable encoder/decoder contexts
  *   - Flexible BSON comparison (ignoring field order, partial matching)
  *   - Support for both document and scalar value encoding
  *   - Property-based testing integration
  *   - Enhanced error messages with BSON pretty-printing
  */
object CodecTestKit:

  /** Perform a round-trip encode/decode operation.
    *
    * Encodes the value to BSON and then decodes it back, verifying that the codec can correctly serialize and deserialize the data.
    *
    * @param value
    *   The value to round-trip
    * @param codec
    *   The codec to use
    * @return
    *   The decoded value after round-tripping
    */
  def roundTrip[T](value: T)(using codec: Codec[T]): T =
    val doc = toBsonDocument(value)
    fromBsonDocument[T](doc)

  /** Perform a round-trip encode/decode operation with custom contexts.
    *
    * @param value
    *   The value to round-trip
    * @param encoderContext
    *   Custom encoder context
    * @param decoderContext
    *   Custom decoder context
    * @param codec
    *   The codec to use
    * @return
    *   The decoded value after round-tripping
    */
  def roundTripWithContext[T](
      value: T,
      encoderContext: EncoderContext = EncoderContext.builder().build(),
      decoderContext: DecoderContext = DecoderContext.builder().build()
  )(using codec: Codec[T]): T =
    val doc = toBsonDocument(value, encoderContext)
    fromBsonDocument[T](doc, decoderContext)

  /** Convert a value to a BsonDocument using the given codec.
    *
    * @param value
    *   The value to encode
    * @param codec
    *   The codec to use for encoding
    * @return
    *   The encoded BsonDocument
    */
  def toBsonDocument[T](value: T)(using codec: Codec[T]): BsonDocument =
    toBsonDocument(value, EncoderContext.builder().build())

  /** Convert a value to a BsonDocument using the given codec and custom context.
    *
    * @param value
    *   The value to encode
    * @param encoderContext
    *   The encoder context to use
    * @param codec
    *   The codec to use for encoding
    * @return
    *   The encoded BsonDocument
    */
  def toBsonDocument[T](value: T, encoderContext: EncoderContext)(using codec: Codec[T]): BsonDocument =
    val doc = new BsonDocument()
    val writer = new BsonDocumentWriter(doc)
    codec.encode(writer, value, encoderContext)
    doc

  /** Extract a specific field from the encoded BSON document.
    *
    * This is useful when you need to verify individual field encodings rather than the entire document structure.
    *
    * @param value
    *   The value to encode
    * @param fieldName
    *   The name of the field to extract
    * @param codec
    *   The codec to use for encoding
    * @return
    *   The BsonValue for the specified field, or null if not present
    * @example
    *   {{{ case class User(name: String, age: Int) val user = User("Alice", 30) val nameValue: BsonValue = CodecTestKit.extractField(user,
    *   "name") // nameValue is BsonString("Alice") }}}
    */
  def extractField[T](value: T, fieldName: String)(using codec: Codec[T]): BsonValue =
    toBsonDocument(value).get(fieldName)

  /** Decode a BsonDocument to a value using the given codec.
    *
    * @param doc
    *   The BsonDocument to decode
    * @param codec
    *   The codec to use for decoding
    * @return
    *   The decoded value
    */
  def fromBsonDocument[T](doc: BsonDocument)(using codec: Codec[T]): T =
    fromBsonDocument(doc, DecoderContext.builder().build())

  /** Decode a BsonDocument to a value using the given codec and custom context.
    *
    * @param doc
    *   The BsonDocument to decode
    * @param decoderContext
    *   The decoder context to use
    * @param codec
    *   The codec to use for decoding
    * @return
    *   The decoded value
    */
  def fromBsonDocument[T](doc: BsonDocument, decoderContext: DecoderContext)(using codec: Codec[T]): T =
    val reader = new BsonDocumentReader(doc)
    codec.decode(reader, decoderContext)

  /** Assert that a codec maintains symmetry (encode then decode yields original value).
    *
    * This is useful in property-based testing to verify codec correctness.
    *
    * @param value
    *   The value to test
    * @param codec
    *   The codec to test
    * @throws java.lang.AssertionError
    *   if the round-trip does not preserve the value
    */
  def assertCodecSymmetry[T](value: T)(using codec: Codec[T]): Unit =
    val bsonDoc = toBsonDocument(value)
    val result = roundTrip(value)
    assert(
      result == value,
      s"""Codec symmetry violation:
         |Original:    $value
         |Round-trip:  $result
         |BSON:        ${prettyPrint(bsonDoc)}
         |""".stripMargin
    )
  end assertCodecSymmetry

  /** Assert codec symmetry with custom contexts.
    *
    * @param value
    *   The value to test
    * @param encoderContext
    *   Custom encoder context
    * @param decoderContext
    *   Custom decoder context
    * @param codec
    *   The codec to test
    * @throws java.lang.AssertionError
    *   if the round-trip does not preserve the value
    */
  def assertCodecSymmetryWithContext[T](
      value: T,
      encoderContext: EncoderContext = EncoderContext.builder().build(),
      decoderContext: DecoderContext = DecoderContext.builder().build()
  )(using codec: Codec[T]): Unit =
    val result = roundTripWithContext(value, encoderContext, decoderContext)
    assert(
      result == value,
      s"""Codec symmetry violation (with custom context):
         |Original:    $value
         |Round-trip:  $result
         |""".stripMargin
    )
  end assertCodecSymmetryWithContext

  /** Test codec symmetry and return Either for property-based testing.
    *
    * Instead of throwing an exception, returns Left with error message or Right(()) on success.
    *
    * @param value
    *   The value to test
    * @param codec
    *   The codec to test
    * @return
    *   Right(()) if symmetry holds, Left(error message) otherwise
    */
  def checkCodecSymmetry[T](value: T)(using codec: Codec[T]): Either[String, Unit] =
    Try(roundTrip(value)) match
      case Success(result) if result == value => Right(())
      case Success(result) =>
        val bsonDoc = toBsonDocument(value)
        Left(s"""Codec symmetry violation:
                |Original:    $value
                |Round-trip:  $result
                |BSON:        ${prettyPrint(bsonDoc)}
                |""".stripMargin)
      case Failure(exception) =>
        Left(s"Codec round-trip failed with exception: ${exception.getMessage}")

  /** Test that encoding and decoding produces the expected BSON structure.
    *
    * @param value
    *   The value to encode
    * @param expectedBson
    *   The expected BSON document structure
    * @param codec
    *   The codec to use
    * @throws java.lang.AssertionError
    *   if the encoded value doesn't match expectations
    */
  def assertBsonStructure[T](value: T, expectedBson: BsonDocument)(using codec: Codec[T]): Unit =
    val actual = toBsonDocument(value)
    assert(
      actual == expectedBson,
      s"""BSON structure mismatch:
         |Expected: ${prettyPrint(expectedBson)}
         |Actual:   ${prettyPrint(actual)}
         |Diff:     ${diff(expectedBson, actual)}
         |""".stripMargin
    )
  end assertBsonStructure

  /** Test that encoding produces a BSON structure that contains all expected fields.
    *
    * This is more flexible than assertBsonStructure as it ignores field order and allows additional fields in the actual document.
    *
    * @param value
    *   The value to encode
    * @param expectedFields
    *   Map of expected field names to values
    * @param codec
    *   The codec to use
    * @throws java.lang.AssertionError
    *   if any expected field is missing or has wrong value
    */
  def assertBsonContains[T](value: T, expectedFields: Map[String, BsonValue])(using codec: Codec[T]): Unit =
    val actual = toBsonDocument(value)
    val missing = expectedFields.filterNot { case (key, expectedValue) =>
      actual.containsKey(key) && actual.get(key) == expectedValue
    }
    assert(
      missing.isEmpty,
      s"""BSON structure doesn't contain expected fields:
         |Missing/Incorrect: ${missing.map { case (k, v) => s"$k=$v" }.mkString(", ")}
         |Actual BSON: ${prettyPrint(actual)}
         |""".stripMargin
    )
  end assertBsonContains

  /** Test that two BSON documents are structurally equivalent (same fields and values, ignoring order).
    *
    * This performs deep comparison, recursively checking nested documents and arrays.
    *
    * @param doc1
    *   First document
    * @param doc2
    *   Second document
    * @return
    *   true if documents are equivalent, false otherwise
    */
  def bsonEquivalent(doc1: BsonDocument, doc2: BsonDocument): Boolean =
    if doc1.size() != doc2.size() then return false

    doc1.keySet().asScala.forall { key =>
      doc2.containsKey(key) && bsonValuesEqual(doc1.get(key), doc2.get(key))
    }

  /** Test if two BSON values are equal, recursively handling documents and arrays.
    *
    * This provides deep equality checking:
    *   - Documents are compared field-by-field, ignoring order
    *   - Arrays are compared element-by-element in order
    *   - Other values use standard equality
    *
    * @param v1
    *   First value
    * @param v2
    *   Second value
    * @return
    *   true if values are equal
    */
  def bsonValuesEqual(v1: BsonValue, v2: BsonValue): Boolean =
    (v1, v2) match
      case (d1: BsonDocument, d2: BsonDocument)             => bsonEquivalent(d1, d2)
      case (a1: org.bson.BsonArray, a2: org.bson.BsonArray) => bsonArraysEqual(a1, a2)
      case _                                                => v1 == v2

  /** Test if two BSON arrays are equal (ordered comparison).
    *
    * Arrays must have the same length and elements in the same order. Elements are compared recursively (handling nested documents/arrays).
    *
    * @param arr1
    *   First array
    * @param arr2
    *   Second array
    * @return
    *   true if arrays are equal
    */
  def bsonArraysEqual(arr1: org.bson.BsonArray, arr2: org.bson.BsonArray): Boolean =
    if arr1.size() != arr2.size() then return false

    arr1.asScala.zip(arr2.asScala).forall { case (v1, v2) =>
      bsonValuesEqual(v1, v2)
    }

  /** Test if two BSON arrays are equivalent ignoring element order.
    *
    * This is useful when array order is not significant (e.g., sets encoded as arrays). Each element in arr1 must have a matching element
    * in arr2 and vice versa.
    *
    * Note: This is O(n²) - use sparingly for large arrays.
    *
    * @param arr1
    *   First array
    * @param arr2
    *   Second array
    * @return
    *   true if arrays contain the same elements regardless of order
    */
  def bsonArraysEquivalent(arr1: org.bson.BsonArray, arr2: org.bson.BsonArray): Boolean =
    if arr1.size() != arr2.size() then return false

    val list1 = arr1.asScala.toList
    val list2 = arr2.asScala.toList

    list1.forall { elem1 =>
      list2.exists(elem2 => bsonValuesEqual(elem1, elem2))
    } && list2.forall { elem2 =>
      list1.exists(elem1 => bsonValuesEqual(elem1, elem2))
    }
  end bsonArraysEquivalent

  /** Test that a BSON document deeply contains expected values.
    *
    * This performs recursive comparison:
    *   - For documents: all expected fields must match (extra fields allowed)
    *   - For arrays: elements are compared in order
    *   - For scalars: values must be equal
    *
    * @param actual
    *   The actual BSON document
    * @param expected
    *   Map of expected field paths to values
    * @return
    *   true if all expected values are present
    */
  def bsonDeepContains(actual: BsonDocument, expected: Map[String, BsonValue]): Boolean =
    expected.forall { case (path, expectedValue) =>
      val actualValue = getNestedField(actual, path)
      actualValue != null && bsonValuesEqual(actualValue, expectedValue)
    }

  /** Get a nested field from a BSON document using dot notation.
    *
    * @param doc
    *   The document to query
    * @param path
    *   Dot-separated field path (e.g., "address.city")
    * @return
    *   The value at the path, or null if not found
    */
  private def getNestedField(doc: BsonDocument, path: String): BsonValue =
    val parts = path.split("\\.").toList

    @scala.annotation.tailrec
    def navigate(current: BsonValue, remaining: List[String]): BsonValue =
      remaining match
        case Nil => current
        case head :: tail =>
          current match
            case d: BsonDocument =>
              val next = d.get(head)
              if next == null then null else navigate(next, tail)
            case _ => null

    navigate(doc, parts)
  end getNestedField

  /** Create a minimal CodecRegistry for testing with only the given codecs.
    *
    * @param codecs
    *   The codecs to include in the registry
    * @return
    *   A CodecRegistry containing only the specified codecs
    */
  def testRegistry(codecs: Codec[?]*): CodecRegistry =
    org.bson.codecs.configuration.CodecRegistries.fromCodecs(codecs*)

  // ===== Pretty-printing and Diff Helpers =====

  /** Pretty-print a BSON document for readable error messages.
    *
    * @param doc
    *   The document to print
    * @return
    *   A formatted string representation
    */
  def prettyPrint(doc: BsonDocument): String =
    prettyPrintValue(doc, indent = 0)

  private def prettyPrintValue(value: BsonValue, indent: Int): String =
    val indentStr = "  " * indent
    value match
      case doc: BsonDocument =>
        if doc.isEmpty then "{}"
        else
          val fields = doc
            .keySet()
            .asScala
            .map { key =>
              s"$indentStr  $key: ${prettyPrintValue(doc.get(key), indent + 1)}"
            }
            .mkString(",\n")
          s"{\n$fields\n$indentStr}"
      case arr: org.bson.BsonArray =>
        if arr.isEmpty then "[]"
        else
          val elements = arr.asScala.map(v => prettyPrintValue(v, indent + 1)).mkString(", ")
          s"[$elements]"
      case _ => value.toString
    end match
  end prettyPrintValue

  /** Generate a diff between expected and actual BSON documents.
    *
    * Performs deep comparison and reports:
    *   - Missing fields (in expected but not actual)
    *   - Extra fields (in actual but not expected)
    *   - Mismatched values (including nested documents and arrays)
    *
    * @param expected
    *   Expected document
    * @param actual
    *   Actual document
    * @return
    *   A string describing the differences
    */
  def diff(expected: BsonDocument, actual: BsonDocument): String =
    val expectedKeys = expected.keySet().asScala.toSet
    val actualKeys = actual.keySet().asScala.toSet

    val missing = expectedKeys diff actualKeys
    val extra = actualKeys diff expectedKeys
    val common = expectedKeys intersect actualKeys

    val mismatched = common.filter { key =>
      !bsonValuesEqual(expected.get(key), actual.get(key))
    }

    val parts = List(
      if missing.nonEmpty then Some(s"Missing fields: ${missing.mkString(", ")}") else None,
      if extra.nonEmpty then Some(s"Extra fields: ${extra.mkString(", ")}") else None,
      if mismatched.nonEmpty then
        Some(s"Mismatched fields:\n${mismatched
            .map { key =>
              val exp = expected.get(key)
              val act = actual.get(key)
              s"  $key:\n    expected: ${prettyPrintValue(exp, 2)}\n    actual:   ${prettyPrintValue(act, 2)}"
            }
            .mkString("\n")}")
      else None
    ).flatten

    if parts.isEmpty then "No differences"
    else parts.mkString("\n")
  end diff

  /** Generate a deep diff showing all differences in nested structures.
    *
    * This recursively compares documents, arrays, and values, reporting differences at all levels with full paths.
    *
    * @param expected
    *   Expected document
    * @param actual
    *   Actual document
    * @param path
    *   Current path (for recursive calls)
    * @return
    *   List of difference descriptions
    */
  def deepDiff(expected: BsonDocument, actual: BsonDocument, path: String = ""): List[String] =
    deepDiffValues(expected, actual, path)

  private def deepDiffValues(expected: BsonValue, actual: BsonValue, path: String): List[String] =
    (expected, actual) match
      case (e: BsonDocument, a: BsonDocument) =>
        val allKeys = (e.keySet().asScala ++ a.keySet().asScala).toSet
        allKeys.flatMap { key =>
          val newPath = if path.isEmpty then key else s"$path.$key"
          val eVal = e.get(key)
          val aVal = a.get(key)

          if eVal == null && aVal != null then List(s"$newPath: missing in expected (found: ${aVal.getBsonType})")
          else if eVal != null && aVal == null then List(s"$newPath: missing in actual")
          else if eVal != null && aVal != null then deepDiffValues(eVal, aVal, newPath)
          else List.empty
        }.toList

      case (e: org.bson.BsonArray, a: org.bson.BsonArray) =>
        if e.size() != a.size() then List(s"$path: array size mismatch (expected: ${e.size()}, actual: ${a.size()})")
        else
          e.asScala
            .zip(a.asScala)
            .zipWithIndex
            .flatMap { case ((eElem, aElem), idx) =>
              deepDiffValues(eElem, aElem, s"$path[$idx]")
            }
            .toList

      case _ =>
        if expected != actual then List(s"$path: ${expected.getBsonType} mismatch (expected: $expected, actual: $actual)")
        else List.empty

  // ===== Property-Based Testing Helpers =====

  /** Helper for ScalaCheck property-based testing.
    *
    * Creates a property that tests codec symmetry for generated values.
    *
    * @example
    *   {{{ import org.scalacheck.Prop.forAll property("codec symmetry") = forAll { (value: MyType) =>
    *   CodecTestKit.codecSymmetryProperty(value) } }}}
    *
    * @param value
    *   The value to test
    * @param codec
    *   The codec to test
    * @return
    *   true if symmetry holds
    */
  def codecSymmetryProperty[T](value: T)(using codec: Codec[T]): Boolean =
    checkCodecSymmetry(value).isRight

  /** Helper for ScalaCheck that returns detailed failure information.
    *
    * @param value
    *   The value to test
    * @param codec
    *   The codec to test
    * @return
    *   Right(true) if symmetry holds, Left(error) otherwise
    */
  def codecSymmetryPropertyVerbose[T](value: T)(using codec: Codec[T]): Either[String, Boolean] =
    checkCodecSymmetry(value).map(_ => true)

  /** Create a ScalaCheck property that tests codec symmetry.
    *
    * This is a convenience method that can be used directly in ScalaCheck properties.
    *
    * @param f
    *   Function that generates a value and codec
    * @return
    *   true if all generated values maintain symmetry
    */
  def forAllCodecs[T](f: => (T, Codec[T])): Boolean =
    val (value, codec) = f
    given Codec[T] = codec
    codecSymmetryProperty(value)

end CodecTestKit
