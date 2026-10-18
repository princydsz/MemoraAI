package com.memoraai.dto;

import java.util.ArrayList;
import java.util.List;

public class MemoryGraphResponse {

    private List<GraphNodeDto> nodes;
    private List<GraphEdgeDto> edges;

    public MemoryGraphResponse() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public MemoryGraphResponse(List<GraphNodeDto> nodes, List<GraphEdgeDto> edges) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
        this.edges = edges != null ? edges : new ArrayList<>();
    }

    public List<GraphNodeDto> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphNodeDto> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public List<GraphEdgeDto> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphEdgeDto> edges) {
        this.edges = edges != null ? edges : new ArrayList<>();
    }
}
