package io.github.mbannour.mongo.codecs

/** Configuration for BSON codec generation behavior.
  *
  * This configuration object encapsulates all codec generation options, making the API more type-safe and extensible than using boolean
  * flags.
  *
  * @param noneHandling
  *   Strategy for handling `None` values in `Option` fields.
  * @param discriminatorField
  *   Field name used to store type discriminators for sealed hierarchies (default: "_t").
  *
  * ===Example Usage===
  * {{{
  *   val config = CodecConfig(
  *     noneHandling = NoneHandling.Ignore,
  *     discriminatorField = "_type"
  *   )
  * }}}
  */
case class CodecConfig(
    noneHandling: NoneHandling = NoneHandling.Encode,
    discriminatorField: String = "_t"
):
  /** Returns true if None values should be encoded as BSON null. */
  def shouldEncodeNone: Boolean = noneHandling == NoneHandling.Encode

/** Strategy for handling `None` values in `Option` fields during BSON encoding.
  */
enum NoneHandling:
  /** Encode `None` as BSON `null`. */
  case Encode

  /** Omit fields with `None` values from the BSON document. */
  case Ignore
end NoneHandling

