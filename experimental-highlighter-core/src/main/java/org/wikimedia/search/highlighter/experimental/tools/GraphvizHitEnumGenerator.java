package org.wikimedia.search.highlighter.experimental.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.wikimedia.search.highlighter.experimental.HitEnum;

/**
 * Tool that generates a graph by reading the HitEnum structure
 */
public class GraphvizHitEnumGenerator {
    private final StringBuilder graph = new StringBuilder();
    private final Map<HitEnum, Integer> hitEnumIds = new HashMap<HitEnum, Integer>();
    private final List<Link> links = new ArrayList<>();

    private int hitEnumIdSequence;

    /**
     * @return Generate the Graph of the HitEnum state
     */
    public String generateGraph(HitEnum hitEnum) {
        graph.setLength(0);
        links.clear();
        graph.append("digraph HitEnums {\n");
        graph.append(" {\n");
        graph.append("  node [shape=record]\n");
        hitEnum.toGraph(this);
        graph.append(" }\n");
        writeLinks();
        graph.append('}');
        return graph.toString();
    }

    public void addChild(HitEnum parent, HitEnum child) {
        addLink(parent, child);
        child.toGraph(this);
    }

    /**
     * Add a node to the current graph
     * @param hitEnum
     */
    public void addNode(HitEnum hitEnum) {
        addNode(hitEnum, Collections.<String, Object>emptyMap());
    }

    /**
     * Add a node to the current graph with extra infos
     * @param hitEnum
     * @param params
     * @return the node id
     */
    public int addNode(HitEnum hitEnum, Map<String, Object> params) {
        int id = getHitEnumId(hitEnum);
        graph.append("  ")
            .append(id)
            .append(" [label=\"{")
            .append(hitEnum.getClass().getSimpleName())
            .append("|{")
            .append("pos: ").append(hitEnum.position())
            .append(", offsets: ")
            .append('[').append(hitEnum.startOffset())
            .append(", ").append(hitEnum.endOffset()).append(']')
            .append("\\l}")
            .append("|{corpusW:").append(hitEnum.corpusWeight())
            .append(", queryW:").append(hitEnum.queryWeight())
            .append("\\l}");

        appendKeyValue("source", hitEnum.source());

        if (!params.isEmpty()) {
            for (Map.Entry<String, Object> en : params.entrySet()) {
                appendKeyValue(en.getKey(), en.getValue());
            }
        }
        graph.append("}\"]\n");
        return id;
    }

    private int getHitEnumId(HitEnum hitEnum) {
        return hitEnumIds.computeIfAbsent(hitEnum, k -> hitEnumIdSequence++);
    }

    private void writeLinks() {
        for (Link link : links) {
            graph.append(' ').append(link.from).append(" -> ").append(link.to).append('\n');
        }
    }

    private void appendKeyValue(String name, Object value) {
        graph.append("|{")
            .append(name)
            .append(" : ")
            .append(value)
            .append("\\l}");
    }

    /**
     * Add a link between two nodes.
     */
    public void addLink(HitEnum parent, HitEnum child) {
        links.add(new Link(getHitEnumId(parent), getHitEnumId(child)));
    }

    private static final class Link {
        private final int from;
        private final int to;
        private Link(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }
}
