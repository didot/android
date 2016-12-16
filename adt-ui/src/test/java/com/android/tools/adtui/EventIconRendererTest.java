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
package com.android.tools.adtui;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import javax.swing.*;

import java.awt.*;
import java.awt.geom.AffineTransform;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class EventIconRendererTest {

  private EventIconRenderer myRenderer;

  @Mock private Icon myIcon;
  @Mock private Graphics2D myGraphics2D;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    when(myIcon.getIconWidth()).thenReturn(2);
    myRenderer = new EventIconRenderer(myIcon, myIcon);
  }

  @Test
  public void g2dTransformIsSetProperly() {
    AffineTransform originalTransform = new AffineTransform();
    AffineTransform testTransform = new AffineTransform();
    when(myGraphics2D.getTransform()).thenReturn(originalTransform);
    myRenderer.draw(new JPanel(), myGraphics2D, testTransform, 0);

    ArgumentCaptor<AffineTransform> transforms = ArgumentCaptor.forClass(AffineTransform.class);
    verify(myGraphics2D, atLeast(1)).setTransform(transforms.capture());
    assertEquals(testTransform, transforms.getAllValues().get(0));
    assertEquals(originalTransform, transforms.getAllValues().get(1));
  }

  @Test
  public void iconIsPaint() {
    myRenderer.draw(new JPanel(), myGraphics2D, new AffineTransform(), 0);
    verify(myIcon).paintIcon(any(JPanel.class), eq(myGraphics2D), eq(-1), eq(0));
  }

  @Test(expected = AssertionError.class)
  public void iconsOfBothThemesHaveSameWidth() {
    Icon largeIcon = Mockito.mock(Icon.class);
    when(largeIcon.getIconWidth()).thenReturn(4);
    new EventIconRenderer(myIcon, largeIcon);
  }
}
