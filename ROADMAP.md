# MongoScala3Codec - Roadmap to Excellence

This document outlines the features and enhancements needed to make MongoScala3Codec an excellent library.

## Current Status ✅

The library already has a strong foundation with:
- ✅ Automatic BSON codec generation for case classes
- ✅ Type-safe configuration with `CodecConfig`
- ✅ Testing utilities with `CodecTestKit`
- ✅ **Full opaque type support** (zero runtime overhead)
- ✅ Scala 3 enum support (plain enums)
- ✅ Concrete sealed trait implementations support
- ✅ Collections support (List, Set, Vector, Map)
- ✅ Optional fields with flexible None handling
- ✅ Custom field annotations (`@BsonProperty`)
- ✅ Compile-time safety and validation
- ✅ Comprehensive test coverage (89 tests passing)

---

## Missing Features for Excellence

### 1. 🔴 **HIGH PRIORITY: Polymorphic Sealed Trait Fields**

**Current Limitation:**  
Fields typed as sealed traits (e.g., `status: PaymentStatus`) are **NOT supported**. Only concrete implementations work.

**Example of What's Missing:**
```scala
sealed trait PaymentStatus
case class Pending(timestamp: Long) extends PaymentStatus
case class Completed(timestamp: Long, transactionId: String) extends PaymentStatus

// ❌ NOT SUPPORTED - polymorphic field
case class Transaction(_id: ObjectId, status: PaymentStatus, amount: Double)

// ✅ WORKAROUND - concrete type
case class Transaction(_id: ObjectId, status: Completed, amount: Double)
```

**Why It's Important:**
- Essential for domain modeling with algebraic data types (ADTs)
- Common pattern in Scala applications
- Enables true polymorphism in data models
- Reduces boilerplate (no need for separate collections per type)

**Implementation Requirements:**
1. Add discriminator field support for sealed hierarchies
2. Implement runtime type resolution during decoding
3. Generate codecs that can handle all sealed trait children
4. Support configurable discriminator field names
5. Handle nested sealed traits

**Complexity:** High  
**Impact:** High - This is the most requested feature

**Suggested API:**
```scala
sealed trait Status
case class Active(since: Long) extends Status
case class Inactive(reason: String) extends Status

case class User(_id: ObjectId, name: String, status: Status)

given config: CodecConfig = CodecConfig(
  discriminatorField = "_type",  // Already exists
  discriminatorStrategy = DiscriminatorStrategy.ClassName  // New
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(config)
  .registerSealed[Status]  // New method - registers all children
  .register[User]
  .build

// BSON structure:
// {
//   "_id": ObjectId("..."),
//   "name": "Alice",
//   "status": {
//     "_type": "Active",
//     "since": 1234567890
//   }
// }
```

---

### 2. 🟡 **MEDIUM PRIORITY: Case Object Support in Sealed Hierarchies**

**Current Limitation:**  
Case objects as sealed trait members are not fully supported.

**Example of What's Missing:**
```scala
sealed trait Permission
case object Read extends Permission
case object Write extends Permission
case object Admin extends Permission

case class User(_id: ObjectId, name: String, permission: Permission)
// ❌ NOT SUPPORTED - case objects don't work
```

**Why It's Important:**
- Natural way to represent enums/simple variants in Scala
- Common in state machines and permission systems
- More idiomatic than empty case classes

**Implementation Requirements:**
1. Detect case objects at compile time
2. Serialize as string or discriminator value
3. Handle deserialization to singleton case objects
4. Support mixing case objects and case classes in sealed hierarchies

**Complexity:** Medium  
**Impact:** Medium - Useful but has workarounds

**Suggested API:**
```scala
sealed trait Status
case object Active extends Status
case object Inactive extends Status
case class Custom(reason: String) extends Status

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Status]  // Handles both case objects and case classes
  .build

// BSON structure for case object:
// { "status": "Active" }  // or { "status": { "_type": "Active" } }
```

---

### 3. 🟡 **MEDIUM PRIORITY: Custom Field Name Transformations**

**Current Limitation:**  
Only `@BsonProperty` annotation for individual fields. No global transformation strategies.

**Example of What's Missing:**
```scala
case class User(
  _id: ObjectId,
  firstName: String,    // Want: first_name
  lastName: String,     // Want: last_name
  emailAddress: String  // Want: email_address
)

// Current: Must annotate every field
case class User(
  _id: ObjectId,
  @BsonProperty("first_name") firstName: String,
  @BsonProperty("last_name") lastName: String,
  @BsonProperty("email_address") emailAddress: String
)
```

**Why It's Important:**
- Reduces boilerplate for snake_case/camelCase conversions
- Consistent naming conventions across the application
- Easier integration with existing MongoDB collections
- Follows DRY principle

**Implementation Requirements:**
1. Add `fieldNameMapper` function to `CodecConfig`
2. Support common strategies: snake_case, camelCase, PascalCase, kebab-case
3. Allow custom transformation functions
4. `@BsonProperty` should override global strategy
5. Maintain backward compatibility

**Complexity:** Medium  
**Impact:** Medium - Quality of life improvement

**Suggested API:**
```scala
enum FieldNamingStrategy:
  case CamelCase      // firstName
  case SnakeCase      // first_name
  case PascalCase     // FirstName
  case KebabCase      // first-name
  case Custom(f: String => String)

given config: CodecConfig = CodecConfig(
  fieldNamingStrategy = FieldNamingStrategy.SnakeCase
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(config)
  .register[User]
  .build

// firstName automatically becomes "first_name" in BSON
```

---

### 4. 🟡 **MEDIUM PRIORITY: Enhanced Enum Support**

**Current Limitation:**  
Only plain enums supported. No ADT-style enums with parameters.

**Example of What's Missing:**
```scala
// ✅ SUPPORTED - plain enum
enum Priority:
  case Low, Medium, High

// ❌ NOT SUPPORTED - parameterized enum
enum Result:
  case Success(value: String)
  case Failure(error: String)

// ❌ NOT SUPPORTED - enum with custom fields
enum Status:
  case Active
  case Inactive
  def isActive: Boolean = this == Active
```

**Why It's Important:**
- Scala 3 enums can be ADTs
- More expressive than plain enums
- Common pattern in Scala 3 codebases

**Implementation Requirements:**
1. Detect parameterized enum cases
2. Treat as sealed trait hierarchy
3. Generate appropriate codecs for each case
4. Support discriminator fields

**Complexity:** Medium (builds on sealed trait support)  
**Impact:** Low-Medium - Nice to have for Scala 3 purists

**Suggested API:**
```scala
enum Result:
  case Success(value: String)
  case Failure(error: String, code: Int)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerEnum[Result]  // New method for ADT enums
  .build
```

---

### 5. 🟢 **LOW PRIORITY: Performance Caching**

**Current Limitation:**  
Reflection-based operations happen on every codec invocation.

**What's Missing:**
```scala
// Cache expensive reflection operations
private val reflectionCache = ConcurrentHashMap[Class[?], FieldInfo]()
private val codecCache = ConcurrentHashMap[Class[?], Codec[?]]()
```

**Why It's Important:**
- Reduces overhead for repeated operations
- Better performance for high-throughput applications
- Minimizes GC pressure

**Implementation Requirements:**
1. Cache field metadata per class
2. Cache generated codecs
3. Thread-safe cache implementation
4. Configurable cache size/eviction
5. Benchmark before/after

**Complexity:** Low  
**Impact:** Low-Medium - Only matters at scale

**Suggested API:**
```scala
given config: CodecConfig = CodecConfig(
  enableCaching = true,
  maxCacheSize = 1000
)
```

---

### 6. 🟢 **LOW PRIORITY: Date/Time Handling Options**

**Current Limitation:**  
No configuration for how dates are stored.

**What's Missing:**
```scala
enum DateTimeStrategy:
  case Timestamp      // Store as Long (milliseconds)
  case ISOString      // Store as String ("2023-01-01T00:00:00Z")
  case BsonDateTime   // Store as BSON DateTime type

given config: CodecConfig = CodecConfig(
  dateTimeStrategy = DateTimeStrategy.Timestamp
)
```

**Why It's Important:**
- Different applications have different requirements
- MongoDB queries work differently for each format
- Legacy system integration

**Complexity:** Low  
**Impact:** Low - Most apps use default handling

---

### 7. 🟢 **LOW PRIORITY: Validation Hooks**

**Current Limitation:**  
No way to validate data during encoding/decoding.

**What's Missing:**
```scala
trait Validator[T]:
  def validate(value: T): Either[String, T]

case class Email(value: String)

given emailValidator: Validator[Email] with
  def validate(email: Email): Either[String, Email] =
    if email.value.contains("@") then Right(email)
    else Left("Invalid email format")

given config: CodecConfig = CodecConfig(
  enableValidation = true
)
```

**Why It's Important:**
- Catch invalid data at codec boundary
- Enforce business rules
- Better error messages

**Complexity:** Medium  
**Impact:** Low - Can be done outside codec layer

---

## Priority Summary

### Must Have (v1.0)
1. **Polymorphic Sealed Trait Fields** - Core ADT support
2. **Case Object Support** - Complete sealed trait support

### Should Have (v1.1)
3. **Custom Field Name Transformations** - DX improvement
4. **Enhanced Enum Support** - Scala 3 completeness

### Nice to Have (v1.2+)
5. **Performance Caching** - Optimization
6. **Date/Time Handling Options** - Flexibility
7. **Validation Hooks** - Data integrity

---

## Implementation Roadmap

### Phase 1: Core ADT Support (v0.1.0 → v1.0.0)
- [ ] Polymorphic sealed trait fields
- [ ] Case object support
- [ ] Sealed trait collections (`List[Status]`)
- [ ] Comprehensive tests for all sealed trait patterns
- [ ] Documentation and migration guide

**Estimated effort:** 3-4 weeks  
**Impact:** Unlocks full Scala 3 ADT power

### Phase 2: Developer Experience (v1.0.0 → v1.1.0)
- [ ] Custom field name transformations
- [ ] Built-in naming strategies (snake_case, etc.)
- [ ] Enhanced enum support (parameterized enums)
- [ ] Better error messages for sealed hierarchies

**Estimated effort:** 2-3 weeks  
**Impact:** Reduces boilerplate, improves usability

### Phase 3: Performance & Polish (v1.1.0 → v1.2.0)
- [ ] Reflection caching
- [ ] Date/time handling options
- [ ] Validation hooks
- [ ] Performance benchmarks
- [ ] Optimization guide

**Estimated effort:** 2 weeks  
**Impact:** Production-ready performance

---

## How to Contribute

1. **Pick a feature** from the roadmap
2. **Open an issue** to discuss the approach
3. **Create a PR** with tests and documentation
4. **Update this roadmap** when features are completed

---

## Success Metrics

When can we say MongoScala3Codec is "excellent"?

- ✅ All common Scala 3 patterns supported (ADTs, enums, opaque types)
- ✅ Zero or minimal boilerplate required
- ✅ Excellent error messages
- ✅ Comprehensive documentation with examples
- ✅ >95% test coverage
- ✅ Performance comparable to hand-written codecs
- ✅ Active community and contributions
- ✅ Used in production by multiple companies

---

## References

- [Scala 3 Book - Algebraic Data Types](https://docs.scala-lang.org/scala3/book/types-adts-gadts.html)
- [MongoDB BSON Specification](http://bsonspec.org/)
- [Circe Codec Derivation](https://circe.github.io/circe/codec.html) - Similar concepts
- [Tapir Validation](https://tapir.softwaremill.com/en/latest/endpoint/validation.html) - Validation patterns

---

## Version History

- **v0.0.6** (Current) - Stable foundation with opaque types, testing utilities, type-safe config
- **v1.0.0** (Target) - Full sealed trait support, case objects
- **v1.1.0** (Target) - Field transformations, enhanced enums
- **v1.2.0** (Target) - Performance optimization, validation hooks

---

*Last updated: 2025-10-15*
