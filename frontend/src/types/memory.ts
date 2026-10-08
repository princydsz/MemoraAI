export interface Memory {
  id: string;
  title: string;
  content: string;
  category?: string;
  tags?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMemoryData {
  title: string;
  content: string;
  category?: string;
  tags?: string;
}

export interface UpdateMemoryData {
  title: string;
  content: string;
  category?: string;
  tags?: string;
}

export interface AiProcessResult {
  summary: string;
  suggestedCategory: string;
  suggestedTags: string[];
}

export interface MemorySearchResult extends Memory {
  similarityScore?: number;
}

export interface ReindexResult {
  message: string;
  reindexedCount: number;
}
