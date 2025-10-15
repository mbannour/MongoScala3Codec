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

  /** Helper method to set None handling to Ignore - fluent API for functional configuration.
    *
    * @example
    *   {{{
    *   builder.configure(_.withIgnoreNone)
    *   }}}
    */
  def withIgnoreNone: CodecConfig = copy(noneHandling = NoneHandling.Ignore)

  /** Helper method to set None handling to Encode - fluent API for functional configuration.
    *
    * @example
    *   {{{
    *   builder.configure(_.withEncodeNone)
    *   }}}
    */
  def withEncodeNone: CodecConfig = copy(noneHandling = NoneHandling.Encode)

  /** Helper method to set discriminator field - fluent API for functional configuration.
    *
    * @param field
    *   The field name to use for type discriminators
    * @example
    *   {{{
    *   builder.configure(_.withDiscriminator("_type"))
    *   }}}
    */
  def withDiscriminator(field: String): CodecConfig = copy(discriminatorField = field)
end CodecConfig

/** Strategy for handling `None` values in `Option` fields during BSON encoding.
  */
enum NoneHandling:
  /** Encode `None` as BSON `null`. */
  case Encode

  /** Omit fields with `None` values from the BSON document. */
  case Ignore
end NoneHandling
