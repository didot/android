package icons;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class AndroidIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AndroidIcons.class);
  }

  /** 16x16 */ public static final @NotNull Icon Android = load("icons/android.svg");

  public static class DeviceExplorer {
    public static final Icon DevicesLineup = load("/icons/explorer/devices-lineup.png"); // 300x150
  }
}
