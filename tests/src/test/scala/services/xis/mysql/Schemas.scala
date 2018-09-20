package services.xis.mysql

import services.xis.mysql.SQL.types._

@schema class TESTDBOBJECT (
  val board: VARCHAR[$30],
  @PRIMARY val articleId: CHAR[$14],
  val hits: INT,
  val author: TEXT,
  val content: MEDIUMTEXT
)
