package org.jetbrains.android.augment;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for light implementations of inner classes of the R class, e.g. {@code R.string}.
 *
 * <p>Implementations need to implement {@link #doGetFields()}, most likely by calling one of the {@code buildResourceFields} methods.
 */
public abstract class InnerRClassBase extends AndroidLightInnerClassBase {
  private static final Logger LOG = Logger.getInstance(InnerRClassBase.class);

  @NotNull
  protected final ResourceType myResourceType;

  @Nullable
  private CachedValue<PsiField[]> myFieldsCache;

  protected static PsiType INT_ARRAY = PsiType.INT.createArrayType();

  public InnerRClassBase(@NotNull PsiClass context, @NotNull ResourceType resourceType) {
    super(context, resourceType.getName());
    myResourceType = resourceType;
  }

  @NotNull
  protected static PsiField[] buildResourceFields(@NotNull ResourceRepository repository,
                                                  @NotNull ResourceNamespace namespace,
                                                  @NotNull AndroidLightField.FieldModifier fieldModifier,
                                                  @NotNull BiPredicate<ResourceType, String> isPublic,
                                                  @NotNull ResourceType resourceType,
                                                  @NotNull PsiClass context) {
    Collection<String> styleableFields = new ArrayList<>();
    Collection<StyleableAttrFieldUrl> styleableAttrFields = new ArrayList<>();
    Collection<String> otherFields = new ArrayList<>();

    if (resourceType != ResourceType.STYLEABLE) {
      otherFields.addAll(repository.getResources(namespace, resourceType).keySet());
    }
    else {
      styleableFields.addAll(repository.getResources(namespace, resourceType).keySet());

      Collection<ResourceItem> items = repository.getResources(namespace, ResourceType.STYLEABLE).values();
      for (ResourceItem item : items) {
        StyleableResourceValue value = (StyleableResourceValue)item.getResourceValue();
        if (value != null) {
          List<AttrResourceValue> attributes = value.getAllAttributes();
          for (AttrResourceValue attr : attributes) {
            if (isPublic.test(attr.getResourceType(), attr.getName())) {
              ResourceNamespace attrNamespace = attr.getNamespace();
              styleableAttrFields.add(new StyleableAttrFieldUrl(
                new ResourceReference(namespace, ResourceType.STYLEABLE, item.getName()),
                new ResourceReference(attrNamespace, ResourceType.ATTR, attr.getName())
              ));
            }
          }
        }
      }
    }

    return buildResourceFields(otherFields, styleableFields, styleableAttrFields, resourceType, context, fieldModifier);
  }

  @NotNull
  protected static PsiField[] buildResourceFields(@NotNull Collection<String> otherFields,
                                                  @NotNull Collection<String> styleableFields,
                                                  @NotNull Collection<StyleableAttrFieldUrl> styleableAttrFields,
                                                  @NotNull ResourceType resourceType,
                                                  @NotNull PsiClass context,
                                                  @NotNull AndroidLightField.FieldModifier fieldModifier) {
    PsiField[] result = new PsiField[otherFields.size() + styleableFields.size() + styleableAttrFields.size()];
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());

    int nextId = resourceType.ordinal() * 100000;
    int i = 0;

    for (String fieldName : otherFields) {
      int fieldId = nextId++;
      AndroidLightField field = new AndroidLightField(AndroidResourceUtil.getFieldNameByResourceName(fieldName),
                                                      context,
                                                      PsiType.INT,
                                                      fieldModifier,
                                                      fieldModifier == AndroidLightField.FieldModifier.FINAL ? fieldId : null);
      field.setInitializer(factory.createExpressionFromText(Integer.toString(fieldId), field));
      result[i++] = field;
    }

    for (String fieldName : styleableFields) {
      int fieldId = nextId++;
      AndroidLightField field = new AndroidLightField(AndroidResourceUtil.getFieldNameByResourceName(fieldName),
                                                      context,
                                                      INT_ARRAY,
                                                      fieldModifier,
                                                      fieldModifier == AndroidLightField.FieldModifier.FINAL ? fieldId : null);
      field.setInitializer(factory.createExpressionFromText(Integer.toString(fieldId), field));
      result[i++] = field;
    }

    for (StyleableAttrFieldUrl fieldContents : styleableAttrFields) {
      int fieldId = nextId++;
      AndroidLightField field = new AndroidStyleableAttrLightField(fieldContents,
                                                                   context,
                                                                   fieldModifier,
                                                                   fieldModifier == AndroidLightField.FieldModifier.FINAL ? fieldId : null);
      field.setInitializer(factory.createExpressionFromText(Integer.toString(fieldId), field));
      result[i++] = field;
    }

    return result;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    if (myFieldsCache == null) {
      myFieldsCache = CachedValuesManager.getManager(getProject()).createCachedValue(
        () -> {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Recomputing fields for " + this);
          }
          return CachedValueProvider.Result.create(doGetFields(), getFieldsDependencies());
        });
    }
    return myFieldsCache.getValue();
  }

  @NotNull
  protected abstract PsiField[] doGetFields();

  /**
   * Dependencies (as defined by {@link CachedValueProvider.Result#getDependencyItems()}) for the cached set of inner classes computed by
   * {@link #doGetFields()}.
   */
  @NotNull
  protected abstract Object[] getFieldsDependencies();

  @NotNull
  public ResourceType getResourceType() {
    return myResourceType;
  }
}
