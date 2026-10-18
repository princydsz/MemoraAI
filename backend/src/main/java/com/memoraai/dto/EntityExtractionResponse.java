package com.memoraai.dto;

import java.util.ArrayList;
import java.util.List;

public class EntityExtractionResponse {

    private List<String> people = new ArrayList<>();
    private List<String> places = new ArrayList<>();

    public EntityExtractionResponse() {
    }

    public EntityExtractionResponse(List<String> people, List<String> places) {
        this.people = people != null ? people : new ArrayList<>();
        this.places = places != null ? places : new ArrayList<>();
    }

    public List<String> getPeople() {
        return people;
    }

    public void setPeople(List<String> people) {
        this.people = people != null ? people : new ArrayList<>();
    }

    public List<String> getPlaces() {
        return places;
    }

    public void setPlaces(List<String> places) {
        this.places = places != null ? places : new ArrayList<>();
    }
}
