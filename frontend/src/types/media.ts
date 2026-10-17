export interface MemoryMedia {
  id: string;
  memoryId: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  createdAt: string;
}

export interface MediaValidationResult {
  valid: boolean;
  error?: string;
}
