// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.ConnectedCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DuplicateCommand;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAIPluginMock;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import mockit.Mock;
import mockit.MockUp;

public class MapWithAIMoveActionTest {
    MapWithAIMoveAction moveAction;
    DataSet mapWithAIData;
    OsmDataLayer osmLayer;
    Way way1;
    Way way2;

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection().territories()
    .assertionsInEDT();

    @BeforeClass
    public static void beforeAll() {
        new MapWithAIPluginMock();
    }

    @Before
    public void setUp() {
        moveAction = new MapWithAIMoveAction();
        final DataSet osmData = new DataSet();
        mapWithAIData = new DataSet();
        way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0.1, 0.1)));
        way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(-0.1, -0.1)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().forEach(node -> mapWithAIData.addPrimitive(node));
        way2.getNodes().forEach(node -> osmData.addPrimitive(node));
        osmData.addPrimitive(way2);
        mapWithAIData.addPrimitive(way1);

        osmLayer = new OsmDataLayer(osmData, "osm", null);
        final MapWithAILayer mapWithAILayer = new MapWithAILayer(mapWithAIData, "MapWithAI", null);
        MainApplication.getLayerManager().addLayer(osmLayer);
        MainApplication.getLayerManager().addLayer(mapWithAILayer);
        MainApplication.getLayerManager().setActiveLayer(mapWithAILayer);
    }

    @Test
    public void testMoveAction() {
        mapWithAIData.addSelected(way1);
        moveAction.actionPerformed(null);
        Assert.assertEquals(osmLayer, MainApplication.getLayerManager().getActiveLayer());
        Assert.assertNotNull(osmLayer.getDataSet().getPrimitiveById(way1));
        UndoRedoHandler.getInstance().undo();
        Assert.assertNull(osmLayer.getDataSet().getPrimitiveById(way1));
    }

    @Test
    public void testConflationDupeKeyRemoval() {
        mapWithAIData.unlock();
        way1.lastNode().put(DuplicateCommand.DUPE_KEY, "n" + Long.toString(way2.lastNode().getUniqueId()));
        mapWithAIData.lock();
        mapWithAIData.addSelected(way1);
        final DataSet ds = osmLayer.getDataSet();

        moveAction.actionPerformed(null);
        Assert.assertTrue(
                ((Way) ds.getPrimitiveById(way1)).lastNode().equals(((Way) ds.getPrimitiveById(way2)).lastNode()));
        Assert.assertFalse(((Way) ds.getPrimitiveById(way2)).lastNode().hasKey(DuplicateCommand.DUPE_KEY));
        Assert.assertFalse(((Way) ds.getPrimitiveById(way1)).lastNode().hasKey(DuplicateCommand.DUPE_KEY));

        UndoRedoHandler.getInstance().undo();
        Awaitility.await().atMost(Durations.ONE_SECOND)
                .until(() -> !((Way) ds.getPrimitiveById(way2)).lastNode().hasKey(DuplicateCommand.DUPE_KEY));
        Assert.assertFalse(way2.lastNode().hasKey(DuplicateCommand.DUPE_KEY));
        Assert.assertTrue(way1.lastNode().hasKey(DuplicateCommand.DUPE_KEY));
    }

    @Test
    public void testConflationConnKeyRemoval() {
        mapWithAIData.unlock();
        way1.lastNode().put(ConnectedCommand.CONN_KEY, "w" + Long.toString(way2.getUniqueId()) + ",n"
                + Long.toString(way2.lastNode().getUniqueId()) + ",n" + Long.toString(way2.firstNode().getUniqueId()));
        mapWithAIData.lock();
        mapWithAIData.addSelected(way1);

        moveAction.actionPerformed(null);
        Assert.assertFalse(way2.lastNode().hasKey(ConnectedCommand.CONN_KEY));
        Assert.assertFalse(way2.firstNode().hasKey(ConnectedCommand.CONN_KEY));
        Assert.assertFalse(way2.getNode(1).hasKey(ConnectedCommand.CONN_KEY));
        Assert.assertTrue(way1.lastNode().isDeleted());

        UndoRedoHandler.getInstance().undo();
        Assert.assertFalse(way2.lastNode().hasKey(ConnectedCommand.CONN_KEY));
        Assert.assertTrue(way1.lastNode().hasKey(ConnectedCommand.CONN_KEY));
        Assert.assertFalse(way1.lastNode().isDeleted());
    }

    private static class NotificationMocker extends MockUp<Notification> {
        public boolean shown;

        @Mock
        public void show() {
            shown = true;
        }
    }

    @Test
    public void testMaxAddNotification() {
        TestUtils.assumeWorkingJMockit();
        new WindowMocker();
        NotificationMocker notification = new NotificationMocker();
        DataSet ds = MapWithAIDataUtils.getLayer(true).getDataSet();
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "TEST", null));
        MapWithAIPreferenceHelper.setMaximumAddition(1, false);
        for (int i = 0; i < 40; i++) {
            ds.addPrimitive(new Node(LatLon.ZERO));
        }
        for (int i = 0; i < 11; i++) {
            GuiHelper
            .runInEDTAndWaitWithException(() -> ds.setSelected(ds.allNonDeletedPrimitives().iterator().next()));
            moveAction.actionPerformed(null);
        }
        Assert.assertTrue(notification.shown);
        notification.shown = false;
    }
}
