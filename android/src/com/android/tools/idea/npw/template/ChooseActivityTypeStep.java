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
package com.android.tools.idea.npw.template;


import com.android.tools.idea.npw.ActivityGalleryStep;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;

/**
 * This step allows the user to select which type of Activity they want to create.
 * TODO: This step can be used as part of the "New Project" flow. In that flow, if the "Has CPP support" is selected, we should not show
 * this step, but the next step should be "Basic Activity". In the current work flow (using the dynamic wizard), this was difficult to do,
 * so instead {@link ActivityGalleryStep} was always shown with three options ("Add no Activity", "Basic Activity" and "Empty Activity").
 * The code to filter out the activities is {@link TemplateListProvider}
 * TODO: ATTR_IS_LAUNCHER seems to be dead code, it was one option in the old UI flow. Find out if we can remove it.
 * TODO: CircularParameterDependencyException when selecting "Empty Activity" > "Cancel" (OK with all others!)
 * TODO: Missing error messages for "missing theme", "incompatible API", ect. See {@link ActivityGalleryStep#validate()}
 * TODO: Extending RenderTemplateModel don't seem to match with the things ChooseActivityTypeStep needs to be configured... For example,
 * it needs to know "Is Cpp Project" (to adjust the list of templates or hide itself).
 * TODO: This class and future ChooseModuleTypeStep look to have a lot in common. Should we have something more specific than a ASGallery,
 * that renders "Gallery items"?
 */
public class ChooseActivityTypeStep extends SkippableWizardStep<NewModuleModel> {
  private final RenderTemplateModel myRenderModel;
  private @NotNull TemplateHandle[] myTemplateList;
  private @NotNull List<AndroidSourceSet> mySourceSets;

  private @NotNull ASGallery<TemplateHandle> myActivityGallery;
  private @NotNull JComponent myRootPanel;

  private @Nullable AndroidFacet myFacet;

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull List<TemplateHandle> templateList,
                                @NotNull List<AndroidSourceSet> sourceSets) {
    this(moduleModel, renderModel);
    init(templateList, sourceSets, null);
  }

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull AndroidFacet facet,
                                @NotNull List<TemplateHandle> templateList,
                                @NotNull VirtualFile targetDirectory) {
    this(moduleModel, renderModel);
    List<AndroidSourceSet> sourceSets = AndroidSourceSet.getSourceSets(facet, targetDirectory);
    init(templateList, sourceSets, facet);
  }

  private ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel, @NotNull RenderTemplateModel renderModel) {
    super(moduleModel, "Add an Activity to " + renderModel.getTemplateHandle().getMetadata().getFormFactor());
    this.myRenderModel = renderModel;
  }

  private void init(@NotNull List<TemplateHandle> templateList,
                    @NotNull List<AndroidSourceSet> sourceSets,
                    @Nullable AndroidFacet facet) {
    myTemplateList = templateList.toArray(new TemplateHandle[templateList.size()]);
    mySourceSets = sourceSets;

    myActivityGallery = createGallery(getTitle());
    myRootPanel = new JBScrollPane(myActivityGallery);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
    myFacet = facet;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myActivityGallery;
  }

  @NotNull
  @Override
  public Collection<? extends ModelWizardStep> createDependentSteps() {
    String title = AndroidBundle.message("android.wizard.config.activity.title");
    return Lists.newArrayList(new ConfigureTemplateParametersStep(myRenderModel, title, mySourceSets, myFacet));
  }

  private static ASGallery<TemplateHandle> createGallery(String title) {
    ASGallery<TemplateHandle> gallery = new ASGallery<TemplateHandle>(
      JBList.createDefaultListModel(),
      ChooseActivityTypeStep::getImage,
      ChooseActivityTypeStep::getTemplateTitle,
      DEFAULT_GALLERY_THUMBNAIL_SIZE,
      null
    ) {

      @Override
      public Dimension getPreferredScrollableViewportSize() {
        Dimension cellSize = computeCellSize();
        int heightInsets = getInsets().top + getInsets().bottom;
        int widthInsets = getInsets().left + getInsets().right;
        // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
        return new Dimension(cellSize.width * 5 + widthInsets, (int)(cellSize.height * 2.2) + heightInsets);
      }
    };

    gallery.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    AccessibleContextUtil.setDescription(gallery, title);

    return gallery;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myActivityGallery.setModel(JBList.createDefaultListModel((Object[])myTemplateList));
    myActivityGallery.setDefaultAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        wizard.goForward();
      }
    });

    myActivityGallery.addListSelectionListener(listSelectionEvent -> {
      TemplateHandle selectedTemplate = myActivityGallery.getSelectedElement();
      if (selectedTemplate != null) {
        myRenderModel.setTemplateHandle(selectedTemplate);
      }
    });

    int defaultSelection = getDefaultSelectedTemplateIndex(myTemplateList);
    myActivityGallery.setSelectedIndex(defaultSelection); // Also fires the Selection Listener
  }

  @Override
  protected void onProceeding() {
    // TODO: From David: Can we look into moving this logic into handleFinished?
    // There should be multiple hashtables that a model points to, which gets merged at the last second. That way, we can clear one of the
    // hashtables.

    getModel().getRenderTemplateValues().setValue(myRenderModel.getTemplateValues());

    new TemplateValueInjector(getModel().getTemplateValues())
      .setProjectDefaults(getModel().getProject().getValueOrNull(), getModel().applicationName().get());
  }

  /**
   * Return the image associated with the current template, if it specifies one, or null otherwise.
   */
  @Nullable
  private static Image getImage(TemplateHandle template) {
    String thumb = template.getMetadata().getThumbnailPath();
    if (thumb != null && !thumb.isEmpty()) {
      try {
        File file = new File(template.getRootPath(), thumb.replace('/', File.separatorChar));
        return file.isFile() ? ImageIO.read(file) : null;
      }
      catch (IOException e) {
        Logger.getInstance(ActivityGalleryStep.class).warn(e);
      }
    }
    return null;
  }

  @NotNull
  private static String getTemplateTitle(TemplateHandle templateHandle) {
    return templateHandle == null ? "<none>" : templateHandle.getMetadata().getTitle();
  }

  private static int getDefaultSelectedTemplateIndex(@NotNull TemplateHandle[] templateList) {
    for (int i = 0; i < templateList.length; i++) {
      if (getTemplateTitle(templateList[i]).equals("Empty Activity")) {
        return i;
      }
    }
    return 0;
  }
}
