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

package com.android.tools.sherpa.drawing;

import com.android.tools.sherpa.drawing.decorator.ColorTheme;

import java.awt.Color;

/**
 * Default color set for the "blueprint" UI mode
 */
public class BlueprintColorSet extends ColorSet {

    public BlueprintColorSet() {
        BlueprintBackground = new Color(24, 55, 112);
        BlueprintFrames = new Color(100, 152, 199);
        BlueprintConstraints = new Color(102, 129, 204);
        BlueprintText = new Color(220, 220, 220);
        BlueprintHighlightFrames = new Color(160, 216, 237);
        BlueprintSnapGuides = new Color(220, 220, 220);
        BlueprintSnapLightGuides = new Color(220, 220, 220, 128);
        InspectorTrackBackgroundColor = new Color(228, 228, 238);
        InspectorTrackColor = new Color(208, 208, 218);
        InspectorHighlightsStrokeColor = new Color(160, 160, 180, 128);

        DarkBlueprintBackground = ColorTheme.updateBrightness(BlueprintBackground, 0.8f);
        DarkBlueprintBackgroundLines =
                ColorTheme.updateBrightness(DarkBlueprintBackground, 1.06f);
        DarkBlueprintFrames = ColorTheme.updateBrightness(BlueprintFrames, 0.8f);

        BlueprintBackgroundLines = ColorTheme.updateBrightness(BlueprintBackground, 1.06f);

        InspectorBackgroundColor =
                ColorTheme.fadeToColor(BlueprintBackground, Color.WHITE, 0.1f);
        InspectorFillColor = ColorTheme
                .fadeToColor(ColorTheme.updateBrightness(BlueprintBackground, 1.3f),
                        Color.WHITE, 0.1f);
    }
}
