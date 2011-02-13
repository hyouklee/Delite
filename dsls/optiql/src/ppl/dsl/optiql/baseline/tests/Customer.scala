package ppl.dsl.optiql.baseline.tests


/**
 * Author: Lere Williams
 * Date: July 2009
 *
 * Description:
 */

class Customer(val id: Int, val name: String, val phone: Int, val orders: QVector[Order]) {
  def getName() = name
  def getNameSubstring(start: Int) = name.substring(start)
  def getNameSubstring(start: Int, end: Int) = name.substring(start, end)
  override def toString() = "CustID:" + id + "_Name:" + name
}