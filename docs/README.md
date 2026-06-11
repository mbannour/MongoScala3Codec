# MongoScala3Codec Documentation

**Complete guide to the most powerful MongoDB codec library for Scala 3**

---

## 💡 Why This Library Exists

**MongoScala3Codec began as the only way to derive BSON codecs on Scala 3.** That gap closed in `mongo-scala-driver` **5.7**, which added a native `scala.quoted` codec macro. The library has since repositioned as the **type-safe ergonomics layer on top of the official driver** — it emits plain `Bson` and standard `Codec`s, so it composes with the driver rather than replacing it.

**What the official 5.7+ driver gives you, and where it stops:**
- ✅ Compile-time codecs for case classes and sealed hierarchies on Scala 3
- ❌ Queries, updates, sorts, and aggregation are still **string-based** (`Filters.eq("address.city", v)`)
- ❌ Polymorphism is a **fixed `_t` discriminator** with the simple class name — no configurable field or strategy
- ❌ Scala 3 `enum` fields throw `CodecConfigurationException` at runtime unless you hand-write a codec *(verified against 5.7.0)*

**MongoScala3Codec adds on top of that:**
- ✅ Type-safe field paths (MongoPath) — `field[T](_.address.?.city)`, respects `@BsonProperty`
- ✅ Type-safe query/update DSL — `field[T](_.f) === value`, `Update.combine(...)`, `Sort.combine(...)`
- ✅ Type-safe aggregation builders — `Stage`/`Expr`/`Accumulator`
- ✅ Scala 3 enum codecs with string/ordinal/custom field encoding
- ✅ Sealed trait/class support with **configurable** discriminators (field name + SimpleName/FQN/Custom)
- ✅ Fluent `RegistryBuilder` instead of manual `fromProviders(classOf[…])` wiring
- ✅ DSL testkit — assertion helpers for testing DSL-produced BSON without MongoDB
- ✅ Production-grade error messages and 300+ tests

---

## 📚 Documentation Map

### 🚀 Getting Started (5 minutes)

| Document | Purpose | Time |
|----------|---------|------|
| **[QUICKSTART.md](QUICKSTART.md)** | Copy-paste working example | 5 min |
| **[../README.md](../README.md)** | Overview & installation | 3 min |

**Start here:** [QUICKSTART.md](QUICKSTART.md) - Get running in 5 minutes

---

### 📖 Core Features

| Document | What You'll Learn |
|----------|-------------------|
| **[FEATURES.md](FEATURES.md)** | Complete feature catalog with examples |
| **[BSON_TYPE_MAPPING.md](BSON_TYPE_MAPPING.md)** | 35+ supported types & BSON mappings |
| **[MONGODB_INTEROP.md](MONGODB_INTEROP.md)** | MongoDB driver integration & CRUD operations |

---

### 🔍 Type-Safe Query DSL

| Document | What You'll Learn |
|----------|-------------------|
| **[DSL.md](DSL.md)** | Complete DSL reference — `field[T]`, filters, updates, sort, projection, `BsonEncoder` |
| **[REPOSITORY.md](REPOSITORY.md)** | Type-safe, effect-agnostic `MongoRepository[F, Doc, Id]` CRUD layer |
| **[DSL.md — Testing](DSL.md#testing-with-dsltestkit)** | `DslTestKit` assertion helpers for unit-testing DSL expressions |

---

### 🎯 Advanced Topics

| Document | For When You Need... |
|----------|---------------------|
| **[SEALED_TRAIT_SUPPORT.md](SEALED_TRAIT_SUPPORT.md)** | Polymorphic codecs with sealed traits/classes (New in 0.0.8) |
| **[ENUM_SUPPORT.md](ENUM_SUPPORT.md)** | Scala 3 enums (string/ordinal/custom) |
| **[HOW_IT_WORKS.md](HOW_IT_WORKS.md)** | Macro internals & optimization techniques |

---

### 🔧 Migration & Troubleshooting

| Document | Use Case |
|----------|----------|
| **[MIGRATION.md](MIGRATION.md)** | Migrate from other MongoDB Scala libraries |
| **[FAQ.md](FAQ.md)** | Common questions & troubleshooting |

---

### ⚡ Performance

| Document | Focus |
|----------|-------|
| **[BENCHMARKS.md](BENCHMARKS.md)** | JMH benchmarks & performance testing |
| **[../benchmarks/README.md](../benchmarks/README.md)** | Running benchmarks locally |

---

### 🤝 Contributing

| Document | Audience |
|----------|----------|
| **[../CONTRIBUTING.md](../CONTRIBUTING.md)** | Contributors & maintainers |

---

## 🎓 Learning Paths

### Path 1: **Quick Start** (For First-Time Users)
1. ✅ [QUICKSTART.md](QUICKSTART.md) - 5-minute example with DSL intro
2. ✅ [FEATURES.md](FEATURES.md) - Explore capabilities
3. ✅ [FAQ.md](FAQ.md) - Common questions

### Path 2: **Type-Safe Queries** (For DSL Users)
1. ✅ [QUICKSTART.md](QUICKSTART.md) - Install codec + DSL
2. ✅ [DSL.md](DSL.md) - Full filter/update/sort/projection reference
3. ✅ [DSL.md — Testing](DSL.md#testing-with-dsltestkit) - Test your queries with DslTestKit

### Path 3: **Production Deployment** (For Teams)
1. ✅ [QUICKSTART.md](QUICKSTART.md) - Proof of concept
2. ✅ [DSL.md](DSL.md) - Type-safe query layer
3. ✅ [MONGODB_INTEROP.md](MONGODB_INTEROP.md) - Driver integration
4. ✅ [BSON_TYPE_MAPPING.md](BSON_TYPE_MAPPING.md) - Type coverage
5. ✅ [MIGRATION.md](MIGRATION.md) - Migration strategy
6. ✅ [BENCHMARKS.md](BENCHMARKS.md) - Performance validation

### Path 4: **Advanced Features** (For Power Users)
1. ✅ [SEALED_TRAIT_SUPPORT.md](SEALED_TRAIT_SUPPORT.md) - Polymorphic codecs
2. ✅ [ENUM_SUPPORT.md](ENUM_SUPPORT.md) - Enum handling
3. ✅ [HOW_IT_WORKS.md](HOW_IT_WORKS.md) - Internals
4. ✅ [CONTRIBUTING.md](../CONTRIBUTING.md) - Extend the library

---

## 🔍 Quick Reference

### Most Common Tasks

| Task | Document | Section |
|------|----------|---------|
| Install library | [README.md](../README.md) | Installation |
| First example | [QUICKSTART.md](QUICKSTART.md) | Complete |
| Register types | [FEATURES.md](FEATURES.md) | Registering Codecs |
| Build type-safe filters | [DSL.md](DSL.md) | Filter Operators |
| Build type-safe updates | [DSL.md](DSL.md) | Update Operators |
| Sort and project results | [DSL.md](DSL.md) | Sort / Projection |
| Add a custom encoder type | [DSL.md](DSL.md) | BsonEncoder |
| Test DSL expressions | [DSL.md](DSL.md) | Testing with DslTestKit |
| Sealed traits | [SEALED_TRAIT_SUPPORT.md](SEALED_TRAIT_SUPPORT.md) | Quick Start |
| Handle enums | [ENUM_SUPPORT.md](ENUM_SUPPORT.md) | Quick Start |
| None handling | [FEATURES.md](FEATURES.md) | Optional Fields |
| Custom fields | [FEATURES.md](FEATURES.md) | @BsonProperty |
| Type-safe paths | [MONGODB_INTEROP.md](MONGODB_INTEROP.md) | MongoPath |
| Performance | [BENCHMARKS.md](BENCHMARKS.md) | Running Benchmarks |
| Troubleshooting | [FAQ.md](FAQ.md) | Complete |

---

## ❓ Still Have Questions?

1. **Check:** [FAQ.md](FAQ.md) - Covers 90% of common questions
2. **Search:** Use GitHub search across all docs
3. **Ask:** [GitHub Discussions](https://github.com/mbannour/MongoScala3Codec/discussions)
4. **Report:** [GitHub Issues](https://github.com/mbannour/MongoScala3Codec/issues)

---

## 📊 Documentation Stats

- **Total Pages:** 50+
- **Code Examples:** 200+
- **Coverage:** All features documented
- **Last Updated:** January 2026

---

**💡 Pro Tip:** Bookmark this page for quick navigation!
