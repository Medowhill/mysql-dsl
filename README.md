# mysql-dsl
Simpl DSL for MySQL.
* Example
```scala
// importing type
import services.xis.mysql.SQL.types._

// defining schema
@schema class ARTICLE (
  val board: VARCHAR[$30],
  @PRIMARY val articleId: CHAR[$14],
  val hits: INT,
  val author: TEXT,
  val content: MEDIUMTEXT
)

// creating object
val article1 = ARTICLE("notice", "12345678901234", 10, "Medowhill", "haha")
val article2 = article1.withArticleId("09876543210987").withHits(100)

// pattern-matching object
article2 match {
  case ARTICLE(_, _, _, author, content) => s"$author: $content"
}

// connecting to database
val db = new Database("localhost", "3306", "DBNAME", "username", "password")
if (db.connected) {

  // inserting object into database
  db <~: article1

  // selecting object from database
  val articles: List[ARTICLE] = db :~> [ARTICLE]("board", "articleId")
  s"${articles.head.board}/${articles.head.articleId}"

} else
  db.connect()
```
