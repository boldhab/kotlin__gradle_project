# Future Enhancements & Architectural Roadmap

> **Notice**: The enhancements and features documented in this file represent **proposed future work**. They are not implemented in the current release and serve as an engineering roadmap for future iterations of the Library Management System.

---

## 1. Current System Limitations

The current release satisfies the core educational requirements of the assignment but possesses several technical limitations:

1. **In-Memory Volatility**:
   - All catalog and patron data reside in transient `LinkedHashMap` collections within [`InMemoryLibraryRepository`](file:///home/hab/Documents/projects/kotlin__gradle_project/app/src/main/kotlin/org/dbu/library/repository/InMemoryLibraryRepository.kt). Terminating the JVM immediately wipes all modifications and resets state to the default seed data.
2. **Hardcoded Business Policies**:
   - The borrowing ceiling is hardcoded to a fixed constant (`3`) directly inside [`DefaultLibraryService.borrowBook`](file:///home/hab/Documents/projects/kotlin__gradle_project/app/src/main/kotlin/org/dbu/library/service/DefaultLibraryService.kt#L27), preventing different limits for student vs. faculty patrons or seasonal adjustments.
3. **Single-Copy Inventory Constraint**:
   - The current data model equates a `Book` (metadata like ISBN, title, author) with a physical inventory item. If a library owns five copies of *"Clean Code"*, the system cannot distinguish individual copies or track multiple concurrent loans of the same title.
4. **Lack of Temporal Loan Tracking**:
   - There is no concept of checkout timestamps, due dates, loan renewals, or overdue fines.
5. **Console UI Visibility Gap**:
   - To preserve strict adherence to the starter specification (Options 1–6 and 0), the console interface does not expose a menu option to inspect registered patrons or review active loans.
6. **Thread Safety & Concurrency**:
   - The underlying `LinkedHashMap` instances are not synchronized. Multi-threaded operations would cause race conditions and potential data corruption.
7. **Automated Test Coverage**:
   - Only the boilerplate `AppTest.kt` exists. The repository and service layers currently lack automated JUnit 5 test suites.

---

## 2. Prioritized Improvement Matrix

| Priority | Enhancement | Category | Complexity | Value / Impact |
| :--- | :--- | :--- | :--- | :--- |
| **P0 (Critical)** | Automated Unit & Integration Testing Suite | Quality Assurance | Medium | Guarantees regression-free refactoring and validates business logic. |
| **P0 (Critical)** | Data Persistence (JSON / SQLite) | Architecture | Medium | Retains book and patron records across application restarts. |
| **P1 (High)** | Multi-Copy Inventory & Loan Entity | Domain Model | High | Supports multiple copies per ISBN with individual barcode tracking. |
| **P1 (High)** | Due Dates, Overdue Alerts & Fines | Business Logic | Medium | Enforces loan periods (e.g., 14 days) and tracks late returns. |
| **P1 (High)** | Configurable Borrowing Limits by Patron Tier | Business Logic | Low | Custom limits for Undergraduates (3), Graduates (5), Faculty (10). |
| **P2 (Medium)** | Administrative Submenu (Patron Management) | User Interface | Low | Enables viewing, searching, and managing patrons without polluting the main menu. |
| **P2 (Medium)** | Strict ISBN-10 / ISBN-13 Checksum Validation | Validation | Low | Prevents invalid ISBN entries using standard check-digit algorithms. |
| **P2 (Medium)** | Thread-Safe In-Memory / Cached Storage | Concurrency | Low | Migrates to `ConcurrentHashMap` or mutex-protected operations. |
| **P3 (Low)** | Rich CLI Table Formatting (Mordant / Text-Table) | User Interface | Low | Modernizes terminal output with formatted tables and color highlights. |
| **P3 (Low)** | REST API / Web Interface (Ktor or Spring Boot) | Integration | High | Exposes library services to web and mobile clients. |

---

## 3. Automated Testing Suite (Proposed Specification)

### 3.1 Unit Testing: `DefaultLibraryServiceTest`
Automated tests should be written using **JUnit Jupiter 5** and **MockK** (or a clean in-memory repository fake):

```kotlin
class DefaultLibraryServiceTest {

    private lateinit var repository: InMemoryLibraryRepository
    private lateinit var service: DefaultLibraryService

    @BeforeEach
    fun setUp() {
        repository = InMemoryLibraryRepository()
        service = DefaultLibraryService(repository)
        repository.addBook(Book("ISBN-1", "Kotlin in Action", "Dmitry Jemerov", 2017))
        repository.addPatron(Patron("P1", "Alice"))
    }

    @Test
    fun `borrowBook successfully loans available book`() {
        val result = service.borrowBook("P1", "ISBN-1")
        assertEquals(BorrowResult.SUCCESS, result)
        assertFalse(repository.findBook("ISBN-1")!!.isAvailable)
        assertTrue(repository.findPatron("P1")!!.borrowedBooks.contains("ISBN-1"))
    }

    @Test
    fun `borrowBook fails when book is not found`() {
        val result = service.borrowBook("P1", "NON_EXISTENT")
        assertEquals(BorrowResult.BOOK_NOT_FOUND, result)
    }

    @Test
    fun `borrowBook fails when patron is not found`() {
        val result = service.borrowBook("NON_EXISTENT", "ISBN-1")
        assertEquals(BorrowResult.PATRON_NOT_FOUND, result)
    }

    @Test
    fun `borrowBook fails when book is already borrowed`() {
        service.borrowBook("P1", "ISBN-1")
        val result = service.borrowBook("P1", "ISBN-1")
        assertEquals(BorrowResult.NOT_AVAILABLE, result)
    }

    @Test
    fun `borrowBook fails when patron exceeds borrowing limit`() {
        // Pre-fill patron with 3 books
        repository.addBook(Book("ISBN-2", "Book 2", "Author", 2020))
        repository.addBook(Book("ISBN-3", "Book 3", "Author", 2021))
        repository.addBook(Book("ISBN-4", "Book 4", "Author", 2022))
        service.borrowBook("P1", "ISBN-1")
        service.borrowBook("P1", "ISBN-2")
        service.borrowBook("P1", "ISBN-3")

        val result = service.borrowBook("P1", "ISBN-4")
        assertEquals(BorrowResult.LIMIT_REACHED, result)
    }

    @Test
    fun `returnBook successfully resets availability and removes ISBN from patron`() {
        service.borrowBook("P1", "ISBN-1")
        val success = service.returnBook("P1", "ISBN-1")
        assertTrue(success)
        assertTrue(repository.findBook("ISBN-1")!!.isAvailable)
        assertFalse(repository.findPatron("P1")!!.borrowedBooks.contains("ISBN-1"))
    }

    @Test
    fun `search returns empty list for whitespace-only query`() {
        val results = service.search("   ")
        assertTrue(results.isEmpty())
    }
}
```

### 3.2 Integration Testing: Console Workflow Test
Simulate interactive user input via `System.setIn` to verify the entire lifecycle from startup to shutdown:
- Add Book -> Register Patron -> Borrow -> Search -> Return -> Exit.

---

## 4. Data Persistence Architecture

### Stage 1: File-Based JSON Persistence
Implement a file-backed repository using `kotlinx.serialization`:
```kotlin
@Serializable
data class LibraryStorageState(
    val books: List<Book>,
    val patrons: List<Patron>
)

class JsonFileLibraryRepository(private val file: File) : LibraryRepository {
    // Loads state on initialization, flushes updates on each write operation
}
```

### Stage 2: Embedded Relational Database (SQLite via Exposed)
Transition to Kotlin Exposed with embedded SQLite:
- `BooksTable`: `isbn (PK)`, `title`, `author`, `year`, `is_available`.
- `PatronsTable`: `id (PK)`, `name`.
- `LoansTable`: `id (PK)`, `patron_id (FK)`, `isbn (FK)`, `borrowed_at`, `due_date`, `returned_at`.

---

## 5. Domain Modeling Enhancements

```
┌─────────────────────────────────┐
│              Book               │
├─────────────────────────────────┤
│ + isbn: String                  │
│ + title: String                 │
│ + author: String                │
│ + year: Int                     │
└────────────────┬────────────────┘
                 │ 1
                 │ has many
                 ▼ *
┌─────────────────────────────────┐
│            BookCopy             │
├─────────────────────────────────┤
│ + barcode: String (UUID)        │
│ + isbn: String                  │
│ + condition: BookCondition      │
│ + status: CopyStatus            │
└────────────────┬────────────────┘
                 │ 1
                 │ referenced by
                 ▼ *
┌─────────────────────────────────┐         ┌──────────────────────────────┐
│              Loan               │ *     1 │            Patron            │
├─────────────────────────────────┼─────────┤──────────────────────────────┤
│ + loanId: UUID                  │         │ + id: String                 │
│ + copyBarcode: String           │         │ + name: String               │
│ + patronId: String              │         │ + tier: PatronTier           │
│ + checkoutDate: LocalDate       │         │ + email: String              │
│ + dueDate: LocalDate            │         └──────────────────────────────┘
│ + returnDate: LocalDate?        │
│ + overdueFine: BigDecimal       │
└─────────────────────────────────┘
```

### Key Additions:
1. **Separation of Title from Copy**:
   Libraries track individual copies (`barcode`). A title can have 3 copies available and 2 checked out.
2. **Patron Tiers**:
   ```kotlin
   enum class PatronTier(val maxBorrowLimit: Int, val loanDurationDays: Long) {
       UNDERGRADUATE(maxBorrowLimit = 3, loanDurationDays = 14),
       GRADUATE(maxBorrowLimit = 5, loanDurationDays = 30),
       FACULTY(maxBorrowLimit = 10, loanDurationDays = 90)
   }
   ```
3. **Temporal Tracking & Fine Calculations**:
   Compute overdue charges automatically based on `ChronoUnit.DAYS.between(dueDate, LocalDate.now())`.

---

## 6. Validation and Error-Handling Upgrades

1. **Formal ISBN Checksum Verification**:
   Replace basic non-empty string checks with standard ISBN-10 (mod 11) and ISBN-13 (mod 10) check-digit validation.
2. **Publication Year Boundaries**:
   Validate that `year` falls within a realistic historical window:
   ```kotlin
   val currentYear = LocalDate.now().year
   require(year in 1450..currentYear + 1) { "Year must be between 1450 and ${currentYear + 1}" }
   ```
3. **Domain Result Monad**:
   Replace ambiguous boolean returns with a typed `Result<T>` or sealed hierarchy:
   ```kotlin
   sealed interface LibraryOperationResult {
       data class Success(val message: String) : LibraryOperationResult
       sealed interface Failure : LibraryOperationResult {
           data class DuplicateEntry(val key: String) : Failure
           data class NotFound(val entity: String, val id: String) : Failure
           data class PolicyViolation(val reason: String) : Failure
       }
   }
   ```

---

## 7. Usability and UI Modernization

1. **Administrative Submenu (Preserving Root Menu 1–6)**:
   To avoid breaking the canonical 6-option main menu, introduce an administrative submenu or management sub-mode:
   - Allows listing all patrons, viewing patron borrowing history, and inspecting overdue books.
2. **Tabular CLI Formatting**:
   Render search results and book lists using structured ASCII borders and ANSI colors:
   ```text
   ┌──────┬──────────────────────┬─────────────────┬──────┬───────────┐
   │ ISBN │ Title                │ Author          │ Year │ Status    │
   ├──────┼──────────────────────┼─────────────────┼──────┼───────────┤
   │ 1    │ Clean Code           │ Robert Martin   │ 2008 │ Available │
   │ 2    │ Effective Java       │ Joshua Bloch    │ 2018 │ Borrowed  │
   └──────┴──────────────────────┴─────────────────┴──────┴───────────┘
   ```
3. **Fuzzy Search**:
   Incorporate Levenshtein distance matching to accommodate minor typos in book titles or author names.

---

## 8. Phased Implementation Roadmap

```
  Phase 1: Quality & Testing (Weeks 1–2)
  ├── Comprehensive JUnit 5 unit test suite (Target: 95% coverage)
  └── Static analysis (ktlint, detekt) integrated into Gradle pipeline
               │
               ▼
  Phase 2: Persistence & Concurrency (Weeks 3–4)
  ├── JSON file-based or SQLite persistence implementation
  └── Concurrency locks & thread-safe data structures
               │
               ▼
  Phase 3: Domain Model & Policy Expansion (Weeks 5–6)
  ├── Multi-copy inventory (BookCopy entity)
  ├── Loan tracking (due dates, checkout timestamps, fines)
  └── Patron tiers with configurable borrowing ceilings
               │
               ▼
  Phase 4: Modern Interface & API (Weeks 7–8)
  ├── Rich CLI formatting (tables, color indicators)
  └── Headless REST API service using Ktor
```

### Phase Acceptance Criteria

#### Phase 1: Quality & Testing
- **AC 1.1**: JUnit Jupiter test suite achieves $\ge 90\%$ branch coverage across `DefaultLibraryService` and `InMemoryLibraryRepository`.
- **AC 1.2**: All borrow edge cases (limit reached, nonexistent book/patron, already borrowed) validated by automated tests.
- **AC 1.3**: Gradle build passes `ktlintCheck` with zero style or formatting errors.

#### Phase 2: Persistence & Concurrency
- **AC 2.1**: Catalog and patron data persist across JVM restarts.
- **AC 2.2**: Graceful recovery and backup creation if persistence file is corrupted.
- **AC 2.3**: Concurrent read and write access safely executed without `ConcurrentModificationException`.

#### Phase 3: Domain Model & Policy Expansion
- **AC 3.1**: Library can store multiple copies of a single ISBN and track individual checkout states.
- **AC 3.2**: System prevents checkout if a patron has overdue books or outstanding unpaid fines.
- **AC 3.3**: Patron tiers dynamically control maximum borrow count.

#### Phase 4: Modern Interface & API
- **AC 4.1**: Console output formatted in cleanly aligned ASCII tables.
- **AC 4.2**: Complete patron management features accessible through an administrative console.
- **AC 4.3**: Optional HTTP/REST endpoints exposed for external integrations.
