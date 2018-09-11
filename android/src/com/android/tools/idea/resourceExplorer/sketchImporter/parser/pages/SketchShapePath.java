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
package com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages;

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayer;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SketchLayer} that mimics the JSON element with attribute <code>"_class"</code> set to be one of the following:
 * <ul>
 * <li>"shapePath"</li>
 * <li>"rectangle"</li>
 * <li>"oval"</li>
 * <li>"star"</li>
 * <li>"polygon"</li>
 * </ul>
 *
 * @see com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.SketchLayerDeserializer
 */
public class SketchShapePath extends SketchLayer {
  private final boolean isClosed;
  private final SketchCurvePoint[] points;

  public SketchShapePath(@NotNull String classType,
                         @NotNull String objectId,
                         int booleanOperation,
                         @NotNull SketchExportOptions exportOptions,
                         @NotNull Rectangle.Double frame,
                         boolean isFlippedHorizontal,
                         boolean isFlippedVertical,
                         boolean isVisible,
                         @NotNull String name,
                         int rotation,
                         boolean shouldBreakMaskChain,
                         boolean isClosed,
                         @NotNull SketchCurvePoint[] points,
                         @NotNull ResizingConstraint constraint) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain, constraint);

    this.isClosed = isClosed;
    this.points = points;
  }

  public boolean isClosed() {
    return isClosed;
  }

  @NotNull
  public SketchCurvePoint[] getPoints() {
    return points;
  }

  @NotNull
  public Point2D.Double getFramePosition() {
    return new Point2D.Double(getFrame().getX(), getFrame().getY());
  }
}
