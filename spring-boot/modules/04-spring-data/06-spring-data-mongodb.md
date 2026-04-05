# 06 — Spring Data MongoDB

## Overview

Spring Data MongoDB provides the same repository abstraction as Spring Data JPA — but for MongoDB document storage.

TaskForge uses PostgreSQL (relational data is a good fit for our structured domain). This module gives you awareness of MongoDB patterns in Spring Boot so you can apply them when a document store is appropriate.

---

## When to Choose MongoDB over PostgreSQL

| Use MongoDB when... | Use PostgreSQL when... |
|--------------------|----------------------|
| Schema varies per document | Fixed schema with relationships |
| Rapid prototyping, frequent schema changes | Stable schema, referential integrity required |
| Document-centric data (e.g., product catalog) | Transactional operations across entities |
| High write throughput, horizontal scaling | Complex queries, aggregations, JOINs |
| Nested/embedded data is natural | Normalized relational data |

**Example: MongoDB is a good fit for...**
- Product catalog with varying attributes per category
- CMS / blog posts with flexible sections
- User activity logs / event streams
- Real-time analytics aggregations

---

## Setup

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/myapp
      # Or with auth:
      # uri: mongodb://user:password@localhost:27017/myapp?authSource=admin
```

Start MongoDB with Docker:
```bash
docker run -d --name mongo-dev -p 27017:27017 mongo:7.0
```

---

## @Document — MongoDB Entity

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection = "products")   // MongoDB collection name
public class Product {

    @Id
    private String id;             // MongoDB uses String ObjectId, not Long

    @Indexed(unique = true)
    private String sku;

    private String name;

    private double price;

    @Field("category_name")        // custom field name in document
    private String categoryName;

    // Embedded document — stored as nested object, no JOIN needed
    private Address warehouseAddress;

    // Array of embedded documents
    private List<ProductVariant> variants;

    // ...
}
```

**Key differences from JPA @Entity:**

| JPA | MongoDB |
|-----|---------|
| `@Entity` | `@Document` |
| `@Table` | `collection` attribute on @Document |
| `@Id` (Jakarta) | `@Id` (Spring Data) |
| `@Column` | `@Field` |
| `@GeneratedValue` | Not needed — MongoDB auto-generates ObjectId |
| Foreign keys | Embedded documents or DBRef |
| Migrations (Flyway) | Schema-less — no migrations needed |

---

## MongoRepository

```java
public interface ProductRepository extends MongoRepository<Product, String> {

    // Derived queries work the same as JPA
    List<Product> findByCategoryName(String categoryName);
    List<Product> findByPriceLessThan(double maxPrice);
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
}
```

---

## @Query with MongoDB Query Language

```java
// MongoDB query syntax uses JSON-like filter expressions
@Query("{ 'price': { '$gte': ?0, '$lte': ?1 } }")
List<Product> findByPriceRange(double min, double max);

@Query("{ 'categoryName': ?0 }")
List<Product> findByCategory(String category);

// Only return specific fields (projection)
@Query(value = "{ 'categoryName': ?0 }", fields = "{ 'name': 1, 'price': 1, '_id': 1 }")
List<Product> findNameAndPriceByCategory(String category);
```

---

## MongoTemplate — Low-Level API

For complex operations:

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final MongoTemplate mongoTemplate;

    public List<Product> findInStockByCategory(String category, double maxPrice) {
        Query query = new Query();
        query.addCriteria(Criteria.where("categoryName").is(category)
            .and("price").lte(maxPrice)
            .and("variants.stock").gt(0));
        query.with(Sort.by(Sort.Direction.ASC, "price"));
        query.limit(50);

        return mongoTemplate.find(query, Product.class);
    }

    public UpdateResult markOutOfStock(String sku) {
        Query query = new Query(Criteria.where("sku").is(sku));
        Update update = new Update().set("inStock", false).currentDate("updatedAt");
        return mongoTemplate.updateFirst(query, update, Product.class);
    }
}
```

---

## Aggregation Pipeline

MongoDB's most powerful feature — multi-stage data transformation:

```java
Aggregation agg = Aggregation.newAggregation(
    Aggregation.match(Criteria.where("categoryName").is("Electronics")),
    Aggregation.group("categoryName")
        .count().as("totalProducts")
        .avg("price").as("avgPrice")
        .max("price").as("maxPrice"),
    Aggregation.sort(Sort.Direction.DESC, "avgPrice")
);

AggregationResults<CategoryStats> results =
    mongoTemplate.aggregate(agg, "products", CategoryStats.class);
```

**Node.js equiv (Mongoose):**
```javascript
await Product.aggregate([
    { $match: { categoryName: 'Electronics' } },
    { $group: { _id: '$categoryName', avgPrice: { $avg: '$price' } } },
    { $sort: { avgPrice: -1 } }
]);
```

---

## Embedded Documents vs References

**Embedded (denormalized — MongoDB's natural style):**
```java
// Address is stored INSIDE the User document
@Document("users")
public class User {
    @Id private String id;
    private String name;
    private Address address;          // embedded
    private List<String> phoneNumbers; // array of primitives
}
```

**References (similar to FK — less common in MongoDB):**
```java
// DBRef — stores a reference to another document
@Document("orders")
public class Order {
    @Id private String id;
    
    @DBRef
    private User customer;   // reference to another document — causes separate query
}
```

**General advice:** Embed if the data is accessed together and rarely updated independently. Reference if the data is large or shared across many documents.

---

## Spring Data Dual Configuration

You can use both JPA and MongoDB in the same Spring Boot app:

```java
// Tell Spring which package uses JPA vs MongoDB
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.myapp.jpa")
@EnableMongoRepositories(basePackages = "com.myapp.mongo")
public class MyApp { ... }
```

---

## Try It Yourself (Optional)

If you want to explore MongoDB:

1. Start MongoDB: `docker run -d -p 27017:27017 mongo:7.0`
2. Add the MongoDB starter to pom.xml
3. Create a `BlogPost` @Document with embedded `List<Comment>` (comments inside the post document)
4. Create a `BlogPostRepository extends MongoRepository<BlogPost, String>`
5. Use Spring Data REST (`spring-boot-starter-data-rest`) to auto-expose CRUD endpoints

---

## Capstone Connection

TaskForge uses PostgreSQL — relational data with FK constraints suits our domain. But knowledge of MongoDB is valuable for:
- Storing task activity logs (append-only, high frequency)
- Caching complex project analytics
- Any future audit trail feature

In a microservices architecture (Module 7), it's common to have some services use PostgreSQL and others MongoDB.

**Next:** [07 — Spring Data JDBC](./07-spring-data-jdbc.md) — a lighter-weight alternative to JPA.
