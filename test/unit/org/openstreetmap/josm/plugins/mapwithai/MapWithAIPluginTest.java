// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.swing.JMenu;

import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 */
public class MapWithAIPluginTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main();

    public PluginInformation info;
    public MapWithAIPlugin plugin;

    private static final String VERSION = "no-such-version";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        final InputStream in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        info = new PluginInformation(in, "MapWithAI", null);
        info.localversion = VERSION;
    }

    /**
     * Test method for {@link MapWithAIPlugin#getPreferenceSetting()}.
     */
    @Test
    public void testGetPreferenceSetting() {
        plugin = new MapWithAIPlugin(info);
        Assert.assertTrue(plugin.getPreferenceSetting() instanceof MapWithAIPreferences);
    }

    /**
     * Test method for {@link MapWithAIPlugin#MapWithAIPlugin(PluginInformation)}.
     */
    @Test
    public void testMapWithAIPlugin() {
        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        final int dataMenuSize = dataMenu.getMenuComponentCount();
        plugin = new MapWithAIPlugin(info);
        Assert.assertEquals(dataMenuSize + 4, dataMenu.getMenuComponentCount());
        Assert.assertEquals(1, MapPaintStyles.getStyles().getStyleSources().parallelStream()
                .filter(source -> source.url != null && source.name.contains("MapWithAI")).count());

        for (boolean existed : Arrays.asList(false, true)) { // false, true order is important
            plugin = new MapWithAIPlugin(info);
            Config.getPref().putBoolean(MapWithAIPlugin.PAINTSTYLE_PREEXISTS, existed);
            plugin.destroy();
            Assert.assertEquals(dataMenuSize, dataMenu.getMenuComponentCount());
            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> existed == MapWithAIDataUtils.checkIfMapWithAIPaintStyleExists());
            Assert.assertEquals(Config.getPref().getBoolean(MapWithAIPlugin.PAINTSTYLE_PREEXISTS) ? 1 : 0,
                    MapPaintStyles.getStyles().getStyleSources().parallelStream()
                            .filter(source -> source.url != null && source.name.contains("MapWithAI")).count());
        }

        for (int i = 0; i < 3; i++) {
            plugin = new MapWithAIPlugin(info);
            Assert.assertEquals(dataMenuSize + 4, dataMenu.getMenuComponentCount());
            Assert.assertEquals(1, MapPaintStyles.getStyles().getStyleSources().parallelStream()
                    .filter(source -> source.url != null && source.name.contains("MapWithAI")).count());
        }
    }

    /**
     * Test method for {@link MapWithAIPlugin#getVersionInfo()}.
     */
    @Test
    public void testGetVersionInfo() {
        plugin = new MapWithAIPlugin(info); // needs to be called for version info to be initialized.
        Assert.assertEquals(VERSION, MapWithAIPlugin.getVersionInfo());
    }

}
