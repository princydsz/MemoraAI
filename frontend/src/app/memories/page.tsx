'use client';

import React, { useEffect, useRef, useState } from 'react';
import ProtectedRoute from '../../components/ProtectedRoute';
import { AiProcessResult, Memory, MemorySearchResult } from '../../types/memory';
import {
  createMemoryApi,
  deleteMemoryApi,
  getMemoriesApi,
  processMemoryApi,
  reindexMemoriesApi,
  searchMemoriesApi,
  updateMemoryApi,
} from '../../lib/memory';
import MediaGallery from '../../components/media/MediaGallery';
import VoiceRecorderModal from '../../components/voice/VoiceRecorderModal';
import {
  formatFileSize,
  MAX_MEDIA_PER_MEMORY,
  uploadMemoryMediaApi,
  validateMediaFiles,
} from '../../lib/media';

export default function MemoryVaultPage() {
  const [memories, setMemories] = useState<Memory[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Semantic Search State
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isSearching, setIsSearching] = useState<boolean>(false);
  const [isSemanticSearchActive, setIsSemanticSearchActive] = useState<boolean>(false);
  const [searchResults, setSearchResults] = useState<MemorySearchResult[]>([]);
  const [isReindexing, setIsReindexing] = useState<boolean>(false);

  // Modal / Form state
  const [isCreateOpen, setIsCreateOpen] = useState<boolean>(false);
  const [isVoiceModalOpen, setIsVoiceModalOpen] = useState<boolean>(false);
  const [isVoiceDictateOpen, setIsVoiceDictateOpen] = useState<boolean>(false);
  const [editingMemory, setEditingMemory] = useState<Memory | null>(null);
  const [viewingMemory, setViewingMemory] = useState<Memory | null>(null);
  const [deletingMemoryId, setDeletingMemoryId] = useState<string | null>(null);

  // AI State
  const [aiProcessingId, setAiProcessingId] = useState<string | null>(null);
  const [aiModalResult, setAiModalResult] = useState<{ memoryId: string; memoryTitle: string; result: AiProcessResult } | null>(null);

  // Review AI Suggestions State for Create Memory Workflow
  const [createdReviewMemory, setCreatedReviewMemory] = useState<Memory | null>(null);
  const [aiReviewResult, setAiReviewResult] = useState<AiProcessResult | null>(null);
  const [reviewCategory, setReviewCategory] = useState('');
  const [reviewTags, setReviewTags] = useState('');

  // Input states
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [category, setCategory] = useState('');
  const [tags, setTags] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Create Modal Media Attachment State
  const [createFiles, setCreateFiles] = useState<File[]>([]);
  const [createPreviews, setCreatePreviews] = useState<{ id: string; file: File; url: string }[]>([]);
  const [pendingCreatedMemory, setPendingCreatedMemory] = useState<Memory | null>(null);
  const [uploadStatusText, setUploadStatusText] = useState<string | null>(null);
  const [isCreateDragging, setIsCreateDragging] = useState<boolean>(false);
  const createFileInputRef = useRef<HTMLInputElement | null>(null);
  const createPreviewsRef = useRef<{ id: string; file: File; url: string }[]>([]);
  createPreviewsRef.current = createPreviews;

  // Cleanup object URLs on unmount to avoid memory leaks
  useEffect(() => {
    return () => {
      createPreviewsRef.current.forEach((p) => URL.revokeObjectURL(p.url));
    };
  }, []);

  const clearCreatePreviews = () => {
    createPreviewsRef.current.forEach((p) => URL.revokeObjectURL(p.url));
    setCreatePreviews([]);
    setCreateFiles([]);
    if (createFileInputRef.current) {
      createFileInputRef.current.value = '';
    }
  };

  const removeCreatePreview = (indexToRemove: number) => {
    const item = createPreviews[indexToRemove];
    if (item) {
      URL.revokeObjectURL(item.url);
    }
    setCreatePreviews((prev) => prev.filter((_, idx) => idx !== indexToRemove));
    setCreateFiles((prev) => prev.filter((_, idx) => idx !== indexToRemove));
  };

  const handleCreateFilesSelected = (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;
    setError(null);

    const filesArray = Array.from(fileList);
    const validation = validateMediaFiles(filesArray, createFiles.length);

    if (!validation.valid) {
      setError(validation.error || 'Selected files failed validation.');
      if (createFileInputRef.current) createFileInputRef.current.value = '';
      return;
    }

    const newPreviews = filesArray.map((file) => ({
      id: `${Date.now()}-${Math.random()}-${file.name}`,
      file,
      url: URL.createObjectURL(file),
    }));

    setCreateFiles((prev) => [...prev, ...filesArray]);
    setCreatePreviews((prev) => [...prev, ...newPreviews]);
    if (createFileInputRef.current) createFileInputRef.current.value = '';
  };

  const closeCreateModal = () => {
    if (isSubmitting) return; // Prevent closing while in-flight request
    clearCreatePreviews();
    setPendingCreatedMemory(null);
    setUploadStatusText(null);
    setIsCreateOpen(false);
  };

  const fetchMemories = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getMemoriesApi();
      setMemories(data);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load memories.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchMemories();
  }, []);

  const handleSemanticSearch = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!searchQuery.trim()) {
      handleClearSearch();
      return;
    }
    setIsSearching(true);
    setError(null);
    try {
      const results = await searchMemoriesApi(searchQuery.trim());
      setSearchResults(results);
      setIsSemanticSearchActive(true);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Semantic search failed.');
      }
    } finally {
      setIsSearching(false);
    }
  };

  const handleClearSearch = () => {
    setSearchQuery('');
    setSearchResults([]);
    setIsSemanticSearchActive(false);
  };

  const handleReindex = async () => {
    setIsReindexing(true);
    setError(null);
    try {
      const res = await reindexMemoriesApi();
      setSuccessMsg(res.message);
      setTimeout(() => setSuccessMsg(null), 3500);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to re-index memories.');
      }
    } finally {
      setIsReindexing(false);
    }
  };

  const openCreateModal = () => {
    setTitle('');
    setContent('');
    setCategory('');
    setTags('');
    setError(null);
    clearCreatePreviews();
    setPendingCreatedMemory(null);
    setUploadStatusText(null);
    setIsCreateOpen(true);
  };

  const openEditModal = (mem: Memory) => {
    setEditingMemory(mem);
    setTitle(mem.title);
    setContent(mem.content);
    setCategory(mem.category || '');
    setTags(mem.tags || '');
    setError(null);
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return; // Prevent duplicate submissions

    if (!title.trim() || !content.trim()) {
      setError('Title and content are required.');
      return;
    }
    setIsSubmitting(true);
    setError(null);

    let memoryToUse = pendingCreatedMemory;

    try {
      // Step 1: Create memory if not already created in a previous attempt
      if (!memoryToUse) {
        setUploadStatusText('Creating memory...');
        const newMemory = await createMemoryApi({
          title: title.trim(),
          content: content.trim(),
          category: category.trim() || undefined,
          tags: tags.trim() || undefined,
        });
        memoryToUse = newMemory;
        setPendingCreatedMemory(newMemory);
        setMemories((prev) => [newMemory, ...prev]);
      }

      // Step 2: Upload attached images if any exist
      if (createFiles.length > 0) {
        setUploadStatusText(`Uploading ${createFiles.length} image${createFiles.length > 1 ? 's' : ''}...`);
        await uploadMemoryMediaApi(memoryToUse.id, createFiles);
      }

      // Step 3: Success! Clean up and close modal
      clearCreatePreviews();
      setPendingCreatedMemory(null);
      setIsCreateOpen(false);
      setSuccessMsg(
        createFiles.length > 0
          ? 'Memory and attachments saved successfully!'
          : 'Memory created successfully!'
      );
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Operation failed.';
      if (memoryToUse) {
        // Memory exists, but image upload failed: retain created memory and files for retry
        setError(
          `Memory "${memoryToUse.title}" was saved, but uploading images failed: ${errMsg}. You can retry uploading your images below, or click Done to finish without images.`
        );
      } else {
        setError(`Failed to create memory: ${errMsg}`);
      }
    } finally {
      setIsSubmitting(false);
      setUploadStatusText(null);
    }
  };

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingMemory) return;
    if (!title.trim() || !content.trim()) {
      setError('Title and content are required.');
      return;
    }
    setIsSubmitting(true);
    setError(null);
    try {
      const updated = await updateMemoryApi(editingMemory.id, {
        title: title.trim(),
        content: content.trim(),
        category: category.trim() || undefined,
        tags: tags.trim() || undefined,
      });
      setMemories((prev) =>
        prev.map((m) => (m.id === updated.id ? updated : m))
      );
      if (viewingMemory?.id === updated.id) {
        setViewingMemory(updated);
      }
      setEditingMemory(null);
      setSuccessMsg('Memory updated successfully!');
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to update memory.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deletingMemoryId) return;
    setIsSubmitting(true);
    setError(null);
    try {
      await deleteMemoryApi(deletingMemoryId);
      setMemories((prev) => prev.filter((m) => m.id !== deletingMemoryId));
      if (viewingMemory?.id === deletingMemoryId) {
        setViewingMemory(null);
      }
      setDeletingMemoryId(null);
      setSuccessMsg('Memory deleted successfully.');
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to delete memory.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleAiProcess = async (mem: Memory) => {
    setAiProcessingId(mem.id);
    setError(null);
    try {
      const result = await processMemoryApi(mem.id);
      setAiModalResult({ memoryId: mem.id, memoryTitle: mem.title, result });
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to process memory with AI.');
      }
    } finally {
      setAiProcessingId(null);
    }
  };

  const handleCreateAndAutoOrganize = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (isSubmitting) return; // Prevent duplicate submissions

    if (!title.trim() || !content.trim()) {
      setError('Title and content are required.');
      return;
    }
    setIsSubmitting(true);
    setError(null);

    let memoryToUse = pendingCreatedMemory;

    try {
      // Step 1: Save memory to DB via existing API if not already created
      if (!memoryToUse) {
        setUploadStatusText('Creating memory...');
        const newMemory = await createMemoryApi({
          title: title.trim(),
          content: content.trim(),
          category: category.trim() || undefined,
          tags: tags.trim() || undefined,
        });
        memoryToUse = newMemory;
        setPendingCreatedMemory(newMemory);
        setMemories((prev) => [newMemory, ...prev]);
      }

      // Step 2: Upload attached images if any exist
      if (createFiles.length > 0) {
        setUploadStatusText(`Uploading ${createFiles.length} image${createFiles.length > 1 ? 's' : ''}...`);
        await uploadMemoryMediaApi(memoryToUse.id, createFiles);
      }

      // Clear preview URLs on successful upload
      clearCreatePreviews();
      setPendingCreatedMemory(null);
      setIsCreateOpen(false);

      // Step 3: Call existing Phase 5 AI process endpoint
      setUploadStatusText('Auto-organizing with AI...');
      const result = await processMemoryApi(memoryToUse.id);

      // Step 4: Open Review AI Suggestions modal with pre-filled AI suggestions
      setCreatedReviewMemory(memoryToUse);
      setAiReviewResult(result);
      setReviewCategory(result.suggestedCategory || memoryToUse.category || '');
      setReviewTags(result.suggestedTags ? result.suggestedTags.join(', ') : (memoryToUse.tags || ''));

    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Operation failed.';
      if (memoryToUse) {
        // Memory exists, but image upload or AI organization failed
        setError(
          `Memory "${memoryToUse.title}" was saved, but an error occurred: ${errMsg}. You can retry uploading your images below, or click Done to finish.`
        );
      } else {
        setError(`Failed to auto-organize memory with AI: ${errMsg}`);
      }
    } finally {
      setIsSubmitting(false);
      setUploadStatusText(null);
    }
  };

  const handleSaveReviewSuggestions = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!createdReviewMemory) return;
    setIsSubmitting(true);
    setError(null);
    try {
      const updated = await updateMemoryApi(createdReviewMemory.id, {
        title: createdReviewMemory.title,
        content: createdReviewMemory.content,
        category: reviewCategory.trim() || undefined,
        tags: reviewTags.trim() || undefined,
      });
      setMemories((prev) =>
        prev.map((m) => (m.id === updated.id ? updated : m))
      );
      setCreatedReviewMemory(null);
      setAiReviewResult(null);
      setSuccessMsg('Memory saved with your reviewed category and tags!');
      setTimeout(() => setSuccessMsg(null), 3500);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to save category and tags.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleApplyExistingAiSuggestions = async () => {
    if (!aiModalResult) return;
    setIsSubmitting(true);
    setError(null);
    try {
      const memId = aiModalResult.memoryId;
      const mem = memories.find((m) => m.id === memId);
      if (!mem) return;

      const updated = await updateMemoryApi(memId, {
        title: mem.title,
        content: mem.content,
        category: aiModalResult.result.suggestedCategory,
        tags: aiModalResult.result.suggestedTags.join(', '),
      });

      setMemories((prev) =>
        prev.map((m) => (m.id === updated.id ? updated : m))
      );
      setAiModalResult(null);
      setSuccessMsg('AI suggested category and tags applied successfully!');
      setTimeout(() => setSuccessMsg(null), 3500);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to apply AI suggestions.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const formatDate = (dateStr: string) => {
    try {
      return new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <ProtectedRoute>
      <div style={{
        minHeight: 'calc(100vh - 70px)',
        backgroundColor: '#f8fafc',
        padding: '2rem 1.5rem',
      }}>
        <div style={{
          maxWidth: '1000px',
          margin: '0 auto',
          display: 'flex',
          flexDirection: 'column',
          gap: '1.5rem',
        }}>
          {/* Header Bar */}
          <div style={{
            backgroundColor: '#ffffff',
            padding: '1.5rem 2rem',
            borderRadius: '16px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 2px 4px rgba(0,0,0,0.03)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '1rem',
          }}>
            <div>
              <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: '#0f172a', margin: '0 0 0.25rem 0' }}>
                Memory Vault
              </h1>
              <p style={{ fontSize: '0.9rem', color: '#64748b', margin: 0 }}>
                Store, reflect, and organize your personal life memories
              </p>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
              <button
                onClick={() => setIsVoiceModalOpen(true)}
                style={{
                  backgroundColor: '#ffffff',
                  color: '#0284c7',
                  border: '1px solid #0284c7',
                  padding: '0.65rem 1.15rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.45rem',
                  boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
                }}
              >
                <span>🎙️</span>
                <span>Record Voice</span>
              </button>
              <button
                onClick={openCreateModal}
                style={{
                  backgroundColor: '#0284c7',
                  color: '#ffffff',
                  border: 'none',
                  padding: '0.65rem 1.25rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  cursor: 'pointer',
                  boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                }}
              >
                + Create Memory
              </button>
            </div>
          </div>

          {/* Phase 6 Semantic Search Bar */}
          <div style={{
            backgroundColor: '#ffffff',
            padding: '1.25rem 1.5rem',
            borderRadius: '14px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 2px 4px rgba(0,0,0,0.02)',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.75rem',
          }}>
            <form onSubmit={handleSemanticSearch} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
              <div style={{ position: 'relative', flex: 1, minWidth: '280px' }}>
                <input
                  type="text"
                  placeholder="Search your memories by meaning... (e.g. 'trips I took with my friends')"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '0.65rem 1rem',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.9rem',
                    outline: 'none',
                    boxSizing: 'border-box',
                  }}
                />
              </div>
              <button
                type="submit"
                disabled={isSearching}
                style={{
                  backgroundColor: '#4f46e5',
                  color: '#ffffff',
                  border: 'none',
                  padding: '0.65rem 1.25rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: isSearching ? 'not-allowed' : 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.4rem',
                }}
              >
                {isSearching ? 'Searching...' : '🔍 Semantic Search'}
              </button>
              {isSemanticSearchActive && (
                <button
                  type="button"
                  onClick={handleClearSearch}
                  style={{
                    backgroundColor: '#f1f5f9',
                    color: '#475569',
                    border: '1px solid #cbd5e1',
                    padding: '0.65rem 1rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.875rem',
                    cursor: 'pointer',
                  }}
                >
                  ✕ Clear
                </button>
              )}
              <button
                type="button"
                onClick={handleReindex}
                disabled={isReindexing}
                title="Index all existing memories into Qdrant vector store"
                style={{
                  backgroundColor: '#f8fafc',
                  color: '#6366f1',
                  border: '1px solid #e0e7ff',
                  padding: '0.65rem 1rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: isReindexing ? 'not-allowed' : 'pointer',
                }}
              >
                {isReindexing ? 'Indexing...' : '⚡ Re-index Vault'}
              </button>
            </form>
            {isSemanticSearchActive && searchResults.length > 0 && (
              <div style={{
                fontSize: '0.85rem',
                color: '#4338ca',
                backgroundColor: '#eef2ff',
                padding: '0.4rem 0.75rem',
                borderRadius: '6px',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
              }}>
                <span>✨ <strong>Semantic Search Active:</strong> Showing {searchResults.length} relevant {searchResults.length === 1 ? 'memory' : 'memories'} for &quot;{searchQuery}&quot;</span>
              </div>
            )}
          </div>

          {/* Feedback Messages */}
          {successMsg && (
            <div style={{
              backgroundColor: '#f0fdf4',
              color: '#166534',
              border: '1px solid #bbf7d0',
              padding: '0.75rem 1rem',
              borderRadius: '8px',
              fontSize: '0.875rem',
              fontWeight: 600,
            }}>
              ✓ {successMsg}
            </div>
          )}

          {error && (
            <div style={{
              backgroundColor: '#fef2f2',
              color: '#b91c1c',
              border: '1px solid #fecaca',
              padding: '0.75rem 1rem',
              borderRadius: '8px',
              fontSize: '0.875rem',
            }}>
              {error}
            </div>
          )}

          {/* Memory List Grid */}
          {isLoading ? (
            <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b', fontWeight: 500 }}>
              Loading memories from your vault...
            </div>
          ) : isSemanticSearchActive && searchResults.length === 0 ? (
            <div style={{
              backgroundColor: '#ffffff',
              padding: '3rem 2rem',
              borderRadius: '16px',
              border: '1px dashed #cbd5e1',
              textAlign: 'center',
            }}>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#1e293b', margin: '0 0 0.5rem 0' }}>
                No relevant memories found for &quot;{searchQuery}&quot;.
              </h3>
              <p style={{ fontSize: '0.9rem', color: '#64748b', maxWidth: '450px', margin: '0 auto 1.5rem auto' }}>
                No memories matched the semantic meaning of &quot;{searchQuery}&quot;. Try clicking &quot;⚡ Re-index Vault&quot; or searching with different descriptive terms.
              </p>
              <button
                onClick={handleClearSearch}
                style={{
                  backgroundColor: '#0284c7',
                  color: '#ffffff',
                  border: 'none',
                  padding: '0.65rem 1.25rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  cursor: 'pointer',
                }}
              >
                Clear Search & Show All Memories
              </button>
            </div>
          ) : memories.length === 0 ? (
            <div style={{
              backgroundColor: '#ffffff',
              padding: '3rem 2rem',
              borderRadius: '16px',
              border: '1px dashed #cbd5e1',
              textAlign: 'center',
            }}>
              <div style={{
                width: '48px',
                height: '48px',
                borderRadius: '12px',
                backgroundColor: '#e0f2fe',
                color: '#0284c7',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.5rem',
                fontWeight: 700,
                marginBottom: '1rem',
              }}>
                M
              </div>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#1e293b', margin: '0 0 0.5rem 0' }}>
                Your Memory Vault is empty
              </h3>
              <p style={{ fontSize: '0.9rem', color: '#64748b', maxWidth: '420px', margin: '0 auto 1.5rem auto' }}>
                Start preserving your life reflections, stories, and family memories by creating your first entry.
              </p>
              <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'center', flexWrap: 'wrap' }}>
                <button
                  onClick={() => setIsVoiceModalOpen(true)}
                  style={{
                    backgroundColor: '#ffffff',
                    color: '#0284c7',
                    border: '1px solid #0284c7',
                    padding: '0.65rem 1.25rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.9rem',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.45rem',
                  }}
                >
                  <span>🎙️</span>
                  <span>Record Voice Memory</span>
                </button>
                <button
                  onClick={openCreateModal}
                  style={{
                    backgroundColor: '#0284c7',
                    color: '#ffffff',
                    border: 'none',
                    padding: '0.65rem 1.25rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.9rem',
                    cursor: 'pointer',
                  }}
                >
                  + Create Your First Memory
                </button>
              </div>
            </div>
          ) : (
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
              gap: '1.25rem',
            }}>
              {(isSemanticSearchActive ? searchResults : memories).map((mem) => (
                <div key={mem.id} style={{
                  backgroundColor: '#ffffff',
                  borderRadius: '14px',
                  border: '1px solid #e2e8f0',
                  padding: '1.5rem',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  boxShadow: '0 2px 4px rgba(0,0,0,0.02)',
                  transition: 'transform 0.15s ease, box-shadow 0.15s ease',
                }}>
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.5rem', marginBottom: '0.5rem' }}>
                      <h3 style={{
                        fontSize: '1.1rem',
                        fontWeight: 700,
                        color: '#0f172a',
                        margin: 0,
                        lineHeight: 1.3,
                        cursor: 'pointer',
                      }}
                      onClick={() => setViewingMemory(mem)}
                      >
                        {mem.title}
                      </h3>
                      {mem.category && (
                        <span style={{
                          fontSize: '0.725rem',
                          fontWeight: 700,
                          backgroundColor: '#e0f2fe',
                          color: '#0369a1',
                          padding: '0.15rem 0.55rem',
                          borderRadius: '9999px',
                          whiteSpace: 'nowrap',
                        }}>
                          {mem.category}
                        </span>
                      )}
                    </div>

                    <p style={{
                      fontSize: '0.875rem',
                      color: '#475569',
                      lineHeight: 1.5,
                      margin: '0 0 1rem 0',
                      display: '-webkit-box',
                      WebkitLineClamp: 3,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                    }}>
                      {mem.content}
                    </p>
                  </div>

                  <div>
                    {mem.tags && (
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginBottom: '1rem' }}>
                        {mem.tags.split(',').map((t, idx) => (
                          <span key={idx} style={{
                            fontSize: '0.7rem',
                            backgroundColor: '#f1f5f9',
                            color: '#475569',
                            padding: '0.1rem 0.45rem',
                            borderRadius: '4px',
                          }}>
                            #{t.trim()}
                          </span>
                        ))}
                      </div>
                    )}

                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      paddingTop: '0.75rem',
                      borderTop: '1px solid #f1f5f9',
                      fontSize: '0.8rem',
                      color: '#94a3b8',
                    }}>
                      <span>{formatDate(mem.createdAt)}</span>
                      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                        <button
                          onClick={() => handleAiProcess(mem)}
                          disabled={aiProcessingId === mem.id}
                          style={{
                            backgroundColor: '#f0f9ff',
                            border: '1px solid #bae6fd',
                            color: '#0284c7',
                            fontWeight: 600,
                            cursor: aiProcessingId === mem.id ? 'not-allowed' : 'pointer',
                            fontSize: '0.75rem',
                            padding: '0.15rem 0.45rem',
                            borderRadius: '4px',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '0.2rem',
                          }}
                        >
                          {aiProcessingId === mem.id ? 'Processing...' : '✨ AI Process'}
                        </button>
                        <button
                          onClick={() => setViewingMemory(mem)}
                          style={{
                            backgroundColor: 'transparent',
                            border: 'none',
                            color: '#0284c7',
                            fontWeight: 600,
                            cursor: 'pointer',
                            fontSize: '0.8rem',
                            padding: 0,
                          }}
                        >
                          View
                        </button>
                        <button
                          onClick={() => openEditModal(mem)}
                          style={{
                            backgroundColor: 'transparent',
                            border: 'none',
                            color: '#475569',
                            fontWeight: 600,
                            cursor: 'pointer',
                            fontSize: '0.8rem',
                            padding: 0,
                          }}
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => setDeletingMemoryId(mem.id)}
                          style={{
                            backgroundColor: 'transparent',
                            border: 'none',
                            color: '#ef4444',
                            fontWeight: 600,
                            cursor: 'pointer',
                            fontSize: '0.8rem',
                            padding: 0,
                          }}
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* CREATE MEMORY MODAL */}
      {isCreateOpen && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '1rem',
          zIndex: 100,
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            width: '100%',
            maxWidth: '580px',
            maxHeight: '90vh',
            overflowY: 'auto',
            padding: '2rem',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          }}>
            {pendingCreatedMemory ? (
              <div style={{
                backgroundColor: '#fffbeb',
                border: '1px solid #fde68a',
                borderRadius: '10px',
                padding: '0.75rem 1rem',
                marginBottom: '1.25rem',
                display: 'flex',
                alignItems: 'center',
                gap: '0.6rem',
                fontSize: '0.85rem',
                color: '#92400e',
              }}>
                <span style={{ fontSize: '1.1rem' }}>ℹ️</span>
                <span>
                  Memory <strong>"{pendingCreatedMemory.title}"</strong> is created. You can retry uploading your attached images below, or click <em>Done</em> to finish without attachments.
                </span>
              </div>
            ) : (
              <div style={{
                backgroundColor: '#f0f9ff',
                border: '1px solid #bae6fd',
                borderRadius: '10px',
                padding: '0.75rem 1rem',
                marginBottom: '1.25rem',
                display: 'flex',
                alignItems: 'center',
                gap: '0.6rem',
                fontSize: '0.85rem',
                color: '#0369a1',
              }}>
                <span style={{ fontSize: '1.1rem' }}>✨</span>
                <span><strong>MemoraAI Auto-Organize:</strong> Type your title & content, then click <em>"✨ Auto-Organize & Save"</em> to automatically receive AI category & tag suggestions.</span>
              </div>
            )}

            <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', marginTop: 0, marginBottom: '1.25rem' }}>
              {pendingCreatedMemory ? 'Upload Image Attachments' : 'Create a New Memory'}
            </h2>

            <form onSubmit={handleCreateSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Title *
                </label>
                <input
                  type="text"
                  required
                  disabled={isSubmitting || !!pendingCreatedMemory}
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g., Grandfather's 80th Birthday Celebration"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.6rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                    outline: 'none',
                    backgroundColor: pendingCreatedMemory ? '#f8fafc' : '#ffffff',
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Category (optional)
                </label>
                <input
                  type="text"
                  disabled={isSubmitting || !!pendingCreatedMemory}
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  placeholder="e.g., Family, Travel, Milestone"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.6rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                    outline: 'none',
                    backgroundColor: pendingCreatedMemory ? '#f8fafc' : '#ffffff',
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Tags (optional, comma-separated)
                </label>
                <input
                  type="text"
                  disabled={isSubmitting || !!pendingCreatedMemory}
                  value={tags}
                  onChange={(e) => setTags(e.target.value)}
                  placeholder="e.g., birthday, family, 2026"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.6rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                    outline: 'none',
                    backgroundColor: pendingCreatedMemory ? '#f8fafc' : '#ffffff',
                  }}
                />
              </div>

              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                  <label style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155' }}>
                    Content *
                  </label>
                  <button
                    type="button"
                    disabled={isSubmitting || !!pendingCreatedMemory}
                    onClick={() => setIsVoiceDictateOpen(true)}
                    style={{
                      backgroundColor: '#f0f9ff',
                      color: '#0284c7',
                      border: '1px solid #bae6fd',
                      borderRadius: '6px',
                      padding: '0.2rem 0.6rem',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      cursor: isSubmitting || !!pendingCreatedMemory ? 'not-allowed' : 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '0.3rem',
                    }}
                  >
                    <span>🎙️</span>
                    <span>Record Voice Note</span>
                  </button>
                </div>
                <textarea
                  required
                  rows={4}
                  disabled={isSubmitting || !!pendingCreatedMemory}
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  placeholder="Write your story, memory details, or thoughts here..."
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.65rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                    outline: 'none',
                    resize: 'vertical',
                    fontFamily: 'inherit',
                    backgroundColor: pendingCreatedMemory ? '#f8fafc' : '#ffffff',
                  }}
                />
              </div>

              {/* IMAGE ATTACHMENT SECTION */}
              <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                  <label style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155' }}>
                    Attach Images (Optional)
                  </label>
                  <span style={{
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    backgroundColor: createFiles.length >= MAX_MEDIA_PER_MEMORY ? '#fee2e2' : '#e0f2fe',
                    color: createFiles.length >= MAX_MEDIA_PER_MEMORY ? '#b91c1c' : '#0369a1',
                    padding: '0.15rem 0.55rem',
                    borderRadius: '9999px',
                  }}>
                    {createFiles.length} / {MAX_MEDIA_PER_MEMORY}
                  </span>
                </div>

                {/* Drop / Select zone (when < 5) */}
                {createFiles.length < MAX_MEDIA_PER_MEMORY && (
                  <div
                    onDragOver={(e) => {
                      e.preventDefault();
                      if (!isSubmitting) setIsCreateDragging(true);
                    }}
                    onDragLeave={(e) => {
                      e.preventDefault();
                      setIsCreateDragging(false);
                    }}
                    onDrop={(e) => {
                      e.preventDefault();
                      setIsCreateDragging(false);
                      if (!isSubmitting) handleCreateFilesSelected(e.dataTransfer.files);
                    }}
                    onClick={() => !isSubmitting && createFileInputRef.current?.click()}
                    style={{
                      border: isCreateDragging ? '2px dashed #0284c7' : '2px dashed #cbd5e1',
                      backgroundColor: isCreateDragging ? '#f0f9ff' : '#f8fafc',
                      borderRadius: '8px',
                      padding: '0.85rem',
                      textAlign: 'center',
                      cursor: isSubmitting ? 'not-allowed' : 'pointer',
                      transition: 'all 0.15s ease',
                      marginBottom: createPreviews.length > 0 ? '0.75rem' : '0',
                    }}
                  >
                    <input
                      ref={createFileInputRef}
                      type="file"
                      multiple
                      accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
                      disabled={isSubmitting}
                      onChange={(e) => handleCreateFilesSelected(e.target.files)}
                      style={{ display: 'none' }}
                    />
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.2rem' }}>
                      <span style={{ fontSize: '1.25rem' }}>📷</span>
                      <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#0f172a' }}>
                        Click or drag images to attach
                      </span>
                      <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        JPEG, PNG, or WEBP up to 10 MB each ({MAX_MEDIA_PER_MEMORY - createFiles.length} slot{MAX_MEDIA_PER_MEMORY - createFiles.length > 1 ? 's' : ''} remaining)
                      </span>
                    </div>
                  </div>
                )}

                {/* Previews Grid */}
                {createPreviews.length > 0 && (
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(110px, 1fr))',
                    gap: '0.5rem',
                    marginTop: '0.5rem',
                  }}>
                    {createPreviews.map((preview, idx) => (
                      <div
                        key={preview.id}
                        style={{
                          position: 'relative',
                          backgroundColor: '#f1f5f9',
                          borderRadius: '8px',
                          overflow: 'hidden',
                          border: '1px solid #e2e8f0',
                          display: 'flex',
                          flexDirection: 'column',
                        }}
                      >
                        <div style={{ height: '75px', width: '100%', position: 'relative' }}>
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img
                            src={preview.url}
                            alt={preview.file.name}
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                          />
                          <button
                            type="button"
                            title="Remove image"
                            disabled={isSubmitting}
                            onClick={(e) => {
                              e.stopPropagation();
                              removeCreatePreview(idx);
                            }}
                            style={{
                              position: 'absolute',
                              top: '3px',
                              right: '3px',
                              backgroundColor: 'rgba(15, 23, 42, 0.75)',
                              color: '#ffffff',
                              border: 'none',
                              borderRadius: '50%',
                              width: '20px',
                              height: '20px',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontSize: '0.7rem',
                              cursor: isSubmitting ? 'not-allowed' : 'pointer',
                              lineHeight: 1,
                            }}
                          >
                            ✕
                          </button>
                        </div>
                        <div style={{ padding: '0.3rem', fontSize: '0.7rem', color: '#334155' }}>
                          <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 600 }}>
                            {preview.file.name}
                          </div>
                          <div style={{ color: '#64748b' }}>
                            {formatFileSize(preview.file.size)}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* ACTION BUTTONS */}
              {pendingCreatedMemory ? (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    disabled={isSubmitting}
                    onClick={closeCreateModal}
                    style={{
                      backgroundColor: '#ffffff',
                      color: '#475569',
                      border: '1px solid #cbd5e1',
                      padding: '0.6rem 1.1rem',
                      borderRadius: '8px',
                      fontWeight: 600,
                      fontSize: '0.875rem',
                      cursor: isSubmitting ? 'not-allowed' : 'pointer',
                    }}
                  >
                    Done (Finish without images)
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting || createFiles.length === 0}
                    style={{
                      backgroundColor: isSubmitting || createFiles.length === 0 ? '#94a3b8' : '#0284c7',
                      color: '#ffffff',
                      border: 'none',
                      padding: '0.6rem 1.25rem',
                      borderRadius: '8px',
                      fontWeight: 600,
                      fontSize: '0.875rem',
                      cursor: isSubmitting || createFiles.length === 0 ? 'not-allowed' : 'pointer',
                    }}
                  >
                    {isSubmitting ? (uploadStatusText || 'Uploading...') : 'Retry Uploading Images'}
                  </button>
                </div>
              ) : (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    disabled={isSubmitting}
                    onClick={closeCreateModal}
                    style={{
                      backgroundColor: '#ffffff',
                      color: '#475569',
                      border: '1px solid #cbd5e1',
                      padding: '0.6rem 1.1rem',
                      borderRadius: '8px',
                      fontWeight: 600,
                      fontSize: '0.875rem',
                      cursor: isSubmitting ? 'not-allowed' : 'pointer',
                    }}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    style={{
                      backgroundColor: '#ffffff',
                      color: '#0284c7',
                      border: '1px solid #0284c7',
                      padding: '0.6rem 1.1rem',
                      borderRadius: '8px',
                      fontWeight: 600,
                      fontSize: '0.875rem',
                      cursor: isSubmitting ? 'not-allowed' : 'pointer',
                    }}
                  >
                    {isSubmitting && uploadStatusText ? uploadStatusText : 'Save Manually'}
                  </button>
                  <button
                    type="button"
                    onClick={handleCreateAndAutoOrganize}
                    disabled={isSubmitting}
                    style={{
                      backgroundColor: isSubmitting ? '#94a3b8' : '#0284c7',
                      color: '#ffffff',
                      border: 'none',
                      padding: '0.6rem 1.25rem',
                      borderRadius: '8px',
                      fontWeight: 600,
                      fontSize: '0.875rem',
                      cursor: isSubmitting ? 'not-allowed' : 'pointer',
                      boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.35rem',
                    }}
                  >
                    {isSubmitting && uploadStatusText ? uploadStatusText : (
                      <>
                        <span>✨</span>
                        <span>Auto-Organize & Save</span>
                      </>
                    )}
                  </button>
                </div>
              )}
            </form>
          </div>
        </div>
      )}

      {/* REVIEW AI SUGGESTIONS MODAL AFTER CREATION */}
      {createdReviewMemory && aiReviewResult && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '1rem',
          zIndex: 1000,
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            maxWidth: '560px',
            width: '100%',
            padding: '2rem',
            boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)',
            border: '1px solid #e2e8f0',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem' }}>
              <span style={{ fontSize: '1.25rem' }}>✨</span>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
                Review AI Suggestions
              </h3>
            </div>
            <p style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '1.25rem', lineHeight: 1.45 }}>
              MemoraAI analyzed <strong>"{createdReviewMemory.title}"</strong> and suggested the following organization. You can accept them or customize them before finalizing.
            </p>

            <form onSubmit={handleSaveReviewSuggestions} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div style={{ backgroundColor: '#f8fafc', padding: '0.85rem 1rem', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
                  AI Generated Summary
                </span>
                <p style={{ fontSize: '0.875rem', color: '#1e293b', margin: '0.25rem 0 0 0' }}>
                  {aiReviewResult.summary}
                </p>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Category
                </label>
                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.35rem' }}>
                  <span style={{ fontSize: '0.8rem', color: '#64748b' }}>AI Suggestion:</span>
                  <span
                    onClick={() => setReviewCategory(aiReviewResult.suggestedCategory)}
                    title="Click to apply AI category"
                    style={{
                      fontSize: '0.8rem',
                      fontWeight: 700,
                      backgroundColor: '#e0f2fe',
                      color: '#0369a1',
                      padding: '0.2rem 0.65rem',
                      borderRadius: '9999px',
                      cursor: 'pointer',
                    }}
                  >
                    [ {aiReviewResult.suggestedCategory} ]
                  </span>
                </div>
                <input
                  type="text"
                  value={reviewCategory}
                  onChange={(e) => setReviewCategory(e.target.value)}
                  placeholder="Review or edit category"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.6rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Tags (comma-separated)
                </label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginBottom: '0.35rem' }}>
                  <span style={{ fontSize: '0.8rem', color: '#64748b', width: '100%' }}>AI Suggested Tags:</span>
                  {aiReviewResult.suggestedTags.map((t, idx) => (
                    <span key={idx} style={{
                      fontSize: '0.75rem',
                      backgroundColor: '#f1f5f9',
                      color: '#0284c7',
                      padding: '0.15rem 0.5rem',
                      borderRadius: '4px',
                      fontWeight: 600,
                    }}>
                      #{t}
                    </span>
                  ))}
                </div>
                <input
                  type="text"
                  value={reviewTags}
                  onChange={(e) => setReviewTags(e.target.value)}
                  placeholder="Review or edit tags"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.6rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.75rem' }}>
                <button
                  type="button"
                  onClick={() => {
                    setCreatedReviewMemory(null);
                    setAiReviewResult(null);
                    setSuccessMsg('Memory saved successfully.');
                    setTimeout(() => setSuccessMsg(null), 3000);
                  }}
                  style={{
                    backgroundColor: '#ffffff',
                    color: '#475569',
                    border: '1px solid #cbd5e1',
                    padding: '0.6rem 1.1rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.875rem',
                    cursor: 'pointer',
                  }}
                >
                  Keep Unchanged
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  style={{
                    backgroundColor: '#0284c7',
                    color: '#ffffff',
                    border: 'none',
                    padding: '0.6rem 1.3rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.875rem',
                    cursor: isSubmitting ? 'not-allowed' : 'pointer',
                    boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                  }}
                >
                  {isSubmitting ? 'Saving...' : '✓ Confirm & Finish'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* EDIT MEMORY MODAL */}
      {editingMemory && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '1rem',
          zIndex: 100,
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            width: '100%',
            maxWidth: '560px',
            padding: '2rem',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', marginTop: 0, marginBottom: '1.25rem' }}>
              Edit Memory
            </h2>

            <form onSubmit={handleEditSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Title *
                </label>
                <input
                  type="text"
                  required
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.6rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                    outline: 'none',
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Category
                </label>
                <input
                  type="text"
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.6rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                    outline: 'none',
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Tags
                </label>
                <input
                  type="text"
                  value={tags}
                  onChange={(e) => setTags(e.target.value)}
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.6rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                    outline: 'none',
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Content *
                </label>
                <textarea
                  required
                  rows={5}
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.65rem 0.85rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.9rem',
                    outline: 'none',
                    resize: 'vertical',
                    fontFamily: 'inherit',
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  onClick={() => setEditingMemory(null)}
                  style={{
                    backgroundColor: '#ffffff',
                    color: '#475569',
                    border: '1px solid #cbd5e1',
                    padding: '0.6rem 1.1rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.875rem',
                    cursor: 'pointer',
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  style={{
                    backgroundColor: isSubmitting ? '#94a3b8' : '#0284c7',
                    color: '#ffffff',
                    border: 'none',
                    padding: '0.6rem 1.25rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.875rem',
                    cursor: isSubmitting ? 'not-allowed' : 'pointer',
                  }}
                >
                  {isSubmitting ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* VIEW MEMORY MODAL */}
      {viewingMemory && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '1rem',
          zIndex: 100,
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            width: '100%',
            maxWidth: '680px',
            maxHeight: '90vh',
            overflowY: 'auto',
            padding: '2rem',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>
                  {viewingMemory.category || 'Uncategorized'}
                </div>
                <h2 style={{ fontSize: '1.35rem', fontWeight: 700, color: '#0f172a', margin: '0.25rem 0 0 0' }}>
                  {viewingMemory.title}
                </h2>
              </div>
              <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                {formatDate(viewingMemory.createdAt)}
              </span>
            </div>

            <div style={{
              backgroundColor: '#f8fafc',
              padding: '1.25rem',
              borderRadius: '12px',
              border: '1px solid #e2e8f0',
              fontSize: '0.95rem',
              color: '#334155',
              lineHeight: 1.6,
              whiteSpace: 'pre-wrap',
              marginBottom: '1.25rem',
              maxHeight: '320px',
              overflowY: 'auto',
            }}>
              {viewingMemory.content}
            </div>

            {viewingMemory.tags && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', marginBottom: '1.5rem' }}>
                {viewingMemory.tags.split(',').map((t, idx) => (
                  <span key={idx} style={{
                    fontSize: '0.75rem',
                    backgroundColor: '#e0f2fe',
                    color: '#0369a1',
                    padding: '0.15rem 0.55rem',
                    borderRadius: '6px',
                    fontWeight: 600,
                  }}>
                    #{t.trim()}
                  </span>
                ))}
              </div>
            )}

            {/* MULTIMEDIA VAULT GALLERY */}
            <MediaGallery memoryId={viewingMemory.id} />

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '1rem', borderTop: '1px solid #f1f5f9' }}>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button
                  onClick={() => {
                    const m = viewingMemory;
                    setViewingMemory(null);
                    openEditModal(m);
                  }}
                  style={{
                    backgroundColor: '#f1f5f9',
                    color: '#334155',
                    border: '1px solid #cbd5e1',
                    padding: '0.5rem 1rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                  }}
                >
                  Edit
                </button>
                <button
                  onClick={() => {
                    setDeletingMemoryId(viewingMemory.id);
                  }}
                  style={{
                    backgroundColor: '#ffffff',
                    color: '#ef4444',
                    border: '1px solid #fca5a5',
                    padding: '0.5rem 1rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                  }}
                >
                  Delete
                </button>
              </div>

              <button
                onClick={() => setViewingMemory(null)}
                style={{
                  backgroundColor: '#0284c7',
                  color: '#ffffff',
                  border: 'none',
                  padding: '0.5rem 1.25rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                }}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DELETE CONFIRMATION MODAL */}
      {deletingMemoryId && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '1rem',
          zIndex: 110,
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            width: '100%',
            maxWidth: '420px',
            padding: '2rem',
            textAlign: 'center',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          }}>
            <div style={{
              width: '48px',
              height: '48px',
              borderRadius: '50%',
              backgroundColor: '#fef2f2',
              color: '#dc2626',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '1.25rem',
              fontWeight: 700,
              marginBottom: '1rem',
            }}>
              !
            </div>
            <h3 style={{ fontSize: '1.15rem', fontWeight: 700, color: '#0f172a', margin: '0 0 0.5rem 0' }}>
              Delete Memory?
            </h3>
            <p style={{ fontSize: '0.875rem', color: '#64748b', margin: '0 0 1.5rem 0' }}>
              Are you sure you want to delete this memory? This action cannot be undone.
            </p>

            <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem' }}>
              <button
                onClick={() => setDeletingMemoryId(null)}
                disabled={isSubmitting}
                style={{
                  backgroundColor: '#ffffff',
                  color: '#475569',
                  border: '1px solid #cbd5e1',
                  padding: '0.6rem 1.25rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: 'pointer',
                }}
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteConfirm}
                disabled={isSubmitting}
                style={{
                  backgroundColor: isSubmitting ? '#fca5a5' : '#dc2626',
                  color: '#ffffff',
                  border: 'none',
                  padding: '0.6rem 1.25rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: isSubmitting ? 'not-allowed' : 'pointer',
                }}
              >
                {isSubmitting ? 'Deleting...' : 'Confirm Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* AI RESULT MODAL */}
      {aiModalResult && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '1rem',
          zIndex: 1000,
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            maxWidth: '520px',
            width: '100%',
            padding: '1.75rem',
            boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)',
            border: '1px solid #e2e8f0',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
              <span style={{ fontSize: '1.25rem' }}>✨</span>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
                AI Memory Analysis
              </h3>
            </div>
            <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '1.25rem' }}>
              Insights generated for: <strong>{aiModalResult.memoryTitle}</strong>
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.5rem' }}>
              <div style={{ backgroundColor: '#f8fafc', padding: '1rem', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
                  Deterministic Summary
                </span>
                <p style={{ fontSize: '0.9rem', color: '#1e293b', margin: '0.35rem 0 0 0', lineHeight: 1.45 }}>
                  {aiModalResult.result.summary}
                </p>
              </div>

              <div style={{ backgroundColor: '#f8fafc', padding: '1rem', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
                  Suggested Category
                </span>
                <div style={{ marginTop: '0.35rem' }}>
                  <span style={{
                    fontSize: '0.8rem',
                    fontWeight: 700,
                    backgroundColor: '#e0f2fe',
                    color: '#0369a1',
                    padding: '0.2rem 0.6rem',
                    borderRadius: '9999px',
                  }}>
                    {aiModalResult.result.suggestedCategory}
                  </span>
                </div>
              </div>

              <div style={{ backgroundColor: '#f8fafc', padding: '1rem', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
                  Suggested Tags
                </span>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginTop: '0.35rem' }}>
                  {aiModalResult.result.suggestedTags.map((tag, idx) => (
                    <span key={idx} style={{
                      fontSize: '0.75rem',
                      backgroundColor: '#f1f5f9',
                      color: '#334155',
                      padding: '0.15rem 0.5rem',
                      borderRadius: '4px',
                      fontWeight: 500,
                    }}>
                      #{tag}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button
                onClick={() => setAiModalResult(null)}
                style={{
                  backgroundColor: '#0f172a',
                  color: '#ffffff',
                  border: 'none',
                  padding: '0.5rem 1.25rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: 'pointer',
                }}
              >
                Close Insights
              </button>
            </div>
          </div>
        </div>
      )}

      {/* STANDALONE VOICE RECORDER MODAL */}
      <VoiceRecorderModal
        isOpen={isVoiceModalOpen}
        onClose={() => setIsVoiceModalOpen(false)}
        onMemoryCreated={(newMemory) => {
          setMemories((prev) => [newMemory, ...prev]);
          setSuccessMsg('Voice memory created successfully!');
          setTimeout(() => setSuccessMsg(null), 3000);
        }}
      />

      {/* VOICE DICTATE HELPER FOR CREATE MEMORY MODAL */}
      <VoiceRecorderModal
        isOpen={isVoiceDictateOpen}
        onClose={() => setIsVoiceDictateOpen(false)}
        initialCategory={category || 'Reflections'}
        initialTags={tags || 'voice'}
        onTranscriptReady={(transcript) => {
          setContent((prev) => (prev ? `${prev}\n\n${transcript}` : transcript));
          if (!title.trim()) {
            const now = new Date();
            setTitle(
              `Voice Reflection - ${now.toLocaleDateString(undefined, {
                month: 'short',
                day: 'numeric',
                year: 'numeric',
              })}`
            );
          }
        }}
      />
    </ProtectedRoute>
  );
}
