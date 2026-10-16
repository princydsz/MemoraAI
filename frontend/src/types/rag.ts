export interface AskMemoryRequest {
  question: string;
}

export interface SourceMemory {
  id: string;
  title: string;
  category?: string;
  tags?: string;
  createdAt: string;
  similarityScore: number;
}

export interface AskMemoryResponse {
  answer: string;
  sources: SourceMemory[];
  retrievedCount: number;
}
