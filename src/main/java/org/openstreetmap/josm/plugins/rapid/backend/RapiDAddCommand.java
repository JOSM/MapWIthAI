// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.AddPrimitivesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.MergeSourceBuildingVisitor;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

public class RapiDAddCommand extends Command {
	DataSet editable;
	DataSet rapid;
	Collection<OsmPrimitive> primitives;
	AddPrimitivesCommand addPrimitivesCommand;
	Collection<OsmPrimitive> modifiedPrimitives;

	/**
	 * Add primitives from RapiD to the OSM data layer
	 *
	 * @param rapid     The rapid dataset
	 * @param editable  The OSM dataset
	 * @param selection The primitives to add from RapiD
	 */
	public RapiDAddCommand(DataSet rapid, DataSet editable, Collection<OsmPrimitive> selection) {
		super(rapid);
		this.rapid = rapid;
		this.editable = editable;
		this.primitives = selection;
		modifiedPrimitives = null;
	}

	@Override
	public boolean executeCommand() {
		if (rapid.equals(editable)) {
			Logging.error("{0}: DataSet rapid ({1}) should not be the same as DataSet editable ({2})", RapiDPlugin.NAME,
					rapid, editable);
			throw new IllegalArgumentException();
		}
		primitives = new HashSet<>(primitives);
		addPrimitivesToCollection(/* collection= */ primitives, /* primitives= */ primitives);
		synchronized (this) {
			rapid.unlock();
			Collection<OsmPrimitive> newPrimitives = new TreeSet<>(moveCollection(rapid, editable, primitives));
			createConnections(editable, newPrimitives);
			rapid.lock();
		}
		return true;
	}

	/**
	 * Create connections based off of current RapiD syntax
	 *
	 * @param collection The primitives with connection information (currently only
	 *                   checks Nodes)
	 */
	public static void createConnections(DataSet dataSet, Collection<OsmPrimitive> collection) {
		Collection<Node> nodes = Utils.filteredCollection(collection, Node.class);
		for (Node node : nodes) {
			if (node.hasKey("conn")) {
				// Currently w<way id>,n<node1>,n<node2>
				String[] connections = node.get("conn").split(",");
				OsmPrimitive[] primitiveConnections = new OsmPrimitive[connections.length];
				for (int i = 0; i < connections.length; i++) {
					String member = connections[i];
					long id = Long.parseLong(member.substring(1));
					char firstChar = member.charAt(0);
					if (firstChar == 'w') {
						primitiveConnections[i] = dataSet.getPrimitiveById(id, OsmPrimitiveType.WAY);
					} else if (firstChar == 'n') {
						primitiveConnections[i] = dataSet.getPrimitiveById(id, OsmPrimitiveType.NODE);
					} else if (firstChar == 'r') {
						primitiveConnections[i] = dataSet.getPrimitiveById(id, OsmPrimitiveType.RELATION);
					}
				}
				for (int i = 0; i < primitiveConnections.length / 3; i++) {
					if (primitiveConnections[i] instanceof Way && primitiveConnections[i + 1] instanceof Node
							&& primitiveConnections[i + 2] instanceof Node) {
						addNodesToWay(node, (Way) primitiveConnections[i], (Node) primitiveConnections[i + 1],
								(Node) primitiveConnections[i + 2]);
					} else {
						Logging.error("{0}: {1}, {2}: {3}, {4}: {5}", i, primitiveConnections[i].getClass(), i + 1,
								primitiveConnections[i + 1].getClass(), i + 2, primitiveConnections[i + 2].getClass());
					}
				}
				Logging.error("RapiD: Removing conn from {0} in {1}", node, dataSet.getName());
				node.remove("conn");
			}
		}
	}

	/**
	 * Add a node to a way
	 *
	 * @param toAddNode The node to add
	 * @param way       The way to add the node to
	 * @param first     The first node in a waysegment (the node is between this and
	 *                  the second node)
	 * @param second    The second node in a waysegemnt
	 * @param recursion The recursion (how many times this has called itself). Use 0
	 *                  when calling.
	 */
	public static void addNodesToWay(Node toAddNode, Way way, Node first, Node second) {
		int index = Math.min(way.getNodes().indexOf(first), way.getNodes().indexOf(second));
		way.addNode(index, toAddNode);
	}

	/**
	 * Move primitives from one dataset to another
	 *
	 * @param to        The receiving dataset
	 * @param from      The sending dataset
	 * @param selection The primitives to move
	 * @return true if the primitives have moved datasets
	 */
	public Collection<? extends OsmPrimitive> moveCollection(DataSet from, DataSet to,
			Collection<OsmPrimitive> selection) {
		if (from == null || to.isLocked() || from.isLocked()) {
			Logging.error("RapiD: Cannot move primitives from {0} to {1}", from, to);
			return Collections.emptySet();
		}
		Collection<OsmPrimitive> originalSelection = from.getSelected();
		from.setSelected(selection);
		MergeSourceBuildingVisitor mergeBuilder = new MergeSourceBuildingVisitor(from);
		List<PrimitiveData> primitiveDataList = mergeBuilder.build().allPrimitives().stream().map(OsmPrimitive::save)
				.collect(Collectors.toList());
		from.setSelected(originalSelection);
		addPrimitivesCommand = new AddPrimitivesCommand(primitiveDataList, primitiveDataList, to);
		addPrimitivesCommand.executeCommand();
		return addPrimitivesCommand.getParticipatingPrimitives();
	}

	/**
	 * Add primitives and their children to a collection
	 *
	 * @param collection A collection to add the primitives to
	 * @param primitives The primitives to add to the collection
	 */
	public static void addPrimitivesToCollection(Collection<OsmPrimitive> collection,
			Collection<OsmPrimitive> primitives) {
		for (OsmPrimitive primitive : primitives) {
			if (primitive instanceof Way) {
				collection.addAll(((Way) primitive).getNodes());
			} else if (primitive instanceof Relation) {
				addPrimitivesToCollection(collection, ((Relation) primitive).getMemberPrimitives());
			}
			collection.add(primitive);
		}
	}

	@Override
	public String getDescriptionText() {
		return tr("Add object from RapiD");
	}

	@Override
	public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
			Collection<OsmPrimitive> added) {
		// TODO Auto-generated method stub

	}

}
