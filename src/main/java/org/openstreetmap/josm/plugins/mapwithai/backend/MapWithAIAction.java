// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.AbstractMergeAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Shortcut;

public class MapWithAIAction extends JosmAction {
    /** UID */
    private static final long serialVersionUID = 8886705479253246588L;

    public MapWithAIAction() {
        super(tr("{0}: Download data", MapWithAIPlugin.NAME), "mapwithai",
                tr("Get data from {0}", MapWithAIPlugin.NAME), Shortcut.registerShortcut("data:mapWithAI",
                        tr("Data: {0}", MapWithAIPlugin.NAME), KeyEvent.VK_R, Shortcut.CTRL),
                true);
        setHelpId(ht("Plugin/MapWithAI#BasicUsage"));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (isEnabled()) {
            final boolean hasLayer = MapWithAIDataUtils.getLayer(false) != null;
            final List<OsmDataLayer> osmLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class)
                    .stream().filter(layer -> !(layer instanceof MapWithAILayer)).filter(Layer::isVisible)
                    .collect(Collectors.toList());
            final OsmDataLayer layer = getOsmLayer(osmLayers);
            if ((layer != null) && MapWithAIDataUtils.getMapWithAIData(MapWithAIDataUtils.getLayer(true), layer)) {
                final Notification notification = createMessageDialog();
                if (notification != null) {
                    notification.show();
                }
            } else if ((layer != null) && hasLayer) {
                toggleLayer(layer);
            }
        }
    }

    /**
     * Get the osm layer that the user wants to use to get data from (doesn't ask if
     * user only has one data layer)
     *
     * @param osmLayers The list of osm data layers
     * @return The layer that the user selects
     */
    protected static OsmDataLayer getOsmLayer(List<OsmDataLayer> osmLayers) {
        OsmDataLayer returnLayer = null;
        if (osmLayers.size() == 1) {
            returnLayer = osmLayers.get(0);
        } else if (!osmLayers.isEmpty()) {
            AbstractMergeAction.askTargetLayer(osmLayers.toArray(new OsmDataLayer[0]),
                    tr("Please select the target layer"), tr("Select target layer"), tr("OK"), "download");
        }
        return returnLayer;
    }

    /**
     * Toggle the layer (the toLayer is the layer to switch to, if currently active
     * it will switch to the MapWithAI layer, if the MapWithAI layer is currently
     * active it will switch to the layer passed)
     *
     * @param toLayer The {@link Layer} to switch to
     */
    protected static void toggleLayer(Layer toLayer) {
        final OsmDataLayer mapwithai = MapWithAIDataUtils.getLayer(false);
        final Layer currentLayer = MainApplication.getLayerManager().getActiveLayer();
        if (currentLayer != null) {
            if (currentLayer.equals(mapwithai) && (toLayer != null)) {
                MainApplication.getLayerManager().setActiveLayer(toLayer);
            } else if (currentLayer.equals(toLayer) && (mapwithai != null)) {
                MainApplication.getLayerManager().setActiveLayer(mapwithai);
            }
        }
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditDataSet() != null);
    }

    /**
     * Create a message dialog to notify the user if data is available in their
     * downloaded region
     *
     * @return A Notification to show ({@link Notification#show})
     */
    public static Notification createMessageDialog() {
        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        final Notification notification = layer == null ? null : new Notification();
        if (notification != null) {
            final List<Bounds> bounds = new ArrayList<>(layer.getDataSet().getDataSourceBounds());
            if (bounds.isEmpty()) {
                MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream()
                        .map(OsmDataLayer::getDataSet).filter(Objects::nonNull).map(DataSet::getDataSourceBounds)
                        .forEach(bounds::addAll);
            }
            final StringBuilder message = new StringBuilder();
            message.append(MapWithAIPlugin.NAME).append(": ");
            final MapWithAIAvailability availability = MapWithAIAvailability.getInstance();
            final Map<String, Boolean> availableTypes = new TreeMap<>();
            for (final Bounds bound : bounds) {
                availability.getDataTypes(bound.getCenter())
                        .forEach((type, available) -> availableTypes.merge(type, available, Boolean::logicalOr));
            }
            final List<String> types = availableTypes.entrySet().stream().filter(Entry::getValue)
                    .map(entry -> MapWithAIAvailability.getPossibleDataTypesAndMessages().get(entry.getKey()))
                    .collect(Collectors.toList());
            if (types.isEmpty()) {
                message.append(tr("No data available"));
            } else {
                message.append("Data available: ").append(String.join(", ", types));
            }

            notification.setContent(message.toString());
            notification.setDuration(Notification.TIME_DEFAULT);
            notification.setIcon(JOptionPane.INFORMATION_MESSAGE);
        }
        return notification;
    }
}
