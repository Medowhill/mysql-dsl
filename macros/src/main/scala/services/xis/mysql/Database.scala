package services.xis.mysql

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox.Context
import scala.language.experimental.macros

import macrocompat.bundle

import java.sql.{DriverManager, Connection, PreparedStatement, ResultSet}

class Database(
  host: String, port: String, db: String, user: String, pwd: String
) {
  private var connection: Connection = null

  def connect() =
    if (connection == null) {
      Database.loadDriver()
      try {
        connection = DriverManager.getConnection(
      s"jdbc:mysql://$host:$port/$db?useUnicode=true&characterEncoding=UTF-8",
          user, pwd
        )
      } catch {
        case e: Exception => e.printStackTrace
      }
    }
  def connected = connection != null

  private[mysql] def query(q: String): Unit = {
    val stmt = connection.createStatement()
    stmt.execute(q)
    stmt.close()
  }

  private[mysql] def insert(name: String, values: Any*): Unit = {
    if (connected) {
      val stmt = connection.prepareStatement(
        values.map(_ => "?").mkString(
          s"INSERT INTO $name VALUES (", ", ", ")"))
      values.zipWithIndex.foreach{ case (v, i) => set(stmt, i + 1, v) }
      stmt.executeUpdate()
      stmt.close()
    }
  }

  private def set(stmt: PreparedStatement, i: Int, v: Any): Unit = v match {
    case n: Int => stmt.setInt(i, n)
    case _ => stmt.setString(i, v.toString)
  }

  private[mysql] def select(
    f: ResultSet => Unit, name: String, fields: String*
  ): Unit = {
    val query = fields.mkString("SELECT ", ",", s" FROM $name")
    val stmt = connection.createStatement()
    val rs = stmt.executeQuery(query)
    while (rs.next()) f(rs)
    stmt.close()
  }

  private[mysql] def get[T: ClassTag, S](
    f: T => S, rs: ResultSet, field: String
  ): S =
    implicitly[ClassTag[T]].unapply(0) match {
      case None => f(rs.getString(field).asInstanceOf[T])
      case _ => f(rs.getInt(field).asInstanceOf[T])
    }

  def :~>[T](fields: String*): List[T] = macro selectMacro.impl[T]

  connect()
}

@bundle
private class selectMacro(val c: Context) {
  import c.universe._

  private def table(t: Type): String = {
    val s = t.toString
    val i = s.lastIndexOf(".")
    if (i < 0) s else s.substring(i + 1)
  }

  def impl[T: c.WeakTypeTag](fields: c.Expr[String]*): c.Expr[List[T]] = {
    val T = implicitly[c.WeakTypeTag[T]].tpe
    val companion = TermName(table(T))
    val db = c.prefix
    val t = table(T) + "S"

    val names = fields.map(f => f.tree match {
      case Literal(Constant(s: String)) => s
      case t =>
        c.abort(t.pos, ":~> should get string literals as field names")
    })
    val init: Tree = q"$companion.empty"
    val gets = (init /: names){ case (e, n) =>
      val m = TermName(s"with${n.capitalize}")
      q"$db.get($e.$m, rs, $n)"
    }

    c.Expr[List[T]](q"""
      val buf: scala.collection.mutable.ListBuffer[$T] = scala.collection.mutable.ListBuffer[$T]()
      def f(rs: java.sql.ResultSet): Unit = { buf += $gets }
      $db.select(f, $t, ..$fields)
      buf.toList
    """)
  }
}

object Database {
  private[this] var loaded = false

  private def loadDriver(): Unit =
    if (!loaded) {
      this.synchronized {
        try {
          Class.forName("com.mysql.jdbc.Driver").newInstance
          loaded = true
        } catch {
          case e: Exception => e.printStackTrace
        }
      }
    }
} 
