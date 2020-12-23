// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.awaitility.Durations;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAIPluginMock;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Territories;

import com.github.tomakehurst.wiremock.WireMockServer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAILayerTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection()
    .fakeAPI().territories();

    WireMockServer wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));

    MapWithAILayer layer;

    @BeforeClass
    public static void beforeAll() {
        TestUtils.assumeWorkingJMockit();
        new MapWithAIPluginMock();
        Territories.initialize(); // Required to avoid an NPE (see JOSM-19132)
    }

    @Before
    public void setUp() {
        wireMock.start();
        MapWithAIPreferenceHelper.setMapWithAIURLs(MapWithAIPreferenceHelper.getMapWithAIURLs().stream().map(map -> {
            map.put("url", map.getOrDefault("url", MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API)
                    .replace("https://www.facebook.com", wireMock.baseUrl()));
            return map;
        }).collect(Collectors.toList()));
        layer = new MapWithAILayer(new DataSet(), "test", null);
    }

    @After
    public void tearDown() {
        wireMock.stop();
    }

    @Test
    public void testGetSource() {
        Assert.assertNull(layer.getChangesetSourceTag());
        DataSet to = new DataSet();
        DataSet from = new DataSet();
        Way way = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        way.getNodes().stream().forEach(from::addPrimitive);
        from.addPrimitive(way);
        way.put(GetDataRunnable.MAPWITHAI_SOURCE_TAG_KEY, MapWithAIPlugin.NAME);
        MapWithAIAddCommand command = new MapWithAIAddCommand(from, to, Collections.singleton(way));
        UndoRedoHandler.getInstance().add(command);
        Assert.assertNotNull(layer.getChangesetSourceTag());
        Assert.assertFalse(layer.getChangesetSourceTag().trim().isEmpty());
        Assert.assertEquals(MapWithAIPlugin.NAME, layer.getChangesetSourceTag());
    }

    @Test
    public void testGetInfoComponent() {
        final Object tObject = layer.getInfoComponent();
        Assert.assertTrue(tObject instanceof JPanel);

        JPanel jPanel = (JPanel) tObject;
        final List<Component> startComponents = Arrays.asList(jPanel.getComponents());
        for (final Component comp : startComponents) {
            final JLabel label = (JLabel) comp;
            Assert.assertFalse(label.getText().contains("URL"));
            Assert.assertFalse(label.getText().contains("Maximum Additions"));
            Assert.assertFalse(label.getText().contains("Switch Layers"));
        }

        layer.setMapWithAIUrl("bad_url");
        layer.setMaximumAddition(0);
        layer.setSwitchLayers(false);

        jPanel = (JPanel) layer.getInfoComponent();
        final List<Component> currentComponents = Arrays.asList(jPanel.getComponents());

        for (final Component comp : currentComponents) {
            final JLabel label = (JLabel) comp;
            if (label.getText().contains("URL")) {
                Assert.assertEquals(tr("URL: {0}", "bad_url"), label.getText());
            } else if (label.getText().contains("Maximum Additions")) {
                Assert.assertEquals(tr("Maximum Additions: {0}", 0), label.getText());
            } else if (label.getText().contains("Switch Layers")) {
                Assert.assertEquals(tr("Switch Layers: {0}", false), label.getText());
            }
        }
    }

    @Test
    public void testGetLayer() {
        Layer mapWithAILayer = MapWithAIDataUtils.getLayer(false);
        Assert.assertNull(mapWithAILayer);

        mapWithAILayer = MapWithAIDataUtils.getLayer(true);
        Assert.assertEquals(MapWithAILayer.class, mapWithAILayer.getClass());

        Layer tMapWithAI = MapWithAIDataUtils.getLayer(false);
        Assert.assertSame(mapWithAILayer, tMapWithAI);

        tMapWithAI = MapWithAIDataUtils.getLayer(true);
        Assert.assertSame(mapWithAILayer, tMapWithAI);
    }

    @Test
    public void testSelection() throws InvocationTargetException, InterruptedException {
        MapWithAILayer mapWithAILayer = MapWithAIDataUtils.getLayer(true);
        DataSet ds = mapWithAILayer.getDataSet();
        new GetDataRunnable(Arrays.asList(new BBox(-5.7400005, 34.4524384, -5.6686014, 34.5513153)), ds, null).fork()
        .join();
        assertTrue(ds.getSelected().isEmpty());
        SwingUtilities.invokeAndWait(() -> ds.setSelected(ds.allNonDeletedCompletePrimitives()));
        assertEquals(1, ds.getSelected().size());
        OsmPrimitive prim = ds.getSelected().iterator().next();
        assertTrue(prim instanceof Way);
        SwingUtilities.invokeAndWait(() -> ds.setSelected(((Way) prim).getNodes()));
        assertEquals(((Way) prim).getNodes().size(), ds.getSelected().size());
        assertTrue(((Way) prim).getNodes().parallelStream().allMatch(ds::isSelected));
    }

    @Test
    public void testGetData() {
        final MapWithAILayer mapWithAILayer = MapWithAIDataUtils.getLayer(true);
        final OsmDataLayer osm = new OsmDataLayer(new DataSet(), "test", null);
        MainApplication.getLayerManager().addLayer(osm);
        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer, osm);

        Assert.assertTrue(mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty());

        osm.getDataSet().addDataSource(new DataSource(new Bounds(0, 0, 0.001, 0.001), "random test"));

        osm.lock();
        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        Assert.assertTrue(mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty());
        osm.unlock();

        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        await().atMost(Durations.TEN_SECONDS).until(() -> !mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty());
        Assert.assertFalse(mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty());
        Assert.assertEquals(1, mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count());

        osm.getDataSet().addDataSource(new DataSource(new Bounds(-0.001, -0.001, 0, 0), "random test"));
        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        await().atMost(Durations.TEN_SECONDS).until(
                () -> mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count() == 2);
        Assert.assertEquals(2, mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count());

        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        Assert.assertEquals(2, mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count());
    }
}
