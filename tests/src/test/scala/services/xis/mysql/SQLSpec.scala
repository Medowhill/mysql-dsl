package services.xis.mysql

import scala.collection.mutable.ListBuffer
import scala.io.Source

import java.nio.file.{Paths, Files}

import org.scalatest._

class HelloSpec extends FlatSpec with Matchers with Resources {
  "MySQL Config" should "exists and be well-formed" in {
    Files.exists(Paths.get(confPath)) shouldEqual true
    Source.fromFile(confPath).mkString.split("\n").toList.length shouldEqual 5
  }

  val conf = Source.fromFile(confPath).mkString.split("\n").toList
  val db = new Database(conf(0), conf(1), conf(2), conf(3), conf(4))

  "Database" should "be connected" in {
    db.connected shouldEqual true
  }

  "Database" should "resolve query" in {
    db.query("DROP TABLE IF EXISTS TESTDBOBJECTS")
  }

  "Object" should "be pattern matched" in {
    obj match {
      case TESTDBOBJECT(b, i, h, au, c) =>
        b shouldEqual board
        i shouldEqual id
        h shouldEqual hits
        au shouldEqual author
        c shouldEqual content
    }
  }

  "Object" should "be pretty printed" in {
    obj.toString shouldEqual pretty
  }

  "Object" should "be copied" in {
    val newBoard = "notice"
    val obj1 = obj.withBoard(newBoard)
    obj1.board shouldEqual newBoard
    obj1.articleId shouldEqual id

    val newHits = 1000
    val obj2 = obj.withHits(newHits)
    obj2.board shouldEqual board
    obj2.hits shouldEqual newHits
  }

  "Object" should "be able to create table" in {
    TESTDBOBJECT.create shouldEqual query
  }

  "Object" should "be inserted into the table" in {
    db <~: obj
  }

  "Object" should "be selected from the table" in {
    val obj :: Nil = db :~> [TESTDBOBJECT]("board", "hits")
    obj.board shouldEqual board
    obj.hits shouldEqual hits
  }
}

trait Resources {
  val confPath = "mysql.conf"

  val board = "student_notice"
  val id = "11537395538406"
  val hits = 100
  val author = "Jaemin Hong"
  val content = "blah blah"
  val obj = TESTDBOBJECT(board, id, hits, author, content)

  val pretty = s"TESTDBOBJECT[board=$board, articleId=$id, hits=$hits, author=$author, content=$content]"
  var query = """CREATE TABLE IF NOT EXISTS TESTDBOBJECTS (
board VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
articleId CHAR(14) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
hits INT NOT NULL,
author TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
content MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
PRIMARY KEY (articleId)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"""
}
