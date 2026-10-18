package com.memoraai.dto;

import java.util.HashMap;
import java.util.Map;

public class GraphNodeDto {

    private String id;
    private String label;
    private String type;
    private Map<String, Object> properties;

    public GraphNodeDto() {
        this.properties = new HashMap<>();
    }

    public GraphNodeDto(String id, String label, String type) {
        this(id, label, type, new HashMap<>());
    }

    public GraphNodeDto(String id, String label, String type, Map<String, Object> properties) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.properties = properties != null ? properties : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties != null ? properties : new HashMap<>();
    }
}
