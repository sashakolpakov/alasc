package net.alasc.coll

sealed trait Size {
  def toInt: Int
}
case class IntSize(size: Int) extends Size {
  def toInt = size
}
case class BigIntSize(size: BigInt) extends Size {
  def toInt = {
    require(size.isValidInt)
    size.toInt
  }
}
case object InfiniteSize extends Size {
  def toInt = sys.error("Cannot return infinite size as Int")
}
