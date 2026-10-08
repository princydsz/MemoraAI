package com.memoraai.dto;

import java.util.List;

public class EmbeddingResponse {
    private List<Double> embedding;
    private int dimension;
    private String model;

    public EmbeddingResponse() {
    }

    public EmbeddingResponse(List<Double> embedding, int dimension, String model) {
        this.embedding = embedding;
        this.dimension = dimension;
        this.model = model;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
