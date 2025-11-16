# MongoScala3Codec Documentation

**Complete guide to the most powerful MongoDB codec library for Scala 3**

---

## 💡 Why This Library Exists

**MongoScala3Codec was created to enable native MongoDB usage in Scala 3.** The official `mongo-scala-driver` only supports Scala 2.11, 2.12, and 2.13 because it relies on **Scala 2 macros** for automatic codec generation. Since Scala 3 completely redesigned the macro system, **the official driver cannot work with Scala 3** without a major rewrite.

**Without this library, Scala 3 users must:**
- Downgrade to Scala 2.13 (losing union types, opaque types, better enums, etc.)
- Use the Java driver directly (losing type safety and writing manual codecs)
- Wait indefinitely for official Scala 3 support

**MongoScala3Codec provides:**
- ✅ Native Scala 3 macro-based codec generation
- ✅ Full BSON type support with compile-time safety
- ✅ Scala 3 enum support with string/ordinal/custom field encoding
- ✅ Type-safe field paths (MongoPath) - unique in the ecosystem
- ✅ Production-grade error messages and 290+ tests

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

### 🎯 Advanced Topics

| Document | For When You Need... |
|----------|---------------------|
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
| **[INTERNAL_ANALYSIS.md](INTERNAL_ANALYSIS.md)** | Architecture deep-dive (internal) |

---

## 🎓 Learning Paths

### Path 1: **Quick Start** (For First-Time Users)
1. ✅ [QUICKSTART.md](QUICKSTART.md) - 5-minute example
2. ✅ [FEATURES.md](FEATURES.md) - Explore capabilities
3. ✅ [FAQ.md](FAQ.md) - Common questions

### Path 2: **Production Deployment** (For Teams)
1. ✅ [QUICKSTART.md](QUICKSTART.md) - Proof of concept
2. ✅ [MONGODB_INTEROP.md](MONGODB_INTEROP.md) - Driver integration
3. ✅ [BSON_TYPE_MAPPING.md](BSON_TYPE_MAPPING.md) - Type coverage
4. ✅ [MIGRATION.md](MIGRATION.md) - Migration strategy
5. ✅ [BENCHMARKS.md](BENCHMARKS.md) - Performance validation

### Path 3: **Advanced Features** (For Power Users)
1. ✅ [ENUM_SUPPORT.md](ENUM_SUPPORT.md) - Enum handling
2. ✅ [HOW_IT_WORKS.md](HOW_IT_WORKS.md) - Internals
3. ✅ [CONTRIBUTING.md](../CONTRIBUTING.md) - Extend the library

---

## 🔍 Quick Reference

### Most Common Tasks

| Task | Document | Section |
|------|----------|---------|
| Install library | [README.md](../README.md) | Installation |
| First example | [QUICKSTART.md](QUICKSTART.md) | Complete |
| Register types | [FEATURES.md](FEATURES.md) | Registering Codecs |
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
- **Last Updated:** November 2025

---

**💡 Pro Tip:** Bookmark this page for quick navigation!
