package io.github.mbannour.mongo.codecs

import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.CodecRegistry

/** Testing utilities for BSON codecs.
  *
  * Provides helper methods for testing codec symmetry and round-trip encoding/decoding.
  */
object CodecTestKit:

  /** Perform a round-trip encode/decode operation.
    *
    * Encodes the value to BSON and then decodes it back, verifying that the codec
    * can correctly serialize and deserialize the data.
    *
    * @param value The value to round-trip
    * @param codec The codec to use
    * @return The decoded value after round-tripping
    */
  def roundTrip[T](value: T)(using codec: Codec[T]): T =
    val doc = toBsonDocument(value)
    fromBsonDocument[T](doc)

  /** Convert a value to a BsonDocument using the given codec.
    *
    * @param value The value to encode
    * @param codec The codec to use for encoding
    * @return The encoded BsonDocument
    */
  def toBsonDocument[T](value: T)(using codec: Codec[T]): BsonDocument =
    val doc = new BsonDocument()
    val writer = new BsonDocumentWriter(doc)
    codec.encode(writer, value, EncoderContext.builder().build())
    doc

  /** Decode a BsonDocument to a value using the given codec.
    *
    * @param doc The BsonDocument to decode
    * @param codec The codec to use for decoding
    * @return The decoded value
    */
  def fromBsonDocument[T](doc: BsonDocument)(using codec: Codec[T]): T =
    val reader = new BsonDocumentReader(doc)
    codec.decode(reader, DecoderContext.builder().build())

  /** Assert that a codec maintains symmetry (encode then decode yields original value).
    *
    * This is useful in property-based testing to verify codec correctness.
    *
    * @param value The value to test
    * @param codec The codec to test
    * @throws AssertionError if the round-trip does not preserve the value
    */
  def assertCodecSymmetry[T](value: T)(using codec: Codec[T]): Unit =
    val result = roundTrip(value)
    assert(
      result == value,
      s"Codec symmetry violation: round-trip changed value from $value to $result"
    )

  /** Test that encoding and decoding produces the expected BSON structure.
    *
    * @param value The value to encode
    * @param expectedBson The expected BSON document structure
    * @param codec The codec to use
    * @throws AssertionError if the encoded value doesn't match expectations
    */
  def assertBsonStructure[T](value: T, expectedBson: BsonDocument)(using codec: Codec[T]): Unit =
    val actual = toBsonDocument(value)
    assert(
      actual == expectedBson,
      s"BSON structure mismatch:\nExpected: $expectedBson\nActual:   $actual"
    )

  /** Create a minimal CodecRegistry for testing with only the given codecs.
    *
    * @param codecs The codecs to include in the registry
    * @return A CodecRegistry containing only the specified codecs
    */
  def testRegistry(codecs: Codec[?]*): CodecRegistry =
    org.bson.codecs.configuration.CodecRegistries.fromCodecs(codecs*)
end CodecTestKit

