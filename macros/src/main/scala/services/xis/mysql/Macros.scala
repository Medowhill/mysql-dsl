package services.xis.mysql

import scala.reflect.macros.blackbox.Context
import scala.language.experimental.macros
import scala.annotation.StaticAnnotation

import macrocompat.bundle

class PRIMARY extends StaticAnnotation
class AUTOINC extends StaticAnnotation

class schema extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro schemaMacro.impl
}

@bundle
private class schemaMacro(val c: Context) {
  import c.universe._

  private def getColumn(t: Tree) = t match {
    case ValDef(mods, name, tpt, _) => (mods.annotations, name, tpt)
    case _ => c.abort(t.pos, "Ill-formed @schema.")
  }

  private def getFields(ts: List[Tree]) = 
    ts.flatMap{
      case dd @ DefDef(_, name, Nil, vparamss, _, _)
        if name == termNames.CONSTRUCTOR => vparamss match {
        case vparams :: Nil => for (vparam  <- vparams) yield getColumn(vparam)
        case _ =>
          c.abort(dd.pos, "Currying is not allowed for @schema.")
      }
      case dd: DefDef =>
        c.abort(dd.pos, "Method definition is not allowed for @schema.")
      case _ => List()
    }

  private def readType(t: Tree) = t match {
    case AppliedTypeTree(tycon, targ :: Nil) =>
      s"$tycon(${targ.toString.substring(1)})"
    case t => t.toString
  }

  private def optionOfType(t: Tree) =
    if (t.toString == "INT") "NOT NULL"
    else "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL"

  def impl(annottees: c.Expr[Any]*): c.Expr[Any] = {
    annottees.map(_.tree) match {
      case ClassDef(_, name, Nil, Template(_, _, body)) :: Nil =>
        val fields = getFields(body)
        val (_, names, _types) = fields.unzip3

        val primary = fields.filter{
          case (annot, _, _) => annot.exists(_.toString == "new PRIMARY()")
        }
        if (primary.length != 1)
          c.abort(c.enclosingPosition,
            "@schema should contain one and only one primary key.")

        val create = fields.map{ case (annot, name, tpt) => {
          s"$name ${readType(tpt)} ${optionOfType(tpt)}"
        }}.mkString(
          s"CREATE TABLE IF NOT EXISTS ${name}S (\n",
          ",\n",
          s",\nPRIMARY KEY (${primary.head._2})\n) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        )

        val types = _types.map{
          case AppliedTypeTree(t, _) => t
          case t => t
        }
        val params = (names zip types).map{
          case (name, tpt) => q"val $name: $tpt"
        }
        val gets = names.map(n => q"x.$n")
        val defaults = types.map(t =>
          if (t.toString == "INT") q"-1" else q"null"
        )
        val copiers = (names zip types).map{
          case (n, t) => {
            val m = TermName(s"with${n.toString.capitalize}")
            q"def $m($n: $t): $name = new $name(..$names)"
        }}

        val prestring = names.map(name => {
          val n = Literal(Constant(name.toString + "="))
          q"$n + $name"
        }).reduce((t1, t2) => q"""$t1 + ", " + $t2""")
        val string = q"""${Literal(Constant(name.toString + "["))} + $prestring + "]""""

        val table = Literal(Constant(name.toString + "S"))
        val companion = name.toTermName
        c.Expr(q"""
          class $name(..$params) {
            override def toString: String = $string
            def <~:(db: Database): Unit = {
              db.query($companion.create)
              db.insert($table, ..$names)
            }
            ..$copiers
          }
          object $companion {
            val empty = new $name(..$defaults)
            private[mysql] val create = $create
            def apply(..$params) = new $name(..$names)
            def unapply(x: $name): Option[(..$types)] = Some(..$gets)
          }
        """)
      case _ =>
        c.abort(c.enclosingPosition,
    "@schema should be applied to a class definition without type parameters.")
    }
  }
}
