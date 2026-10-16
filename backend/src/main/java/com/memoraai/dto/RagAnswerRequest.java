package com.memoraai.dto;

import java.util.List;
import java.util.UUID;

public class RagAnswerRequest {

    private String question;
    private List<RagMemoryItem> memories;

    public RagAnswerRequest() {
    }

    public RagAnswerRequest(String question, List<RagMemoryItem> memories) {
        this.question = question;
        this.memories = memories;
    }

    public static class RagMemoryItem {
        private UUID id;
        private String title;
        private String content;
        private String category;
        private String tags;

        public RagMemoryItem() {
        }

        public RagMemoryItem(UUID id, String title, String content, String category, String tags) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.category = category;
            this.tags = tags;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getTags() {
            return tags;
        }

        public void setTags(String tags) {
            this.tags = tags;
        }
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<RagMemoryItem> getMemories() {
        return memories;
    }

    public void setMemories(List<RagMemoryItem> memories) {
        this.memories = memories;
    }
}
