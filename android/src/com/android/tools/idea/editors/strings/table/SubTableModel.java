/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.editors.strings.table;

import org.jetbrains.annotations.NotNull;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.util.function.IntSupplier;

/**
 * A view into a range of the delegate table's columns.
 */
final class SubTableModel implements TableModel {
  private final TableModel myDelegate;

  private final IntSupplier myStartColumnSupplier;
  private final IntSupplier myEndColumnSupplier;

  /**
   * The parameters are IntSuppliers so clients can specify the range as:
   *
   * <p>{@code new SubTableModel(delegate, () -> startColumn, delegate::getColumnCount)}
   *
   * <p>The number of columns in a TableModel can change.
   *
   * @param startColumnSupplier supplies the start column index (inclusive)
   * @param endColumnSupplier   supplies the end column index (exclusive)
   */
  SubTableModel(@NotNull TableModel delegate, @NotNull IntSupplier startColumnSupplier, @NotNull IntSupplier endColumnSupplier) {
    myDelegate = delegate;

    myStartColumnSupplier = startColumnSupplier;
    myEndColumnSupplier = endColumnSupplier;
  }

  /**
   * Maps the index of the column in this model to the index of the column in the delegate.
   */
  int convertColumnIndexToDelegate(int modelColumnIndex) {
    assert 0 <= modelColumnIndex && modelColumnIndex < getColumnCount();
    return myStartColumnSupplier.getAsInt() + modelColumnIndex;
  }

  /**
   * Maps the index of the column in the delegate to the index of the column in this model.
   */
  int convertColumnIndexToModel(int delegateColumnIndex) {
    int startDelegateColumnIndex = myStartColumnSupplier.getAsInt();

    assert startDelegateColumnIndex <= delegateColumnIndex && delegateColumnIndex < myEndColumnSupplier.getAsInt();
    return delegateColumnIndex - startDelegateColumnIndex;
  }

  @NotNull
  TableModel delegate() {
    return myDelegate;
  }

  @Override
  public int getRowCount() {
    return myDelegate.getRowCount();
  }

  @Override
  public int getColumnCount() {
    return myEndColumnSupplier.getAsInt() - myStartColumnSupplier.getAsInt();
  }

  @NotNull
  @Override
  public String getColumnName(int column) {
    return myDelegate.getColumnName(convertColumnIndexToDelegate(column));
  }

  @NotNull
  @Override
  public Class<?> getColumnClass(int column) {
    return myDelegate.getColumnClass(convertColumnIndexToDelegate(column));
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return myDelegate.isCellEditable(row, convertColumnIndexToDelegate(column));
  }

  @NotNull
  @Override
  public Object getValueAt(int row, int column) {
    return myDelegate.getValueAt(row, convertColumnIndexToDelegate(column));
  }

  @Override
  public void setValueAt(@NotNull Object value, int row, int column) {
    myDelegate.setValueAt(value, row, convertColumnIndexToDelegate(column));
  }

  @Override
  public void addTableModelListener(@NotNull TableModelListener listener) {
  }

  @Override
  public void removeTableModelListener(@NotNull TableModelListener listener) {
  }
}
