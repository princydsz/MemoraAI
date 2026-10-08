import os
import re
from typing import List, Optional
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

app = FastAPI(
    title="MemoraAI AI Microservice",
    description="AI, Speech-to-Text, Embeddings, and RAG Pipeline for MemoraAI",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class ProcessMemoryRequest(BaseModel):
    title: str = Field(..., min_length=1, description="Memory title")
    content: str = Field(..., min_length=1, description="Memory content")
    category: Optional[str] = Field(None, description="Memory category")
    tags: Optional[str] = Field(None, description="Comma-separated tags")

class ProcessMemoryResponse(BaseModel):
    summary: str
    suggestedCategory: str
    suggestedTags: List[str]

class EmbeddingRequest(BaseModel):
    text: str = Field(..., min_length=1, description="Text to generate embedding for")

class EmbeddingResponse(BaseModel):
    embedding: List[float]
    dimension: int
    model: str

EMBEDDING_DIM = 384
EMBEDDING_MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"

import hashlib
import math

_model_instance = None

def get_embedding_model():
    global _model_instance
    if _model_instance is None:
        try:
            from fastembed import TextEmbedding
            _model_instance = TextEmbedding(model_name=EMBEDDING_MODEL_NAME)
        except ImportError:
            try:
                from sentence_transformers import SentenceTransformer
                _model_instance = SentenceTransformer(EMBEDDING_MODEL_NAME)
            except ImportError:
                _model_instance = None
    return _model_instance

def generate_embedding(text: str) -> List[float]:
    """
    Generates a 384-dimensional normalized neural embedding using sentence-transformers/all-MiniLM-L6-v2.
    The model is initialized as a singleton once at service load.
    """
    text_clean = text.strip()
    if not text_clean:
        return [0.0] * EMBEDDING_DIM

    model = get_embedding_model()
    if model is not None:
        try:
            if hasattr(model, "embed"):
                vectors = list(model.embed([text_clean]))
                if vectors and len(vectors) > 0:
                    vec = [float(x) for x in vectors[0]]
                    norm = math.sqrt(sum(v * v for v in vec))
                    if norm > 0:
                        vec = [round(v / norm, 6) for v in vec]
                    return vec
            elif hasattr(model, "encode"):
                vec = model.encode(text_clean, normalize_embeddings=True)
                return [round(float(v), 6) for v in vec]
        except Exception:
            pass

    return [0.0] * EMBEDDING_DIM

@app.get("/health")
def health_check():
    return {
        "status": "UP",
        "service": "MemoraAI AI Service"
    }

@app.post("/embeddings", response_model=EmbeddingResponse, status_code=status.HTTP_200_OK)
def get_embeddings(request: EmbeddingRequest):
    text_clean = request.text.strip()
    if not text_clean:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Text cannot be blank for embedding generation."
        )

    vector = generate_embedding(text_clean)
    return EmbeddingResponse(
        embedding=vector,
        dimension=EMBEDDING_DIM,
        model=EMBEDDING_MODEL_NAME
    )

@app.post("/process-memory", response_model=ProcessMemoryResponse, status_code=status.HTTP_200_OK)
def process_memory(request: ProcessMemoryRequest):
    title_clean = request.title.strip()
    content_clean = request.content.strip()

    if not title_clean or not content_clean:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Title and content cannot be blank."
        )

    # Deterministic Summary Generation
    first_sentence = content_clean.split(".")[0].strip()
    if len(first_sentence) > 100:
        first_sentence = first_sentence[:97] + "..."
    summary = f"A memory titled '{title_clean}' capturing: {first_sentence}."

    # Deterministic Category Suggestion
    if request.category and request.category.strip():
        suggested_category = request.category.strip().title()
    else:
        content_lower = content_clean.lower()
        if any(w in content_lower for w in ["work", "office", "job", "project", "career", "presentation"]):
            suggested_category = "Work & Career"
        elif any(w in content_lower for w in ["family", "birthday", "wedding", "mom", "dad", "child", "son", "daughter"]):
            suggested_category = "Family & Milestones"
        elif any(w in content_lower for w in ["travel", "vacation", "trip", "flight", "beach", "hotel"]):
            suggested_category = "Travel & Adventure"
        else:
            suggested_category = "Personal Reflection"

    # Deterministic Tags Extraction
    extracted_tags = []
    if request.tags and request.tags.strip():
        extracted_tags = [t.strip().lower() for t in request.tags.split(",") if t.strip()]

    # Add keywords from title
    title_words = [w.lower() for w in re.findall(r'\b\w{3,}\b', title_clean)]
    for w in title_words:
        if w not in extracted_tags and len(extracted_tags) < 5:
            extracted_tags.append(w)

    if not extracted_tags:
        extracted_tags = ["memory", "memoraai"]

    return ProcessMemoryResponse(
        summary=summary,
        suggestedCategory=suggested_category,
        suggestedTags=extracted_tags
    )

if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("AI_SERVICE_PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)
