/*
 * This file is generated by jOOQ.
 */
package gospeak.libs.sql.jooqdb.tables.records


import gospeak.libs.sql.jooqdb.tables.Categories

import java.lang.Integer
import java.lang.String

import org.jooq.Field
import org.jooq.Record1
import org.jooq.Record2
import org.jooq.Row2
import org.jooq.impl.UpdatableRecordImpl


/**
 * This class is generated by jOOQ.
 */
class CategoriesRecord extends UpdatableRecordImpl[CategoriesRecord](Categories.CATEGORIES) with Record2[Integer, String] {

  /**
   * Setter for <code>PUBLIC.categories.id</code>.
   */
  def setId(value : Integer) : Unit = {
    set(0, value)
  }

  /**
   * Getter for <code>PUBLIC.categories.id</code>.
   */
  def getId : Integer = {
    val r = get(0)
    if (r == null) null else r.asInstanceOf[Integer]
  }

  /**
   * Setter for <code>PUBLIC.categories.name</code>.
   */
  def setName(value : String) : Unit = {
    set(1, value)
  }

  /**
   * Getter for <code>PUBLIC.categories.name</code>.
   */
  def getName : String = {
    val r = get(1)
    if (r == null) null else r.asInstanceOf[String]
  }

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------
  override def key : Record1[Integer] = {
    return super.key.asInstanceOf[ Record1[Integer] ]
  }

  // -------------------------------------------------------------------------
  // Record2 type implementation
  // -------------------------------------------------------------------------

  override def fieldsRow : Row2[Integer, String] = {
    super.fieldsRow.asInstanceOf[ Row2[Integer, String] ]
  }

  override def valuesRow : Row2[Integer, String] = {
    super.valuesRow.asInstanceOf[ Row2[Integer, String] ]
  }
  override def field1 : Field[Integer] = Categories.CATEGORIES.ID
  override def field2 : Field[String] = Categories.CATEGORIES.NAME
  override def component1 : Integer = getId
  override def component2 : String = getName
  override def value1 : Integer = getId
  override def value2 : String = getName

  override def value1(value : Integer) : CategoriesRecord = {
    setId(value)
    this
  }

  override def value2(value : String) : CategoriesRecord = {
    setName(value)
    this
  }

  override def values(value1 : Integer, value2 : String) : CategoriesRecord = {
    this.value1(value1)
    this.value2(value2)
    this
  }

  /**
   * Create a detached, initialised CategoriesRecord
   */
  def this(id : Integer, name : String) = {
    this()

    set(0, id)
    set(1, name)
  }
}
