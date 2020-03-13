package icons;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

public class AndroidIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AndroidIcons.class);
  }

  public static final Icon Android = load("/icons/android.svg"); // 16x16

  public static final Icon AvdManager = load("/icons/avd_manager.png"); // 16x16
  public static final Icon SdkManager = load("/icons/sdk_manager.png"); // 16x16

  public static final Icon Renderscript = load("/icons/render-script.png"); // 16x16
  public static final Icon GreyArrowDown = load("/icons/dropArrow.png"); // 20x20
  public static final Icon NotMatch = load("/icons/notMatch.png");

  public static final Icon AndroidFile = load("/icons/android_file.png"); // 16x16
  public static final Icon Activity = load("/icons/activity.png"); // 16x16
  public static final Icon Targets = load("/icons/targets.png"); // 16x16
  public static final Icon Square = load("/icons/square.png"); // 16x16
  public static final Icon Landscape = load("/icons/landscape.png"); // 16x16
  public static final Icon Portrait = load("/icons/portrait.png"); // 16x16
  public static final Icon Display = load("/icons/display.png"); // 16x16
  public static final Icon ThemesPreview = load("/icons/themesPreview.png"); // 13x13

  public static final Icon EmptyFlag = load("/icons/flags/flag_empty.png"); // 16x16


  public static final Icon Variant = load("/icons/variant.png");

  public static final Icon GreyQuestionMark = load("/icons/grey_question.png"); // 23x23

  public static class Wizards {
    public static final Icon StudioProductIcon = load("/icons/wizards/studio_product.png"); // 60x60
    // Template thumbnails
    public static final Icon AndroidModule = load("/icons/wizards/android_module.png"); // 256x256
    public static final Icon AutomotiveModule = load("/icons/wizards/automotive_module.png"); // 256x256
    public static final Icon BenchmarkModule = load("/icons/wizards/benchmark_module.png"); // 256x256
    public static final Icon DynamicFeatureModule = load("/icons/wizards/dynamic_feature_module.png"); // 256x256
    public static final Icon EclipseModule = load("/icons/wizards/eclipse_module.png"); // 256x256
    public static final Icon GradleModule = load("/icons/wizards/gradle_module.png"); // 256x256
    public static final Icon InstantDynamicFeatureModule = load("/icons/wizards/instant_dynamic_feature_module.png"); // 256x256
    public static final Icon MobileModule = load("/icons/wizards/mobile_module.png"); // 256x256
    public static final Icon ThingsModule = load("/icons/wizards/things_module.png"); // 256x256
    public static final Icon TvModule = load("/icons/wizards/tv_module.png"); // 256x256
    public static final Icon WearModule = load("/icons/wizards/wear_module.png"); // 256x256
    public static final Icon NavigationDrawer = load("/icons/wizards/navigation/navigation_drawer.png"); // 256x256
    public static final Icon BottomNavigation = load("/icons/wizards/navigation/bottom_navigation.png"); // 256x256
    public static final Icon NavigationTabs = load("/icons/wizards/navigation/navigation_tabs.png"); // 256x256
    public static final Icon CppConfiguration = load("/icons/wizards/cpp_configure.png"); // 256x256
    public static final Icon NoActivity = load("/icons/wizards/no_activity.png"); // 256x256
  }

  public static class SherpaIcons {
    public static final Icon Layer = load("/icons/sherpa/switch_blueprint_off.png");
  }

  public static class ToolWindows {
    public static final Icon Warning = IconLoader.getIcon("/icons/toolwindows/toolWindowWarning.svg"); // 13x13
  }

  public static class Issue {
    public static final Icon ErrorBadge = load("/icons/nele/issue/error-badge.png"); // 8x8
    public static final Icon WarningBadge = load("/icons/nele/issue/warning-badge.png"); // 8x8
  }

  public static class DeviceExplorer {
    public static final Icon DatabaseFolder = load("/icons/explorer/DatabaseFolder.png"); // 16x16
    public static final Icon DevicesLineup = load("/icons/explorer/devices-lineup.png"); // 300x150
  }

  public static class Assistant {
    public static final Icon TutorialIndicator = load("/icons/assistant/tutorialIndicator.png"); // 16x16
  }

  public static class Mockup {
    public static final Icon Mockup = load("/icons/mockup/mockup.png"); // 16x16
    public static final Icon Crop = load("/icons/mockup/crop.png"); // 16x16
    public static final Icon CreateWidget = load("/icons/mockup/mockup_add.png"); // 16x16
    public static final Icon CreateLayout = load("/icons/mockup/new_layout.png"); // 16x16
    public static final Icon MatchWidget = load("/icons/mockup/aspect_ratio.png"); // 16x16
    public static final Icon NoMockup = load("/icons/mockup/no_mockup.png"); // 100x100
    public static final Icon ExtractBg = load("/icons/mockup/extract_bg.png"); // 16x16
  }
}
