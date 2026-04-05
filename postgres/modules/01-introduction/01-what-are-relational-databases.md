# What Are Relational Databases?

## Concept

A **relational database** organizes data into **tables** (called *relations*) made up of **rows** and **columns**. Relationships between tables are expressed through **shared values** (keys), not through pointers, document nesting, or physical proximity on disk.

This sounds simple, but it's a profound design choice: data is stored separately from the code that retrieves it, and any valid piece of data can be related to any other through a query. This is what E.F. Codd described in his 1970 paper "A Relational Model of Data for Large Shared Data Banks" — and it remains the dominant model for structured data 55+ years later.

---

## The Core Idea: Data as Tables

At its heart, a relational database presents data as a collection of two-dimensional tables:

```
┌─────────────────────────────────────────────────────────┐
│                    customer                              │
├────┬──────────────────────────┬────────────────────────┤
│ id │ email                    │ name                   │
├────┼──────────────────────────┼────────────────────────┤
│  1 │ alice@example.com        │ Alice Nguyen           │
│  2 │ bob@example.com          │ Bob Patel              │
│  3 │ carol@example.com        │ Carol Smith            │
└────┴──────────────────────────┴────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                        order                                    │
├────┬─────────────┬────────────────┬──────────────────────────┤
│ id │ customer_id │ total_amount   │ status                   │
├────┼─────────────┼────────────────┼──────────────────────────┤
│ 10 │           1 │         129.99 │ DELIVERED                │
│ 11 │           1 │          49.00 │ SHIPPED                  │
│ 12 │           2 │         289.50 │ PENDING                  │
└────┴─────────────┴────────────────┴──────────────────────────┘
```

The `order.customer_id` column *references* the `customer.id` column. There are no nested documents, no arrays of embedded objects — just flat tables linked by shared values.

When you want Alice's orders, you *join* the two tables on the shared key:

```sql
SELECT c.name, o.total_amount, o.status
FROM customer c
JOIN "order" o ON o.customer_id = c.id
WHERE c.email = 'alice@example.com';
```

---

## Why This Design?

### 1. Each Fact Is Stored Once

Alice's email address exists in exactly one row of the `customer` table. If she changes her email, you update one row. In a document database with orders containing embedded customer documents, you'd need to update every order Alice has ever placed.

This is the **don't repeat yourself** principle applied to data storage.

### 2. New Questions Don't Require Schema Changes

Because data is stored in normalized tables and linked by keys, you can answer questions that weren't anticipated when the schema was designed — purely through queries. Adding a new report doesn't require restructuring the data.

### 3. The Query Language Is Standardized

SQL (Structured Query Language) is a declared standard. Skills transfer across PostgreSQL, MySQL, SQL Server, Oracle, SQLite, and dozens of others — with dialect differences, but a common foundation.

### 4. Integrity Is Enforced by the Database

A relational database enforces **constraints**: you cannot insert an order that references a non-existent customer, you cannot set a rating to 6 when the maximum is 5, you cannot have two customers with the same email. The database is the last line of defense for data quality.

---

## When to Use a Relational Database

| Use Case | Good Fit? | Reason |
|----------|-----------|--------|
| Structured business data (orders, users, products) | ✅ Yes | Relations, constraints, transactions |
| Financial transactions | ✅ Yes | ACID guarantees are non-negotiable |
| Data with complex relationships | ✅ Yes | JOINs, foreign keys |
| Ad-hoc reporting and analytics | ✅ Yes | SQL is extremely expressive |
| Application configuration or session data | ⚠️ Maybe | Often better in Redis or a config file |
| Unstructured text or large blobs | ⚠️ Maybe | Consider object storage for large files |
| Extremely high write throughput at web scale | ⚠️ Maybe | Consider sharding or purpose-built stores |
| Document data with highly variable shape | ⚠️ Maybe | MongoDB or PostgreSQL's JSONB column |
| Graph relationships (social networks) | ⚠️ Maybe | Graph databases (Neo4j) excel here; PostgreSQL with recursive CTEs is often sufficient |

> **Rule of thumb:** Start with a relational database. You can always add specialized stores later. Starting with a document store and discovering you need transactions is much harder to recover from.

---

## Relational vs. Document Databases: A Concrete Example

Imagine storing a product with multiple attributes (color, size, material) that vary by product type.

**Document approach (MongoDB):**
```json
{
  "_id": "prod_123",
  "name": "Classic T-Shirt",
  "price": 29.99,
  "attributes": {
    "color": "navy",
    "size": "M",
    "material": "cotton"
  }
}
```

**Relational approach (PostgreSQL):**
```sql
-- Option A: JSONB column (flexible attributes)
SELECT id, name, price, attributes->>'color' AS color
FROM product
WHERE id = 'prod_123';

-- Option B: EAV table (structured, queryable)
SELECT p.name, pa.attribute_key, pa.attribute_value
FROM product p
JOIN product_attribute pa ON pa.product_id = p.id
WHERE p.id = 'prod_123';
```

PostgreSQL offers **both** options. As you'll learn in Module 05, choosing between them is a schema design decision — and PostgreSQL doesn't force you to pick just one model.

---

## Try It Yourself

Before Module 02 gets PostgreSQL running, answer these questions about StoreForge conceptually:

1. If a product is deleted from the `product` table, what should happen to `order_item` rows that reference it? (Hint: there are four options for foreign key behavior.)

2. The `category` table has a `parent_id` column that references its own `id` column. What kind of structure does this represent, and what query technique (Module 04) would you use to traverse it?

3. An order's `total_amount` could be calculated by summing `order_item.quantity * order_item.unit_price`. Should it be stored in the `order` table, or recalculated every time? List one argument for each side.

<details>
<summary>Discussion answers</summary>

1. Options: `ON DELETE RESTRICT` (block the delete), `ON DELETE CASCADE` (delete order_items too), `ON DELETE SET NULL` (set order_item.product_id to NULL — requires nullable column), or `ON DELETE NO ACTION` (deferred check). For an e-commerce system, `RESTRICT` or `SET NULL` makes the most sense — you don't want to delete order history just because a product is discontinued.

2. This is a **self-referential relationship** representing a tree (category hierarchy). Electronics → Laptops → Gaming Laptops, for example. Module 04 covers **recursive CTEs** for traversing such hierarchies.

3. **Stored (denormalized):** Faster reads, needed for order history integrity (prices change over time — `unit_price` is captured at order time for exactly this reason). **Calculated:** Always accurate, no risk of inconsistency. In practice: store it, update it via trigger or application logic on order_item changes. Module 06 implements this trigger.

</details>

---

## Capstone Connection

Every table in StoreForge exists for a reason rooted in relational principles:

- **Separate `address` table** (not embedded in `customer`) — a customer can have multiple addresses; orders reference a specific address at the time of purchase
- **`order_item.unit_price`** captures price at purchase time — product prices change, but historical order totals must remain accurate
- **`category.parent_id`** self-reference — represents a product taxonomy tree without a fixed depth limit
- **No embedded arrays** — every relationship is expressed through foreign keys and JOIN-able tables, making every relationship queryable in both directions
