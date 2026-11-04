'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  deleteMemoryMediaApi,
  formatFileSize,
  getMemoryMediaApi,
  isAudioMedia,
  MAX_MEDIA_PER_MEMORY,
  uploadMemoryMediaApi,
  validateMediaFiles,
} from '../../lib/media';
import { MemoryMedia } from '../../types/media';
import AuthenticatedImage from './AuthenticatedImage';
import AuthenticatedAudioPlayer from './AuthenticatedAudioPlayer';

interface MediaGalleryProps {
  memoryId: string;
  onMediaCountChange?: (count: number) => void;
}

export default function MediaGallery({ memoryId, onMediaCountChange }: MediaGalleryProps) {
  const [mediaList, setMediaList] = useState<MemoryMedia[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isUploading, setIsUploading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Lightbox Preview State
  const [activePreviewMedia, setActivePreviewMedia] = useState<MemoryMedia | null>(null);

  // Deletion Confirmation State
  const [deletingMedia, setDeletingMedia] = useState<MemoryMedia | null>(null);
  const [isDeleting, setIsDeleting] = useState<boolean>(false);

  // Drag and Drop State
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const fetchMedia = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getMemoryMediaApi(memoryId);
      setMediaList(data);
      if (onMediaCountChange) {
        onMediaCountChange(data.length);
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load attached media.');
      }
    } finally {
      setIsLoading(false);
    }
  }, [memoryId, onMediaCountChange]);

  useEffect(() => {
    let isCurrent = true;

    setIsLoading(true);
    setError(null);
    setSuccessMsg(null);
    setActivePreviewMedia(null);
    setDeletingMedia(null);

    getMemoryMediaApi(memoryId)
      .then((data) => {
        if (!isCurrent) return;
        setMediaList(data);
        setIsLoading(false);
        if (onMediaCountChange) {
          onMediaCountChange(data.length);
        }
      })
      .catch((err: unknown) => {
        if (!isCurrent) return;
        if (err instanceof Error) {
          setError(err.message);
        } else {
          setError('Failed to load attached media.');
        }
        setIsLoading(false);
      });

    return () => {
      isCurrent = false;
    };
  }, [memoryId, onMediaCountChange]);

  const handleFilesSelected = async (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;
    setError(null);
    setSuccessMsg(null);

    const filesArray = Array.from(fileList);
    const validation = validateMediaFiles(filesArray, mediaList.length);

    if (!validation.valid) {
      setError(validation.error || 'Selected files failed validation.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    setIsUploading(true);
    try {
      const uploaded = await uploadMemoryMediaApi(memoryId, filesArray);
      const updatedList = [...mediaList, ...uploaded];
      setMediaList(updatedList);
      setSuccessMsg(
        `Successfully uploaded ${uploaded.length} image${uploaded.length > 1 ? 's' : ''}.`
      );
      if (onMediaCountChange) {
        onMediaCountChange(updatedList.length);
      }
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Upload failed. Please check file format and network.');
      }
    } finally {
      setIsUploading(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deletingMedia) return;
    setIsDeleting(true);
    setError(null);
    try {
      await deleteMemoryMediaApi(memoryId, deletingMedia.id);
      const remaining = mediaList.filter((m) => m.id !== deletingMedia.id);
      setMediaList(remaining);
      if (onMediaCountChange) {
        onMediaCountChange(remaining.length);
      }
      if (activePreviewMedia?.id === deletingMedia.id) {
        setActivePreviewMedia(null);
      }
      setDeletingMedia(null);
      setSuccessMsg(`Image "${deletingMedia.fileName}" removed.`);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to delete image.');
      }
    } finally {
      setIsDeleting(false);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    if (mediaList.length < MAX_MEDIA_PER_MEMORY && !isUploading) {
      setIsDragging(true);
    }
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (mediaList.length >= MAX_MEDIA_PER_MEMORY || isUploading) return;
    handleFilesSelected(e.dataTransfer.files);
  };

  const isLimitReached = mediaList.length >= MAX_MEDIA_PER_MEMORY;

  return (
    <div style={{ marginTop: '1.5rem', borderTop: '1px solid var(--color-border)', paddingTop: '1.25rem' }}>
      {/* Header with Attachment Counter */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '0.75rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--color-text)' }}>
            Attached Media
          </span>
          <span
            style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              backgroundColor: isLimitReached ? 'var(--color-error-background)' : 'var(--color-accent-soft)',
              color: isLimitReached ? 'var(--color-error)' : 'var(--primary-hover)',
              padding: '0.15rem 0.55rem',
              borderRadius: '9999px',
            }}
          >
            {mediaList.length} / {MAX_MEDIA_PER_MEMORY}
          </span>
        </div>

        {isLimitReached && (
          <span style={{ fontSize: '0.75rem', color: 'var(--color-secondary-text)', fontWeight: 500 }}>
            Maximum attachment limit reached
          </span>
        )}
      </div>

      {/* Messages */}
      {error && (
        <div
          style={{
            backgroundColor: 'var(--color-error-background)',
            border: '1px solid var(--color-error-border)',
            color: 'var(--color-error)',
            padding: '0.65rem 0.9rem',
            borderRadius: '8px',
            fontSize: '0.85rem',
            marginBottom: '1rem',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span>{error}</span>
          <button
            onClick={() => setError(null)}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--color-error)',
              cursor: 'pointer',
              fontWeight: 'bold',
            }}
          >
            ✕
          </button>
        </div>
      )}

      {successMsg && (
        <div
          style={{
            backgroundColor: 'var(--color-success-background)',
            border: '1px solid var(--color-success-border)',
            color: 'var(--color-success)',
            padding: '0.65rem 0.9rem',
            borderRadius: '8px',
            fontSize: '0.85rem',
            marginBottom: '1rem',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span>{successMsg}</span>
          <button
            onClick={() => setSuccessMsg(null)}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--color-success)',
              cursor: 'pointer',
              fontWeight: 'bold',
            }}
          >
            ✕
          </button>
        </div>
      )}

      {/* Loading Gallery State */}
      {isLoading ? (
        <div
          style={{
            padding: '2rem',
            textAlign: 'center',
            color: 'var(--color-secondary-text)',
            fontSize: '0.9rem',
            backgroundColor: 'var(--color-background)',
            borderRadius: '10px',
            border: '1px solid var(--color-border)',
          }}
        >
          Loading attachments...
        </div>
      ) : (
        <>
          {/* Empty State */}
          {mediaList.length === 0 ? (
            <div
              style={{
                padding: '1.5rem',
                textAlign: 'center',
                color: 'var(--color-secondary-text)',
                backgroundColor: 'var(--color-background)',
                borderRadius: '10px',
                border: '1px dashed var(--color-border)',
                fontSize: '0.85rem',
                marginBottom: '1rem',
              }}
            >
              No media attached to this memory yet. Upload photos or voice recordings below.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem', marginBottom: '1.25rem' }}>
              {/* Audio Recordings Section */}
              {mediaList.some((m) => m.contentType?.toLowerCase().startsWith('audio/')) && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                  <div style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--color-text)', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                    <span>🎙️ Voice Recordings</span>
                    <span style={{ fontSize: '0.7rem', color: 'var(--color-secondary-text)', fontWeight: 500 }}>
                      ({mediaList.filter((m) => m.contentType?.toLowerCase().startsWith('audio/')).length})
                    </span>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    {mediaList
                      .filter((m) => m.contentType?.toLowerCase().startsWith('audio/'))
                      .map((audioMedia) => (
                        <div
                          key={audioMedia.id}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '0.75rem',
                            backgroundColor: 'var(--surface-muted)',
                            border: '1px solid var(--border)',
                            borderRadius: 'var(--radius-sm)',
                            padding: '0.6rem 0.85rem',
                            boxShadow: 'var(--shadow-card)',
                          }}
                        >
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <AuthenticatedAudioPlayer
                              memoryId={memoryId}
                              mediaId={audioMedia.id}
                              fileName={audioMedia.fileName}
                              fileSize={audioMedia.fileSize}
                              style={{ boxShadow: 'none', border: 'none', padding: 0, backgroundColor: 'transparent' }}
                            />
                          </div>
                          <button
                            type="button"
                            onClick={() => setDeletingMedia(audioMedia)}
                            title="Delete voice recording"
                            style={{
                              backgroundColor: 'var(--danger-background)',
                              color: 'var(--danger)',
                              border: '1px solid var(--danger-border)',
                              borderRadius: '999px',
                              padding: '0.35rem 0.75rem',
                              fontSize: '0.75rem',
                              fontWeight: 700,
                              cursor: 'pointer',
                              flexShrink: 0,
                            }}
                          >
                            Delete
                          </button>
                        </div>
                      ))}
                  </div>
                </div>
              )}

              {/* Photos & Images Section */}
              {mediaList.some((m) => !m.contentType?.toLowerCase().startsWith('audio/')) && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                  <div style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--color-text)', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                    <span>📷 Photos & Images</span>
                    <span style={{ fontSize: '0.7rem', color: 'var(--color-secondary-text)', fontWeight: 500 }}>
                      ({mediaList.filter((m) => !m.contentType?.toLowerCase().startsWith('audio/')).length})
                    </span>
                  </div>
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))',
                      gap: '0.75rem',
                    }}
                  >
                    {mediaList
                      .filter((m) => !m.contentType?.toLowerCase().startsWith('audio/'))
                      .map((media) => (
                        <div
                          key={media.id}
                          style={{
                            backgroundColor: 'var(--color-surface)',
                            border: '1px solid var(--border)',
                            borderRadius: 'var(--radius-sm)',
                            overflow: 'hidden',
                            display: 'flex',
                            flexDirection: 'column',
                            boxShadow: 'var(--shadow-card)',
                            position: 'relative',
                          }}
                        >
                          {/* Thumbnail */}
                          <div
                            style={{
                              height: '110px',
                              width: '100%',
                              backgroundColor: 'var(--color-background)',
                              position: 'relative',
                              overflow: 'hidden',
                            }}
                          >
                            <AuthenticatedImage
                              memoryId={memoryId}
                              mediaId={media.id}
                              alt={media.fileName}
                              onClick={() => setActivePreviewMedia(media)}
                            />
                          </div>

                          {/* Metadata & Actions */}
                          <div style={{ padding: '0.5rem', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                            <div
                              title={media.fileName}
                              style={{
                                fontSize: '0.75rem',
                                fontWeight: 600,
                                color: 'var(--color-text)',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                              }}
                            >
                              {media.fileName}
                            </div>

                            <div
                              style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                marginTop: '0.35rem',
                              }}
                            >
                              <span style={{ fontSize: '0.7rem', color: 'var(--color-secondary-text)' }}>
                                {formatFileSize(media.fileSize)}
                              </span>
                              <button
                                onClick={() => setDeletingMedia(media)}
                                title="Delete image"
                                style={{
                                  backgroundColor: 'var(--danger-background)',
                                  color: 'var(--danger)',
                                  border: '1px solid var(--danger-border)',
                                  borderRadius: '999px',
                                  padding: '0.2rem 0.55rem',
                                  fontSize: '0.7rem',
                                  fontWeight: 700,
                                  cursor: 'pointer',
                                }}
                              >
                                Delete
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Upload Drop Zone / Input */}
          {!isLimitReached && (
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => !isUploading && fileInputRef.current?.click()}
              style={{
                border: isDragging ? '2px dashed var(--primary)' : '2px dashed var(--border)',
                backgroundColor: isDragging ? 'rgba(var(--accent-rgb), 0.12)' : 'var(--surface-muted)',
                borderRadius: 'var(--radius-md)',
                padding: '1.25rem',
                textAlign: 'center',
                cursor: isUploading ? 'not-allowed' : 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept="image/jpeg,image/png,image/webp,audio/webm,audio/wav,audio/mp4,audio/mpeg,audio/ogg,.jpg,.jpeg,.png,.webp,.webm,.wav,.mp4,.m4a,.mp3,.ogg"
                disabled={isUploading}
                onChange={(e) => handleFilesSelected(e.target.files)}
                style={{ display: 'none' }}
              />

              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.35rem' }}>
                <span style={{ fontSize: '1.4rem' }}>📷 🎙️</span>
                <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--color-text)' }}>
                  {isUploading ? 'Uploading attachment(s)...' : 'Click or drag photos or voice recordings to upload'}
                </span>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-secondary-text)' }}>
                  Photos up to 10 MB, audio up to 25 MB ({MAX_MEDIA_PER_MEMORY - mediaList.length} slot
                  {MAX_MEDIA_PER_MEMORY - mediaList.length > 1 ? 's' : ''} remaining)
                </span>
              </div>
            </div>
          )}
        </>
      )}

      {/* LIGHTBOX FULLSCREEN MODAL */}
      {activePreviewMedia && (
        <div
          onClick={() => setActivePreviewMedia(null)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(62, 39, 35, 0.65)',
            backdropFilter: 'blur(8px)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1.5rem',
            zIndex: 1000,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              maxWidth: '90vw',
              maxHeight: '85vh',
              display: 'flex',
              flexDirection: 'column',
              backgroundColor: 'var(--surface)',
              borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--border)',
              overflow: 'hidden',
              boxShadow: 'var(--shadow-soft)',
            }}
          >
            {/* Lightbox Header */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '0.75rem 1.25rem',
                borderBottom: '1px solid var(--border)',
              }}
            >
              <div>
                <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--color-text)' }}>
                  {activePreviewMedia.fileName}
                </span>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-secondary-text)', marginLeft: '0.5rem' }}>
                  ({formatFileSize(activePreviewMedia.fileSize)})
                </span>
              </div>
              <button
                onClick={() => setActivePreviewMedia(null)}
                style={{
                  backgroundColor: 'transparent',
                  border: 'none',
                  fontSize: '1.2rem',
                  cursor: 'pointer',
                  color: 'var(--color-secondary-text)',
                  lineHeight: 1,
                }}
              >
                ✕
              </button>
            </div>

            {/* Lightbox Viewport */}
            <div
              style={{
                padding: activePreviewMedia.contentType?.toLowerCase().startsWith('audio/') ? '1.5rem' : '0.75rem',
                backgroundColor: activePreviewMedia.contentType?.toLowerCase().startsWith('audio/') ? 'var(--color-surface)' : 'var(--color-text)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                maxHeight: '75vh',
                minWidth: activePreviewMedia.contentType?.toLowerCase().startsWith('audio/') ? '340px' : 'auto',
                overflow: 'hidden',
              }}
            >
              {activePreviewMedia.contentType?.toLowerCase().startsWith('audio/') ? (
                <AuthenticatedAudioPlayer
                  memoryId={memoryId}
                  mediaId={activePreviewMedia.id}
                  fileName={activePreviewMedia.fileName}
                  fileSize={activePreviewMedia.fileSize}
                  autoPlay={true}
                />
              ) : (
                <AuthenticatedImage
                  memoryId={memoryId}
                  mediaId={activePreviewMedia.id}
                  alt={activePreviewMedia.fileName}
                  style={{
                    maxHeight: '70vh',
                    maxWidth: '85vw',
                    objectFit: 'contain',
                    borderRadius: '4px',
                  }}
                />
              )}
            </div>
          </div>
        </div>
      )}

      {/* DELETE CONFIRMATION MODAL */}
      {deletingMedia && (
        <div
          onClick={() => !isDeleting && setDeletingMedia(null)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(62, 39, 35, 0.48)',
            backdropFilter: 'blur(8px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1rem',
            zIndex: 1100,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              backgroundColor: 'var(--surface)',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--border)',
              maxWidth: '420px',
              width: '100%',
              padding: '1.75rem',
              boxShadow: 'var(--shadow-soft)',
            }}
          >
            <h3 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--text)', margin: '0 0 0.5rem 0' }}>
              {isAudioMedia(deletingMedia) ? 'Delete Voice Recording?' : 'Delete Image Attachment?'}
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', lineHeight: 1.5, margin: '0 0 1.25rem 0' }}>
              Are you sure you want to delete <strong>{deletingMedia.fileName}</strong>? This action cannot
              be undone and will permanently remove the {isAudioMedia(deletingMedia) ? 'voice recording' : 'image'} from storage.
            </p>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                type="button"
                className="secondary-button"
                disabled={isDeleting}
                onClick={() => setDeletingMedia(null)}
                style={{
                  padding: '0.6rem 1.15rem',
                  fontSize: '0.85rem',
                }}
              >
                Cancel
              </button>
              <button
                type="button"
                className="primary-button"
                disabled={isDeleting}
                onClick={handleDeleteConfirm}
                style={{
                  background: 'linear-gradient(135deg, var(--danger), #b85447)',
                  border: 'none',
                  padding: '0.6rem 1.25rem',
                  fontSize: '0.85rem',
                  boxShadow: '0 8px 18px rgba(165, 72, 59, 0.25)',
                }}
              >
                {isDeleting ? 'Deleting...' : 'Confirm Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
