/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.viewer.camera;

import com.android.tools.idea.editors.gfxtrace.viewer.CameraModel;
import com.android.tools.idea.editors.gfxtrace.viewer.vec.MatD;
import com.android.tools.idea.editors.gfxtrace.viewer.vec.VecD;

import static com.android.tools.idea.editors.gfxtrace.viewer.Constants.MAX_DISTANCE;
import static com.android.tools.idea.editors.gfxtrace.viewer.Constants.MIN_DISTANCE;

//  _______     .     _____     ___
// |       |   / \   /     \  ."   ",
// |       |  /   \ /       \/       \
// |       | /     \\       /\       /
// |_______|/_______\\_____/  '.___.'
//

/**
 * A {@link CameraModel} that moves the camera constrained to the isosurface of a given
 * {@link Emitter}. The camera's position and direction is interpolated between a given base
 * {@link CameraModel} and the isosurface based on the zoom level. Thus, this model smoothly
 * transitions from the base model when fully zoomed out to the isosurface model when zoomed all
 * the way in.
 *
 * The position and direction of the camera is determined by casting a ray from the base camera's
 * position towards the origin. Let P be the closest interception of this ray with the isosurface.
 * The direction of the camera is the inverse of the normal of the isosurface at P. The normal is
 * computed numerically by sampling the isosurface around P, The camera's position is placed at the
 * desired zoom distance away from P along the normal. Thus, the camera is position at
 * <code>C = P + zoom * N</code>, looking at P.
 */
public class IsoSurfaceCameraModel implements CameraModel {
  private static final double SMOOTHNESS_ROTATION = 0.8;
  private static final int SMOOTHNESS_GRID_SIZE = 5; // must be odd
  private static final int INDEX_OF_Y1_X0 = (SMOOTHNESS_GRID_SIZE - 1) / 2 + SMOOTHNESS_GRID_SIZE * ((SMOOTHNESS_GRID_SIZE - 1) / 2 + 1);

  private final CameraModel myBase;
  private RayCaster myRayCaster;
  private double myInflation;

  private MatD myViewTransform = MatD.IDENTITY;

  private double myLastDistance = 1;

  public IsoSurfaceCameraModel(CameraModel base) {
    myBase = base;
    update();
  }

  public void setEmitter(Emitter emitter) {
    myRayCaster = new RayCaster(emitter);
    myInflation = Math.max(0, MIN_DISTANCE - emitter.getOffset());
  }

  @Override
  public void updateViewport(double screenWidth, double screenHeight) {
    myBase.updateViewport(screenWidth, screenHeight);
  }

  @Override
  public void onDrag(double dx, double dy) {
    myBase.onDrag(dx, dy);
    update();
  }

  @Override
  public void onZoom(double dz) {
    myBase.onZoom(dz);
    update();
  }

  private void update() {
    if (myRayCaster == null || myBase.getZoom() == 0 || !updateUsingIsoSurface()) {
      myViewTransform = myBase.getViewTransform();
    }
  }

  private boolean updateUsingIsoSurface() {
    double[] m = myBase.getViewTransform().inverseOfTop3x3();
    VecD up = new VecD(m[3], m[4], m[5]).normalize();
    VecD direction = new VecD(-m[6], -m[7], -m[8]).normalize();
    return evaluateIsoSurface(up, direction);
  }

  private boolean evaluateIsoSurface(VecD up, VecD direction) {
    VecD pos = getFirstIntersectionWithHint(direction);
    if (pos == null) {
      return false;
    }

    VecD right = direction.cross(up).normalize().scale(SMOOTHNESS_ROTATION / SMOOTHNESS_GRID_SIZE);
    up = up.scale(SMOOTHNESS_ROTATION / SMOOTHNESS_GRID_SIZE);

    // Generate the samples to compute the normal.
    VecD[] grid = new VecD[SMOOTHNESS_GRID_SIZE * SMOOTHNESS_GRID_SIZE];
    int size = (SMOOTHNESS_GRID_SIZE - 1) / 2;
    for (int y = -size, index = 0; y <= size; y++) {
      for (int x = -size; x <= size; x++, index++) {
        grid[index] = pos.addScaled(up, y).addScaled(right, x);
        VecD dir = grid[index].scale(-1).normalize();
        if ((grid[index] = myRayCaster.getIntersection(grid[index], dir)) == null) {
          return false;
        }
      }
    }

    // Compute the normal based on the sampling grid.
    VecD normal = new VecD();
    for (int y = 0; y < SMOOTHNESS_GRID_SIZE - 1; y++) {
      for (int x = 0; x < SMOOTHNESS_GRID_SIZE - 1; x++) {
        int p0 = y * SMOOTHNESS_GRID_SIZE + x;
        int p1 = (y + 1) * SMOOTHNESS_GRID_SIZE + x;
        int p2 = y * SMOOTHNESS_GRID_SIZE + x + 1;

        VecD upOffset = grid[p1].subtract(grid[p0]);
        VecD rightOffset = grid[p2].subtract(grid[p0]);
        normal = normal.add(rightOffset.cross(upOffset).normalize());
      }
    }

    normal = normal.normalize();
    up = grid[INDEX_OF_Y1_X0].subtract(pos).normalize();
    pos = pos.addScaled(normal, myInflation);

    // Interpolate the isosurface position & direction with the base's based on the zoom amount.
    double zoom = getZoom();
    normal = normal.scale(-1).lerp(direction, zoom);
    pos = pos.lerp(direction.scale(-MAX_DISTANCE), zoom);
    myViewTransform = MatD.lookAt(pos, pos.add(normal), up);
    return true;
  }

  /**
   * Finds the the closest interception with the isosurface for the given direction. Uses the last
   * computed distance as a starting point for optimization (typically, the isosurface ought to be
   * smooth for smooth camera movement).
   */
  private VecD getFirstIntersectionWithHint(VecD direction) {
    VecD pos = direction.scale(-myLastDistance);
    VecD result = myRayCaster.getIntersection(pos, direction);
    if (result != null) {
      myLastDistance = result.magnitude();
      return result;
    }
    return null;
  }

  @Override
  public MatD getViewTransform() {
    return myViewTransform;
  }

  @Override
  public MatD getProjection() {
    return myBase.getProjection();
  }

  @Override
  public double getZoom() {
    return myBase.getZoom();
  }
}
