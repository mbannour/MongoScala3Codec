# Supported-type audit (internal)

Internal working document, not product documentation. It records what MongoScala3Codec
**does** today, measured from tests, so that the public docs can be corrected deliberately
rather than by guesswork. Do not publish it or copy its wording into the README.

Evidence: `SupportedTypeContractSpec`, plus the compatibility specs named per row.

## Where support is decided

Derivation owns a fixed set of field shapes and delegates everything else:

- `CaseClassBsonWriter.writeCaseClassDataImpl` — compile-time match on the field type:
  `String, Int, Double, Float, Boolean, Long, Byte, Short, Char, java.util.UUID,
  Option[T], Map[String, V], Iterable[T]`, then a fallback that emits a **runtime**
  `registry.get(class)`.
- `CaseClassCodecGenerator.readValue` — runtime match on `BsonType` plus the target class,
  with the same fallback to `getCodec(class)`.
- `CaseClassFieldMapper.flattenTypeArgs` — rejects non-`String` map keys at compile time.
- `ClassToCaseFlagMap.collectFieldTypes` — walks field types to build a class/case-class flag map.

The fallback is the extension point: a user or driver `Codec[T]` in the registry is used as-is.
It is also why an unsupported field type compiles and only fails on first encode.

## Matrix

Dimensions: C = compile/derive, E = encode, D = decode, R = round trip, G = exact golden BSON.

| Type / shape | Status | C | E | D | R | G | Notes |
|---|---|---|---|---|---|---|---|
| `String`, `Boolean`, `Int`, `Long`, `Double`, `Float`, `Byte`, `Short`, `Char` | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | `Char`→Int32 code point; `Float`→Double; `Byte`/`Short`→Int32 |
| `ObjectId` | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | Driver codec; also covered by `BsonIdCompatibilitySpec` |
| `java.util.UUID` | SUPPORTED WITH LIMITATIONS | ✓ | ✓ | ✓ | ✓ | ✓ | Written as a **BSON string** by derivation, not Binary subtype 4. Public docs claim Binary. |
| `Option[T]`, T supported | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | `None`→explicit null under default `NoneHandling.Encode`; `OptionCompatibilitySpec` |
| `List[T]`, `Seq[T]`, `Vector[T]`, `Set[T]` | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | BSON array; empty→`[]`; `Set` loses order |
| `Map[String, V]` | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | Embedded document |
| `Map[K, V]`, K ≠ String | UNSUPPORTED | ✗ | - | - | - | - | Controlled compile error naming the field and class |
| `Array[Byte]` | SUPPORTED WITH LIMITATIONS | ✓ | ✓ | - | - | ✓ | Driver `ByteArrayCodec`, Binary subtype 00. Decode/round trip not covered. |
| `Array[T]`, T ≠ Byte | UNSUPPORTED | ✓ | ✗ | - | - | - | Compiles, then `IllegalArgumentException: No codec found` on encode |
| `java.math.BigDecimal` | SUPPORTED WITH LIMITATIONS | ✓ | ✓ | - | - | ✓ | Driver codec, Decimal128. Decode/round trip not covered. |
| `scala.math.BigDecimal` | UNSUPPORTED | ✓ | ✗ | - | - | - | Compiles, then `No codec found for type: scala.math.BigDecimal`, even with all driver providers |
| Nested case class | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | `NestedCaseClassCompatibilitySpec` |
| Constructor defaults | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | Default applies when the field is absent; `ConstructorDefaultCompatibilitySpec` |
| Custom / driver `Codec[T]` | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | Registry codec wins over deriving one, even for a case class |
| Sealed ADT | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | Tasks 10–15; see below |
| Scala 3 enum | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | Via `EnumValueCodecProvider`, string or ordinal |
| Recursive `Option[Self]` | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | `RecursiveCompatibilitySpec` |
| Recursive `List[Self]` | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | Same |
| Mutual recursion | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | Both types must be registered |
| Recursive sealed ADT | SUPPORTED | ✓ | ✓ | ✓ | ✓ | ✓ | Composes with `@BsonDiscriminator` |
| Generic case class, e.g. `Box[String]` | UNSUPPORTED | ✗ | - | - | - | - | Compile error, but it names the **field** (`value`), not the type |
| Tuple field | UNSUPPORTED | ✗ | - | - | - | - | Compile error naming the field (`_1`). A well-worded tuple message exists in `CaseClassFieldMapper` but is unreachable. |
| Unwrapped self-reference `X(next: X)` | UNSUPPORTED | ✗ | - | - | - | - | `StackOverflowError` in macro expansion. Type is uninhabitable in Scala. |
| Path-dependent type `c.T` | UNSUPPORTED | ✗ | - | - | - | - | Controlled compile error; Task 12 |
| `Either[L, R]` | NOT PART OF V1.0 CONTRACT | ✓ | ✗ | - | - | - | Compiles, then `No codec found for sealed trait: scala.util.Either` |
| Cyclic runtime object graph | NOT PART OF V1.0 CONTRACT | - | - | - | - | - | BSON documents are finite trees; no identity or `$ref` handling |
| `Function`, `Future`, effect types, streams | NOT PART OF V1.0 CONTRACT | - | - | - | - | - | Not investigated; no intentional support |
| Opaque types / type aliases | NOT PART OF V1.0 CONTRACT | - | - | - | - | - | No evidence of intentional support |

## Public documentation vs. measured behavior

`docs/BSON_TYPE_MAPPING.md` currently over-promises. Correct before v1.0:

| Doc claim | Measured |
|---|---|
| `BigDecimal` → Decimal128, "✅ Full" | True for `java.math.BigDecimal`; `scala.math.BigDecimal` fails at encode |
| `java.util.UUID` → Binary subtype 4, "✅ Full" | Written as a BSON **string** |
| `Map[K, V]` non-String keys → "Array of pairs, ✅ Full" | Rejected at compile time |
| `Array[Byte]` → Binary, "✅ Full" | Correct |
| `List`/`Seq`/`Vector`/`Set`/`Map[String, T]` | Correct |
| `Either[L, R]` → "❌ Not supported" | Correct |

## Known diagnostic defects

1. `ClassToCaseFlagMap.collectFieldTypes` builds field types from `sym.termRef`, a **term**
   reference, so `tpe.show` prints the field's name. Unsupported field types therefore report
   `Cannot summon ClassTag for type: value` / `: _1` instead of naming the type.
2. Because that walker aborts first, the deliberate tuple message in
   `CaseClassFieldMapper.flattenTypeArgs` ("Tuple types are not supported in BSON
   serialization", with a suggestion) is dead code.
3. A field type with no codec compiles and fails on first encode. Acceptable for genuinely
   pluggable types, since the registry is a runtime value, but it is why `scala.math.BigDecimal`
   and `Array[String]` surface late.
