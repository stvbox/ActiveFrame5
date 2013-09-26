package ru.intertrust.cm.core.gui.impl.client.plugins.collection.view.panel;

/**
 * @author mike-khukh
 */
public interface ICellDraggable {
  /**
   * Install draggable property
   * 
   * @param isDraggable
   */
  void setDraggable(boolean isDraggable);

  /**
   * Returns draggable property
   * 
   * @return isDraggable
   */
  boolean isDraggable();

}
